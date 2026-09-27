#ifndef STRESS_ENGINE_H
#define STRESS_ENGINE_H

#include <stdint.h>

#define STRESS_OK           0
#define STRESS_ERR_ARGS    (-1)
#define STRESS_ERR_RUNNING (-2)
#define STRESS_ERR_THREAD  (-3)
#define STRESS_ERR_BUFFER  (-4)

#define STRESS_MAX_THREADS 1024

/* Posicoes necessarias no buffer de snapshot para n threads. */
#define STRESS_SNAPSHOT_SIZE(n) (2 + 2 * (n))

/*
 * CONTRATO DE CONCORRENCIA
 * ------------------------
 * O QUE O CHAMADOR DEVE GARANTIR
 *
 *   1. stress_start() e stress_stop() sao chamadas por uma UNICA thread de
 *      controle, e nunca em paralelo entre si nem consigo mesmas. Dois starts
 *      simultaneos, ou um start concorrente com um stop, sao comportamento
 *      indefinido. Nao ha mutex nesta camada, por decisao de projeto.
 *
 *   2. O chamador trata out[0] == 0 sem dividir por zero (ver stress_snapshot).
 *
 * O QUE O MOTOR GARANTE
 *
 *   stress_snapshot() pode ser chamada por OUTRA thread, a qualquer momento:
 *   durante a execucao em regime, concorrentemente com stress_stop(), e
 *   concorrentemente com stress_start(). E exatamente o uso do lado Java: a
 *   thread principal chama start/stop (inclusive pelo shutdown hook) enquanto a
 *   thread de amostragem chama snapshot a cada 1 s.
 *
 *   a) NUNCA acessa memoria invalida. O array de workers e uma arena unica,
 *      alocada na primeira chamada de stress_start() e nunca liberada nem
 *      realocada — os ciclos start/stop/start a reutilizam. Isso e uma escolha
 *      de projeto, nao um efeito colateral: ordem de publicacao sozinha NAO
 *      resolveria o caso em que um snapshot le "n > 0", e preemptado, e so
 *      desreferencia o array depois de um start concorrente te-lo liberado.
 *      Com a arena permanente, o ponteiro que o snapshot carregou continua
 *      valido ate o fim do processo. A retencao e deliberada e limitada:
 *      STRESS_MAX_THREADS * 64 B = 64 KiB, uma unica vez.
 *
 *   b) NUNCA mistura duas geracoes. Se um stress_start() concorrente trocar a
 *      configuracao no meio da leitura, o snapshot detecta a transicao e
 *      devolve o resultado de "motor inativo" (ver abaixo) em vez de numeros
 *      incoerentes.
 *
 * O QUE O MOTOR NAO GARANTE
 *
 *   - O snapshot NAO e um instante coerente: cada contador e lido com um load
 *     atomico independente, entao as threads podem estar alguns lotes
 *     dessincronizadas entre si. E inerente a amostragem sem lock e nao afeta
 *     as metricas agregadas do projeto.
 *   - Durante um stress_start() concorrente, o snapshot pode devolver "motor
 *     inativo" mesmo havendo um motor prestes a rodar. E transitorio (a janela
 *     e de microssegundos) e a proxima amostragem ja ve a geracao nova.
 *   - Se `cap` for pequeno demais para o n observado, devolve STRESS_ERR_BUFFER
 *     sem escrever nada. Dimensione `out` pelo n que foi pedido ao start.
 */

/* Inicia o teste. n_threads em [1, 1024], load_pct em [1, 100].
   Retorna STRESS_OK ou um dos codigos de erro acima. */
int stress_start(int n_threads, int load_pct);

/* Sinaliza parada e aguarda todas as threads (join). Idempotente. */
void stress_stop(void);

/* Preenche `out` com o snapshot. Layout:
     out[0]           = tempo real decorrido desde stress_start, em ns
     out[1]           = numero de threads
     out[2 + i*2 + 0] = tempo de CPU acumulado da thread i, em ns
     out[2 + i*2 + 1] = iteracoes acumuladas da thread i
   Retorna a quantidade de posicoes preenchidas, ou STRESS_ERR_BUFFER
   se `cap` for insuficiente (nesse caso nada e escrito em `out`).

   MOTOR INATIVO. Quando nao ha motor ativo, escreve out[0] = 0 e out[1] = 0,
   NAO toca nas demais posicoes, e retorna STRESS_SNAPSHOT_SIZE(0) == 2. Isso
   acontece em tres situacoes: (i) stress_start() nunca foi chamada com
   sucesso; (ii) um start anterior falhou; (iii) um stress_start() concorrente
   esta trocando a geracao neste instante.

   RESPONSABILIDADE DO CHAMADOR: out[0] e o divisor natural de qualquer calculo
   de utilizacao (cpu_ns / tempo_real). Com out[0] == 0 o chamador DEVE pular a
   amostra em vez de dividir — em Java isso nao lanca excecao, produz Infinity
   ou NaN e contamina medias e graficos silenciosamente. O tratamento fica na
   camada Java (Task 7). Note que out[1] == 0 acompanha out[0] == 0, entao
   testar out[1] == 0 e igualmente valido. */
int stress_snapshot(uint64_t *out, int cap);

int stress_cpu_count(void);
int stress_is_running(void);

#endif /* STRESS_ENGINE_H */
