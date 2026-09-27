#include "platform.h"

#include <windows.h>

#include <malloc.h>
#include <stdatomic.h>
#include <stdlib.h>

int platform_cpu_count(void) {
    SYSTEM_INFO si;
    GetSystemInfo(&si);
    return (si.dwNumberOfProcessors < 1) ? 1 : (int)si.dwNumberOfProcessors;
}

/* A frequencia do contador e fixa desde o boot, entao vale cachear. O cache e
   _Atomic de proposito: varias threads do motor chamam esta funcao e a
   primeira delas escreve aqui. Com um `static LARGE_INTEGER` comum isso seria
   data race — todas escreveriam o mesmo valor, mas o modelo de memoria de C
   nao promete leitura rasgada nenhuma, e o comportamento seria indefinido em
   teoria. Com atomic relaxed o custo e o mesmo (load de 64 bits) e o programa
   passa a ser bem definido. Zero e o sentinela de "ainda nao consultada";
   QueryPerformanceFrequency nunca devolve zero em sistemas suportados. */
static _Atomic uint64_t g_qpc_freq = 0;

uint64_t platform_monotonic_ns(void) {
    uint64_t freq = atomic_load_explicit(&g_qpc_freq, memory_order_relaxed);
    if (freq == 0) {
        LARGE_INTEGER f;
        if (!QueryPerformanceFrequency(&f) || f.QuadPart <= 0) return 0;
        freq = (uint64_t)f.QuadPart;
        atomic_store_explicit(&g_qpc_freq, freq, memory_order_relaxed);
    }

    LARGE_INTEGER agora;
    if (!QueryPerformanceCounter(&agora)) return 0;

    /* Separa segundos do resto antes de multiplicar. O caminho direto
       (ticks * 1e9 / freq) estoura uint64 depois de poucos segundos de
       uptime: com freq = 10 MHz, ticks * 1e9 passa de 2^64 em ~55 s. */
    uint64_t ticks = (uint64_t)agora.QuadPart;
    uint64_t seg = ticks / freq;
    uint64_t resto = ticks % freq;
    return seg * 1000000000ull + (resto * 1000000000ull) / freq;
}

uint64_t platform_thread_cpu_ns(void) {
    FILETIME criacao, saida, kernel, usuario;
    if (!GetThreadTimes(GetCurrentThread(), &criacao, &saida, &kernel,
                        &usuario)) {
        return 0;
    }

    /* FILETIME nao e alinhado para acesso de 64 bits; copiar pelos campos via
       ULARGE_INTEGER e a forma documentada de le-lo. */
    ULARGE_INTEGER k, u;
    k.LowPart = kernel.dwLowDateTime;
    k.HighPart = kernel.dwHighDateTime;
    u.LowPart = usuario.dwLowDateTime;
    u.HighPart = usuario.dwHighDateTime;

    /* Unidades de 100 ns. A resolucao REAL e o tick do escalonador (~15,6 ms):
       o valor so muda quando o tick encontra a thread rodando, entao amostras
       curtas saem em degraus, nao suaves. Ver "Limitacoes conhecidas" no
       README — e por isso que StressStats limita a utilizacao a [0, 100]. */
    return (uint64_t)(k.QuadPart + u.QuadPart) * 100ull;
}

void platform_sleep_ms(int ms) {
    if (ms <= 0) return;
    Sleep((DWORD)ms);
}

typedef struct {
    platform_thread_fn fn;
    void *arg;
} trampolim_t;

static DWORD WINAPI win_trampolim(LPVOID p) {
    trampolim_t *t = (trampolim_t *)p;
    platform_thread_fn fn = t->fn;
    void *arg = t->arg;
    free(t);
    fn(arg);
    return 0;
}

int platform_thread_create(platform_thread_t *out, platform_thread_fn fn,
                           void *arg) {
    trampolim_t *t = (trampolim_t *)malloc(sizeof(trampolim_t));
    if (t == NULL) return -1;
    t->fn = fn;
    t->arg = arg;

    HANDLE h = CreateThread(NULL, 0, win_trampolim, t, 0, NULL);
    if (h == NULL) {
        free(t);
        return -1;
    }
    *out = (platform_thread_t)h;
    return 0;
}

int platform_thread_join(platform_thread_t t) {
    HANDLE h = (HANDLE)t;
    if (h == NULL) return -1;

    DWORD r = WaitForSingleObject(h, INFINITE);
    /* Diferente do pthread_join, esperar nao destroi a thread: o handle
       sobrevive ao termino e precisa ser fechado explicitamente. Sem este
       CloseHandle o processo vaza um handle de kernel por thread criada, e o
       vazamento se acumula a cada start/stop dentro da mesma execucao. */
    CloseHandle(h);
    return (r == WAIT_OBJECT_0) ? 0 : -1;
}

void platform_enable_vt_mode(void) {
    HANDLE h = GetStdHandle(STD_OUTPUT_HANDLE);
    if (h == INVALID_HANDLE_VALUE || h == NULL) return;

    DWORD modo = 0;
    /* Falha quando a saida esta redirecionada para arquivo ou pipe; nesse caso
       nao ha console para configurar e nao ha o que fazer. */
    if (!GetConsoleMode(h, &modo)) return;
    SetConsoleMode(h, modo | ENABLE_VIRTUAL_TERMINAL_PROCESSING);
}

/* O MSVC nao implementa aligned_alloc (C11 7.22.3.1): a alternativa e
   _aligned_malloc, que inverte a ordem dos argumentos e — o ponto critico —
   devolve memoria que NAO pode ser liberada com free(). Misturar os dois
   alocadores corrompe o heap no Windows. E por isso que platform.h expoe o
   par alloc/free junto em vez de deixar o chamador usar free() direto.

   A validacao de `size % alignment` nao e exigida pelo _aligned_malloc, mas e
   mantida para a funcao aceitar e rejeitar exatamente as mesmas entradas que
   a versao POSIX: um argumento invalido tem que falhar nas tres plataformas,
   nao so naquela em que o padrao obriga. */
void *platform_aligned_alloc(size_t alignment, size_t size) {
    if (alignment == 0 || (alignment & (alignment - 1)) != 0) return NULL;
    if (size == 0 || (size % alignment) != 0) return NULL;
    return _aligned_malloc(size, alignment);
}

void platform_aligned_free(void *p) {
    _aligned_free(p);
}
