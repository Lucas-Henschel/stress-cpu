package br.furb.so.stress.metrics;

/**
 * Metricas de uma janela de amostragem.
 *
 * <p>O motor nativo devolve apenas contadores brutos monotonicos. Todo o
 * calculo de delta e percentual acontece aqui, como funcao pura — o que
 * torna esta logica testavel sem JNI e sem CPU ocupada.
 *
 * @param elapsedMillis    tempo total desde o inicio do teste
 * @param threadCount      threads ativas
 * @param utilization      percentual de CPU por thread, em [0, 100]
 * @param iterationsPerSec vazao por thread na janela
 * @param systemLoad       carga global do sistema em [0, 1], ou NaN se indisponivel
 */
public record StressStats(
    long elapsedMillis,
    int threadCount,
    double[] utilization,
    double[] iterationsPerSec,
    double systemLoad
) {
    private static final int HEADER = 2;

    /**
     * Calcula as metricas entre duas amostras consecutivas do buffer nativo.
     *
     * <p>Layout do buffer (definido em {@code stress_engine.h}):
     * <pre>
     *   [0]           tempo real decorrido (ns)
     *   [1]           numero de threads
     *   [2 + i*2]     tempo de CPU acumulado da thread i (ns)
     *   [3 + i*2]     iteracoes acumuladas da thread i
     * </pre>
     */
    public static StressStats delta(long[] anterior, long[] atual, double systemLoad) {
        int n = (int) atual[1];
        double[] util = new double[n];
        double[] ips = new double[n];

        long deltaReal = atual[0] - anterior[0];
        long elapsedMillis = atual[0] / 1_000_000L;

        if (deltaReal <= 0) {
            return new StressStats(elapsedMillis, n, util, ips, systemLoad);
        }

        double segundos = deltaReal / 1e9;

        for (int i = 0; i < n; i++) {
            int idxCpu = HEADER + i * 2;
            int idxIter = idxCpu + 1;

            long cpuAntes = idxCpu < anterior.length ? anterior[idxCpu] : 0L;
            long iterAntes = idxIter < anterior.length ? anterior[idxIter] : 0L;

            long deltaCpu = atual[idxCpu] - cpuAntes;
            long deltaIter = atual[idxIter] - iterAntes;

            double pct = deltaCpu * 100.0 / deltaReal;
            util[i] = Math.min(100.0, Math.max(0.0, pct));
            ips[i] = Math.max(0.0, deltaIter / segundos);
        }

        return new StressStats(elapsedMillis, n, util, ips, systemLoad);
    }
}
