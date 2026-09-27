#ifndef PLATFORM_H
#define PLATFORM_H

#include <stddef.h>
#include <stdint.h>

#ifdef _WIN32
typedef void *platform_thread_t;
#else
#include <pthread.h>
typedef pthread_t platform_thread_t;
#endif

/* Função executada por uma thread nativa. Retorna void para uniformizar
   POSIX (void*) e Win32 (DWORD); cada implementação usa um trampolim. */
typedef void (*platform_thread_fn)(void *arg);

/* Núcleos lógicos disponíveis. Retorna >= 1 sempre. */
int platform_cpu_count(void);

/* Relógio monotônico do sistema, em nanossegundos. Não retrocede. */
uint64_t platform_monotonic_ns(void);

/* Tempo de CPU (usuário + sistema) consumido pela THREAD CORRENTE, em ns.
   DEVE ser chamada pela própria thread cujo tempo se deseja medir. */
uint64_t platform_thread_cpu_ns(void);

/* Suspende a thread corrente por ao menos `ms` milissegundos. */
void platform_sleep_ms(int ms);

/* Cria uma thread. Retorna 0 em sucesso, != 0 em falha. */
int platform_thread_create(platform_thread_t *out, platform_thread_fn fn, void *arg);

/* Aguarda o término da thread. Retorna 0 em sucesso. */
int platform_thread_join(platform_thread_t t);

/* Habilita sequências ANSI no console. No-op fora do Windows. */
void platform_enable_vt_mode(void);

/* Aloca `size` bytes com o alinhamento pedido (potencia de 2). A memoria NAO
   vem zerada. Devolve NULL em falha. Libere SOMENTE com platform_aligned_free. */
void *platform_aligned_alloc(size_t alignment, size_t size);

/* Libera memoria obtida de platform_aligned_alloc. Aceita NULL. */
void platform_aligned_free(void *p);

#endif /* PLATFORM_H */
