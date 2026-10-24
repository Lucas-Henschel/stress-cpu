#include <jni.h>
#include <stdio.h>
#include <stdlib.h>

#include "platform.h"
#include "stress_engine.h"

/* Este arquivo contem APENAS traducao de tipos e de erros.
   Nenhuma regra de negocio — ver secao 2.4 da spec. */

static void lancar(JNIEnv *env, const char *classe, const char *msg) {
    jclass c = (*env)->FindClass(env, classe);
    /* FindClass pode falhar (classe ausente) e deixar uma excecao pendente
       (NoClassDefFoundError); nesse caso c == NULL e nao chamamos ThrowNew
       nem nenhuma outra funcao JNI — a excecao ja pendente basta. */
    if (c != NULL) (*env)->ThrowNew(env, c, msg);
}

JNIEXPORT jint JNICALL
Java_br_furb_so_stress_jni_CpuStress_cpuCount(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    return (jint)stress_cpu_count();
}

JNIEXPORT jboolean JNICALL
Java_br_furb_so_stress_jni_CpuStress_start(
    JNIEnv *env, jclass cls, jintArray cores, jintArray loads
) {
    (void)cls;
    if (loads == NULL) {
        lancar(env, "java/lang/IllegalArgumentException", "loads nulo");
        return JNI_FALSE;
    }

    jsize n = (*env)->GetArrayLength(env, loads);
    if (cores != NULL && (*env)->GetArrayLength(env, cores) != n) {
        lancar(env, "java/lang/IllegalArgumentException",
               "cores e loads devem ter o mesmo tamanho");
        return JNI_FALSE;
    }
    if (n < 1 || n > STRESS_MAX_THREADS) {
        lancar(env, "java/lang/IllegalStateException",
               "argumentos invalidos: threads deve estar em [1,1024]");
        return JNI_FALSE;
    }

    /* jint e int32 em todas as plataformas suportadas, mas copiamos para int
       em vez de converter o ponteiro: o C nao promete que sejam o mesmo tipo. */
    int *c_loads = (int *)malloc(sizeof(int) * (size_t)n);
    int *c_cores = (cores != NULL) ? (int *)malloc(sizeof(int) * (size_t)n) : NULL;
    jint *tmp = (jint *)malloc(sizeof(jint) * (size_t)n);
    if (c_loads == NULL || tmp == NULL || (cores != NULL && c_cores == NULL)) {
        free(c_loads); free(c_cores); free(tmp);
        lancar(env, "java/lang/OutOfMemoryError", "sem memoria para iniciar");
        return JNI_FALSE;
    }

    (*env)->GetIntArrayRegion(env, loads, 0, n, tmp);
    for (jsize i = 0; i < n; i++) c_loads[i] = (int)tmp[i];
    if (cores != NULL) {
        (*env)->GetIntArrayRegion(env, cores, 0, n, tmp);
        for (jsize i = 0; i < n; i++) c_cores[i] = (int)tmp[i];
    }
    free(tmp);

    int afinidade = 0;
    int rc = stress_start((int)n, c_cores, c_loads, &afinidade);
    free(c_loads);
    free(c_cores);

    char msg[160];
    switch (rc) {
        case STRESS_OK:
            return afinidade ? JNI_TRUE : JNI_FALSE;
        case STRESS_ERR_ARGS:
            lancar(
                env, "java/lang/IllegalStateException",
                "argumentos invalidos: load deve estar em [1,100] e cada "
                "nucleo deve existir nesta maquina"
            );
            return JNI_FALSE;
        case STRESS_ERR_RUNNING:
            lancar(
                env, "java/lang/IllegalStateException",
                "o motor de stress ja esta em execucao"
            );
            return JNI_FALSE;
        case STRESS_ERR_AFFINITY:
            snprintf(msg, sizeof(msg),
                     "o sistema operacional recusou fixar uma thread no nucleo "
                     "%d (nucleo offline ou nao permitido a este processo)",
                     afinidade);
            lancar(env, "java/lang/IllegalStateException", msg);
            return JNI_FALSE;
        default:
            lancar(
                env, "java/lang/IllegalStateException",
                "falha ao criar as threads nativas"
            );
            return JNI_FALSE;
    }
}

JNIEXPORT void JNICALL
Java_br_furb_so_stress_jni_CpuStress_stop(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    stress_stop();
}

JNIEXPORT jint JNICALL
Java_br_furb_so_stress_jni_CpuStress_snapshot(JNIEnv *env, jclass cls, jlongArray buffer) {
    (void)cls;
    if (buffer == NULL) {
        lancar(env, "java/lang/IllegalArgumentException", "buffer nulo");
        return -1;
    }

    jsize cap = (*env)->GetArrayLength(env, buffer);

    /* Buffer temporario em C: assim usamos SetLongArrayRegion, que copia
       sem fazer pinning do array Java nem criar referencias locais.
       Evitamos malloc(0) quando cap == 0 (comportamento de retorno
       implementation-defined em C11 — poderia devolver NULL sem ser OOM,
       o que seria mal-interpretado como falha de alocacao abaixo). */
    size_t alloc_n = (cap > 0) ? (size_t)cap : (size_t)1;
    uint64_t *tmp = (uint64_t *)malloc(sizeof(uint64_t) * alloc_n);
    if (tmp == NULL) {
        lancar(env, "java/lang/OutOfMemoryError", "sem memoria para o snapshot");
        return -1;
    }

    int usados = stress_snapshot(tmp, (int)cap);
    if (usados < 0) {
        free(tmp);
        lancar(env, "java/lang/IllegalArgumentException",
               "buffer pequeno demais para o numero de threads ativas");
        return -1;
    }

    /* usados <= cap sempre que stress_snapshot nao reporta erro (contrato
       da Task 3), entao SetLongArrayRegion nunca extrapola os limites do
       array Java aqui. Se ainda assim lancasse (ArrayIndexOutOfBounds), a
       excecao pendente se propaga normalmente ao retornarmos para o Java —
       nao fazemos mais nenhuma chamada JNI depois deste ponto. */
    (*env)->SetLongArrayRegion(env, buffer, 0, (jsize)usados, (const jlong *)tmp);
    free(tmp);
    return (jint)usados;
}

JNIEXPORT void JNICALL
Java_br_furb_so_stress_jni_CpuStress_enableVtMode(JNIEnv *env, jclass cls) {
    (void)env; (void)cls;
    platform_enable_vt_mode();
}
