package br.furb.so.stress.gui.model;

import br.furb.so.stress.metrics.StressStats;
import java.util.List;
import java.util.Objects;

/** Maps native worker-order metrics to the selected logical processor IDs. */
public final class RealStressMetrics {
    private RealStressMetrics() {
    }

    public static List<CoreMetric> from(StressConfiguration configuration, StressStats stats) {
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(stats, "stats");
        if (stats.threadCount() == 0) {
            return List.of();
        }

        var cores = configuration.selectedCores().stream().sorted().toList();
        if (stats.threadCount() != cores.size()) {
            throw new IllegalArgumentException("Quantidade de workers nao corresponde aos nucleos selecionados");
        }

        return java.util.stream.IntStream.range(0, cores.size())
            .mapToObj(index -> new CoreMetric(cores.get(index), stats.utilization()[index]))
            .toList();
    }
}
