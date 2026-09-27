#include "stress_engine.h"
#include "platform.h"

#include <math.h>
#include <stdatomic.h>
#include <stdbool.h>
#include <stdlib.h>

/* Iteracoes por lote. A flag de parada e lida uma vez por lote, nao por
   iteracao: ler um atomico a cada iteracao destruiria a vazao. A latencia
   de parada resultante e de um lote (< 1ms). */
#define BATCH 100000

/* _Alignas(64) evita false sharing: sem ele, contadores de threads distintas
   compartilhariam a mesma linha de cache, e cada fetch_add invalidaria a
   linha das demais. Ver secao 4.6 da spec.

   O _Alignas no primeiro membro ja arredonda sizeof(worker_t) para o proximo
   multiplo de 64 (C11 6.7.5/6.2.8): nao e preciso nenhum campo de padding
   explicito. Com os quatro campos abaixo, sizeof == 64 == uma linha de cache,
   que e exatamente a intencao. */
typedef struct {
    _Alignas(64) atomic_uint_fast64_t iterations;
    atomic_uint_fast64_t cpu_ns;
    int load_pct;
} worker_t;

#define WORKER_ALIGN 64

/* Garantias verificadas em tempo de compilacao, nao por inspecao visual:
   - alinhamento 64 e o que o _Alignas pediu (senao o false sharing volta);
   - sizeof == 64 mantem um worker por linha de cache E satisfaz a exigencia
     do aligned_alloc de que o tamanho pedido seja multiplo do alinhamento. */
_Static_assert(_Alignof(worker_t) == WORKER_ALIGN,
               "worker_t deve estar alinhado em 64 bytes");
_Static_assert(sizeof(worker_t) == WORKER_ALIGN,
               "worker_t deve ocupar exatamente uma linha de cache");

/* ATOMIC_VAR_INIT foi depreciado no C17 e removido no C23.
   Inicializacao direta de variavel estatica e valida e portavel. */
static atomic_bool g_running = false;

/* ARENA DE WORKERS — ponto central do contrato de concorrencia.

   g_workers aponta para UMA arena de STRESS_MAX_THREADS workers, alocada na
   primeira chamada bem-sucedida de stress_start() e NUNCA liberada nem
   realocada. O motivo e uma corrida TOCTOU que nenhuma ordem de publicacao
   resolve: uma thread que ja leu "n > 0" dentro de stress_snapshot() pode ser
   preemptada ali e so desreferenciar o array depois; se stress_start()
   liberasse o array nesse meio-tempo, a leitura seria em memoria liberada.
   Zerar g_n_threads antes do free protege apenas quem ainda NAO leu n.

   Como a arena vive enquanto o processo vive, o ponteiro que o snapshot
   carregou e valido para sempre: o acesso invalido deixa de ser possivel por
   construcao, sem mutex. O custo e fixo e previsivel: 1024 * 64 B = 64 KiB,
   uma unica alocacao para todo o processo.

   Ela e _Atomic porque stress_snapshot() a le enquanto stress_start() pode
   escreve-la (NULL -> arena, uma unica vez na vida do processo). */
static _Atomic(worker_t *) g_workers = NULL;

/* Tocado somente pela thread de controle (start/stop); snapshot nao o le. */
static platform_thread_t *g_threads = NULL;

/* Atomicas porque stress_snapshot() as le de outra thread enquanto
   stress_start()/stress_stop() podem escreve-las (ver o contrato de
   concorrencia em stress_engine.h). */
static _Atomic int g_n_threads = 0;
static _Atomic uint64_t g_start_ns = 0;
static _Atomic uint64_t g_stop_ns = 0;

/* CONTADOR DE GERACAO (seqlock de leitor unico, sem retry).
   Impar = stress_start() esta trocando a geracao; par = estado publicado.
   A arena garante que o snapshot nunca toca memoria invalida; este contador
   garante que ele nunca devolve uma MISTURA de duas geracoes — ao detectar
   uma transicao concorrente, devolve o mesmo resultado de "motor inativo"
   (out[0] = out[1] = 0) em vez de numeros incoerentes. */
static _Atomic unsigned g_generation = 0;

static void marcar_transicao(void) {
    atomic_fetch_add_explicit(&g_generation, 1u, memory_order_acq_rel);
}

#define DUTY_WINDOW_MS 100

static void duty_cycle_pause(worker_t *w, uint64_t *inicio_janela) {
    if (w->load_pct >= 100) return;

    uint64_t agora = platform_monotonic_ns();
    uint64_t trabalho_ns = agora - *inicio_janela;
    uint64_t alvo_ns = (uint64_t)DUTY_WINDOW_MS * 1000000ull
                     * (uint64_t)w->load_pct / 100ull;

    if (trabalho_ns < alvo_ns) return;

    uint64_t janela_ns = (uint64_t)DUTY_WINDOW_MS * 1000000ull;
    if (trabalho_ns < janela_ns) {
        platform_sleep_ms((int)((janela_ns - trabalho_ns) / 1000000ull));
    }
    *inicio_janela = platform_monotonic_ns();
}

static void worker_main(void *arg) {
    worker_t *w = (worker_t *)arg;
    volatile double sink = 0.0;      /* impede eliminacao do loop com -O2 */

    /* SEMENTE UNIFORME ENTRE THREADS — de proposito, e contraintuitivo.
       Variar a semente por thread (por exemplo, 1.0 + id) parece "mais
       realista", mas quebra a comparabilidade da metrica: para id <= 2 a
       trajetoria de x convergia a um ponto fixo (a guarda de reenquadramento
       abaixo nunca disparava) e cada iteracao dependia do resultado da
       anterior — o laco ficava limitado por LATENCIA. Para id >= 3 a
       primeira conta levava x a um valor negativo, a guarda disparava em
       TODA iteracao, e x era resetado a uma constante fixa antes de sqrt/sin/
       cos rodarem de novo — isso quebra a cadeia de dependencia entre
       iteracoes, permitindo que o processador sobreponha iteracoes em
       pipeline (limitado por VAZAO, nao por latencia). O resultado medido
       era um salto deterministico de ~4,7x em iteracoes/s entre threads de
       id baixo e id alto, com variancia zero entre quadros — um artefato da
       trajetoria numerica escolhida por thread, nao do hardware. Isso e
       exatamente o que este projeto NAO quer medir: a spec usa iteracoes/s
       para expor heterogeneidade de nucleos (P-core vs. E-core), e o defeito
       fazia a metrica expor o indice da thread em vez do nucleo em que ela
       rodava. Com semente identica (1.0) para todas as threads, todas
       percorrem a MESMA trajetoria e pagam o MESMO custo por iteracao — a
       partir daqui, diferencas em iteracoes/s so podem vir do hardware
       (P-core vs. E-core, contencao, etc.), que e o que a spec pede.
       NAO "melhore" isto voltando a variar por thread. */
    double x = 1.0;
    uint64_t inicio_janela = platform_monotonic_ns();

    while (atomic_load_explicit(&g_running, memory_order_relaxed)) {
        for (int i = 0; i < BATCH; i++) {
            x = sqrt(x * 1.0000001 + 1.0);
            x = sin(x) + cos(x) * 1.5;
            /* Reenquadra x: sem isso ele escorrega para subnormais ou NaN,
               e a FPU passa a executar um caminho lento e irregular. Reset
               para a MESMA constante (1.0) em todas as threads — ver o
               comentario acima sobre semente uniforme; um reset dependente
               de w->id aqui reintroduziria o defeito mesmo com a semente
               inicial corrigida. */
            if (x > 1e6 || x < 1e-6 || x != x) x = 1.0;
        }
        sink += x;

        atomic_store_explicit(&w->cpu_ns, platform_thread_cpu_ns(),
                              memory_order_relaxed);
        atomic_fetch_add_explicit(&w->iterations, (uint_fast64_t)BATCH,
                                  memory_order_relaxed);

        duty_cycle_pause(w, &inicio_janela);
    }
    (void)sink;
}

int stress_cpu_count(void) {
    return platform_cpu_count();
}

int stress_is_running(void) {
    return atomic_load(&g_running) ? 1 : 0;
}

/* Devolve a arena, alocando-a na primeira vez. Chamada SOMENTE pela thread de
   controle (stress_start), que e a unica escritora de g_workers — por isso o
   load pode ser relaxed; o store e release para que qualquer leitor que enxergue
   o ponteiro ja enxergue a arena inteiramente inicializada. */
static worker_t *arena_obter(void) {
    worker_t *arena = atomic_load_explicit(&g_workers, memory_order_relaxed);
    if (arena != NULL) return arena;

    /* worker_t e sobrealinhado (64 B): calloc garante apenas o alinhamento
       fundamental (C11 7.22.3), e guardar um tipo sobrealinhado em memoria
       menos alinhada e comportamento indefinido (C11 6.3.2.3p7).
       sizeof(worker_t) == 64 == WORKER_ALIGN, entao o tamanho pedido e
       multiplo do alinhamento, como aligned_alloc exige. */
    arena = (worker_t *)platform_aligned_alloc(
        WORKER_ALIGN, (size_t)STRESS_MAX_THREADS * sizeof(worker_t));
    if (arena == NULL) return NULL;

    /* platform_aligned_alloc NAO zera a memoria. Inicializamos TODAS as
       posicoes, nao apenas as n em uso: um leitor com um n antigo (maior que o
       atual) pode indexar posicoes que a geracao corrente nao usa, e elas
       precisam conter atomicos validos, nao bytes indeterminados. */
    for (int i = 0; i < STRESS_MAX_THREADS; i++) {
        arena[i].load_pct = 100;
        atomic_init(&arena[i].iterations, 0);
        atomic_init(&arena[i].cpu_ns, 0);
    }
    atomic_store_explicit(&g_workers, arena, memory_order_release);
    return arena;
}

int stress_start(int n_threads, int load_pct) {
    if (n_threads < 1 || n_threads > STRESS_MAX_THREADS) return STRESS_ERR_ARGS;
    if (load_pct < 1 || load_pct > 100) return STRESS_ERR_ARGS;
    if (atomic_load(&g_running)) return STRESS_ERR_RUNNING;

    /* A arena e reaproveitada entre ciclos e nunca liberada: ver o comentario
       em g_workers. E isso — e nao a ordem de publicacao — que impede um
       snapshot concorrente de desreferenciar memoria liberada. */
    worker_t *arena = arena_obter();
    if (arena == NULL) return STRESS_ERR_THREAD;

    platform_thread_t *threads = (platform_thread_t *)calloc(
        (size_t)n_threads, sizeof(platform_thread_t));
    if (threads == NULL) return STRESS_ERR_THREAD;

    marcar_transicao();                /* geracao impar: troca em curso */

    /* Fecha a janela para quem AINDA NAO leu n: enquanto a geracao nova nao
       estiver pronta, um snapshot concorrente ve "motor inativo". */
    atomic_store_explicit(&g_n_threads, 0, memory_order_relaxed);

    /* Reinicializacao da geracao: stores atomicos, nao atomic_init — os
       objetos ja existem e podem estar sendo lidos por um snapshot concorrente,
       e atomic_init nao e uma operacao atomica. Nenhuma worker thread da
       geracao anterior existe aqui (stress_stop ja fez join de todas), entao
       id/load_pct podem ser escritos normalmente. */
    for (int i = 0; i < n_threads; i++) {
        arena[i].load_pct = load_pct;
        atomic_store_explicit(&arena[i].iterations, 0, memory_order_relaxed);
        atomic_store_explicit(&arena[i].cpu_ns, 0, memory_order_relaxed);
    }

    atomic_store_explicit(&g_start_ns, platform_monotonic_ns(),
                          memory_order_relaxed);
    atomic_store_explicit(&g_stop_ns, 0, memory_order_relaxed);
    g_threads = threads;
    atomic_store(&g_running, true);
    /* g_n_threads POR ULTIMO e com release: e ele que "abre" o array para o
       snapshot, e o release garante que os contadores zerados acima ja estejam
       visiveis para quem o ler com acquire. */
    atomic_store_explicit(&g_n_threads, n_threads, memory_order_release);

    marcar_transicao();                /* geracao par: estado publicado */

    for (int i = 0; i < n_threads; i++) {
        if (platform_thread_create(&g_threads[i], worker_main, &arena[i]) != 0) {
            /* Falha parcial: encerra as threads ja criadas antes de desistir. */
            atomic_store(&g_running, false);
            for (int j = 0; j < i; j++) platform_thread_join(g_threads[j]);
            marcar_transicao();
            atomic_store_explicit(&g_n_threads, 0, memory_order_release);
            marcar_transicao();
            free(g_threads); g_threads = NULL;
            /* A arena NAO e liberada: ela sobrevive ao processo inteiro. */
            return STRESS_ERR_THREAD;
        }
    }
    return STRESS_OK;
}

void stress_stop(void) {
    if (!atomic_load(&g_running)) return;

    /* g_stop_ns ANTES de g_running: um snapshot concorrente que visse
       running == false com g_stop_ns ainda 0 calcularia fim == 0 e devolveria
       tempo real decorrido == 0 — o que faria o lado Java dividir por zero.
       Essa janela e real: na Task 9 o shutdown hook chama stop() enquanto o
       laco de amostragem de 1 s roda em outra thread. */
    atomic_store_explicit(&g_stop_ns, platform_monotonic_ns(),
                          memory_order_relaxed);
    atomic_store(&g_running, false);

    int n = atomic_load_explicit(&g_n_threads, memory_order_relaxed);
    for (int i = 0; i < n; i++) {
        platform_thread_join(g_threads[i]);
    }
    free(g_threads);
    g_threads = NULL;
    /* g_n_threads e a arena sao mantidos: snapshot() apos stop deve devolver os
       valores finais, nao lixo. A arena nunca e liberada (ver g_workers). */
}

int stress_snapshot(uint64_t *out, int cap) {
    if (out == NULL) return STRESS_ERR_BUFFER;

    /* Uma unica leitura de cada global, para variaveis locais. Reler qualquer
       uma delas no meio da funcao reintroduziria as corridas que a ordem de
       publicacao existe para fechar. */
    unsigned g0 = atomic_load_explicit(&g_generation, memory_order_acquire);
    int n = atomic_load_explicit(&g_n_threads, memory_order_acquire);
    worker_t *w = atomic_load_explicit(&g_workers, memory_order_acquire);

    int necessario = STRESS_SNAPSHOT_SIZE(n);
    if (cap < necessario) return STRESS_ERR_BUFFER;

    /* Motor nunca iniciado, parado-e-rearmado, ou start em curso (g0 impar). */
    if (n == 0 || w == NULL || (g0 & 1u) != 0u) {
        out[0] = 0;
        out[1] = 0;
        return STRESS_SNAPSHOT_SIZE(0);
    }

    uint64_t inicio = atomic_load_explicit(&g_start_ns, memory_order_relaxed);
    uint64_t fim = atomic_load(&g_running)
                 ? platform_monotonic_ns()
                 : atomic_load_explicit(&g_stop_ns, memory_order_relaxed);
    out[0] = (fim > inicio) ? (fim - inicio) : 0;
    out[1] = (uint64_t)n;

    for (int i = 0; i < n; i++) {
        out[2 + i * 2 + 0] =
            (uint64_t)atomic_load_explicit(&w[i].cpu_ns, memory_order_relaxed);
        out[2 + i * 2 + 1] =
            (uint64_t)atomic_load_explicit(&w[i].iterations, memory_order_relaxed);
    }

    /* Validacao de geracao. A barreira e necessaria: um load acquire impede que
       operacoes POSTERIORES subam, nao que as ANTERIORES desçam — sem ela, as
       leituras dos contadores poderiam ser reordenadas para depois do segundo
       load de g_generation, e a validacao nao validaria nada. */
    atomic_thread_fence(memory_order_acquire);
    if (atomic_load_explicit(&g_generation, memory_order_relaxed) != g0) {
        out[0] = 0;
        out[1] = 0;
        return STRESS_SNAPSHOT_SIZE(0);
    }
    return necessario;
}
