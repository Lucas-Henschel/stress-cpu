#include "platform.h"

#include <errno.h>
#include <stdlib.h>
#include <time.h>
#include <unistd.h>

#ifdef __APPLE__
#include <mach/mach.h>
#endif

int platform_cpu_count(void) {
    long n = sysconf(_SC_NPROCESSORS_ONLN);
    return (n < 1) ? 1 : (int)n;
}

uint64_t platform_monotonic_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
}

uint64_t platform_thread_cpu_ns(void) {
#ifdef __APPLE__
    /* macOS nao possui CLOCK_THREAD_CPUTIME_ID; e preciso usar Mach. */
    mach_port_t port = mach_thread_self();
    thread_basic_info_data_t info;
    mach_msg_type_number_t count = THREAD_BASIC_INFO_COUNT;
    kern_return_t kr = thread_info(port, THREAD_BASIC_INFO,
                                   (thread_info_t)&info, &count);
    /* mach_thread_self() incrementa a contagem de referencias da porta.
       Sem este deallocate o processo vaza uma porta Mach por chamada. */
    mach_port_deallocate(mach_task_self(), port);

    if (kr != KERN_SUCCESS) return 0;

    uint64_t segundos = (uint64_t)info.user_time.seconds
                      + (uint64_t)info.system_time.seconds;
    uint64_t micros = (uint64_t)info.user_time.microseconds
                    + (uint64_t)info.system_time.microseconds;
    return segundos * 1000000000ull + micros * 1000ull;
#else
    struct timespec ts;
    if (clock_gettime(CLOCK_THREAD_CPUTIME_ID, &ts) != 0) return 0;
    return (uint64_t)ts.tv_sec * 1000000000ull + (uint64_t)ts.tv_nsec;
#endif
}

void platform_sleep_ms(int ms) {
    if (ms <= 0) return;
    struct timespec req;
    req.tv_sec = ms / 1000;
    req.tv_nsec = (long)(ms % 1000) * 1000000L;
    /* nanosleep pode ser interrompido por sinal; retomamos o restante. */
    struct timespec rem;
    while (nanosleep(&req, &rem) == -1 && errno == EINTR) {
        req = rem;
    }
}

typedef struct {
    platform_thread_fn fn;
    void *arg;
} trampolim_t;

static void *posix_trampolim(void *p) {
    trampolim_t *t = (trampolim_t *)p;
    platform_thread_fn fn = t->fn;
    void *arg = t->arg;
    free(t);
    fn(arg);
    return NULL;
}

int platform_thread_create(platform_thread_t *out, platform_thread_fn fn, void *arg) {
    trampolim_t *t = (trampolim_t *)malloc(sizeof(trampolim_t));
    if (t == NULL) return -1;
    t->fn = fn;
    t->arg = arg;
    int rc = pthread_create(out, NULL, posix_trampolim, t);
    if (rc != 0) free(t);
    return rc;
}

int platform_thread_join(platform_thread_t t) {
    return pthread_join(t, NULL);
}

void platform_enable_vt_mode(void) {
    /* POSIX: terminais ja interpretam ANSI. */
}

/* C11 7.22.3.1p2 exige que `size` seja multiplo de `alignment` (e que
   `alignment` seja suportado pela implementacao); chamar aligned_alloc fora
   dessa regra e comportamento indefinido. O chamador precisa garantir isso —
   no motor de stress isso vale porque sizeof(worker_t) == 64 == alinhamento.

   No Windows esta funcao sera _aligned_malloc/_aligned_free: o MSVC nao
   implementa aligned_alloc, e memoria de _aligned_malloc NAO pode ser
   liberada com free() (nem vice-versa). Por isso o par alloc/free e exposto
   junto na interface: nunca misture os dois alocadores. */
void *platform_aligned_alloc(size_t alignment, size_t size) {
    if (alignment == 0 || (alignment & (alignment - 1)) != 0) return NULL;
    if (size == 0 || (size % alignment) != 0) return NULL;
    return aligned_alloc(alignment, size);
}

void platform_aligned_free(void *p) {
    free(p);
}
