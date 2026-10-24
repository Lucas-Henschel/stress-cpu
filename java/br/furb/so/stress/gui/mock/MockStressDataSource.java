package br.furb.so.stress.gui.mock;

import br.furb.so.stress.gui.model.CoreMetric;
import br.furb.so.stress.gui.model.StressConfiguration;
import java.util.List;

/**
 * Amostras ficticias para a interface, sem JNI e sem medir ou ocupar a CPU.
 * Cada instancia produz a mesma sequencia, com variacao de ate cinco pontos
 * percentuais em torno da carga solicitada.
 */
public final class MockStressDataSource {
    private double phase;

    public List<CoreMetric> next(StressConfiguration configuration) {
        double samplePhase = phase;
        phase = (phase + 0.25) % (2 * Math.PI);
        return configuration.selectedCores().stream()
            .sorted()
            .map(core -> {
                double variation = 5 * Math.sin(samplePhase + core);
                double utilization = Math.max(0, Math.min(100, configuration.loadPercent() + variation));
                return new CoreMetric(core, utilization);
            })
            .toList();
    }
}
