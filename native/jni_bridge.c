#include <jni.h>
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

JNIEXPORT void JNICALL
Java_br_furb_so_stress_jni_CpuStress_start(
    JNIEnv *env, jclass cls, jint threads, jint loadPct
) {
    (void)cls;
    int rc = stress_start((int)threads, (int)loadPct);
    switch (rc) {
        case STRESS_OK:
            return;
        case STRESS_ERR_ARGS:
            lancar(
                env, "java/lang/IllegalStateException",
                "argumentos invalidos: threads deve estar em [1,1024] e load em [1,100]"
            );
            return;
        case STRESS_ERR_RUNNING:
            lancar(
                env, "java/lang/IllegalStateException",
                "o motor de stress ja esta em execucao"
            );
            return;
        default:
            lancar(
                env, "java/lang/IllegalStateException",
                "falha ao criar as threads nativas"
            );
            return;
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
