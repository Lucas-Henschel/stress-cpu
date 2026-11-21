package br.furb.so.stress.gui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Sequencia de passos (nucleos + carga) que um modo de estresse executa ao longo do tempo.
 * Os modos estaticos tem um unico passo; os dinamicos trocam de passo a cada intervalo.
 * Nao e thread-safe: o controlador usa a instancia apenas na sua thread de controle.
 */
public final class StressSchedule {
    /** Degraus da rampa do modo Oscilante, do piso (1/4 da carga) ate a carga configurada. */
    static final int OSCILLATION_LEVELS = 5;

    /** Um estado do motor nativo: quais nucleos rodam e com qual carga. */
    public record Step(List<Integer> cores, int loadPercent) {
        public Step {
            cores = List.copyOf(cores);
        }

        public int[] coreArray() {
            return cores.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private final StressMode mode;
    private final List<Integer> cores;
    private final int highLoad;
    private final List<Integer> oscillationLoads;
    private final RandomGenerator random;
    private int index;
    private int oscillationPhase;
    private Step current;

    public StressSchedule(StressConfiguration configuration, RandomGenerator random) {
        Objects.requireNonNull(configuration, "configuration");
        this.random = Objects.requireNonNull(random, "random");
        this.mode = configuration.mode();
        this.cores = CoreSelection.resolve(configuration.selectedCores(), mode);
        this.highLoad = configuration.loadPercent();
        this.oscillationLoads = oscillationLoads(highLoad);
        this.index = mode == StressMode.ALEATORIO ? random.nextInt(cores.size()) : 0;
        this.current = build();
    }

    public Step current() {
        return current;
    }

    /** Indica se ainda havera troca de passo; falso para modos estaticos ou rampa ja completa. */
    public boolean isDynamic() {
        return switch (mode) {
            case TODOS, PARES, IMPARES -> false;
            case CIRCULAR, ALEATORIO -> cores.size() > 1;
            case ESCALONADO -> index < cores.size() - 1;
            case OSCILANTE -> oscillationLoads.size() > 1;
        };
    }

    /** Avanca para o proximo passo e o retorna; sem efeito quando o modo nao e dinamico. */
    public Step advance() {
        if (!isDynamic()) {
            return current;
        }
        switch (mode) {
            case CIRCULAR -> index = (index + 1) % cores.size();
            case ESCALONADO -> index++;
            case OSCILANTE -> oscillationPhase = (oscillationPhase + 1) % (2 * (oscillationLoads.size() - 1));
            case ALEATORIO -> {
                // Sorteia entre os demais nucleos para garantir troca a cada passo.
                int next = random.nextInt(cores.size() - 1);
                index = next >= index ? next + 1 : next;
            }
            default -> throw new IllegalStateException("Modo estatico nao avanca: " + mode);
        }
        current = build();
        return current;
    }

    private Step build() {
        return switch (mode) {
            case TODOS, PARES, IMPARES -> new Step(cores, highLoad);
            case CIRCULAR, ALEATORIO -> new Step(List.of(cores.get(index)), highLoad);
            case ESCALONADO -> new Step(cores.subList(0, index + 1), highLoad);
            case OSCILANTE -> new Step(cores, oscillationLoad());
        };
    }

    /**
     * Onda triangular: comeca na carga configurada, desce um degrau por passo ate o piso e
     * sobe de volta, sem saltar direto de um extremo ao outro.
     */
    private int oscillationLoad() {
        int top = oscillationLoads.size() - 1;
        int distanceFromTop = oscillationPhase <= top ? oscillationPhase : 2 * top - oscillationPhase;
        return oscillationLoads.get(top - distanceFromTop);
    }

    /** Degraus crescentes e distintos entre 1/4 da carga (minimo 1%) e a carga configurada. */
    static List<Integer> oscillationLoads(int highLoad) {
        int lowLoad = Math.max(1, highLoad / 4);
        var loads = new ArrayList<Integer>();
        for (int level = 0; level < OSCILLATION_LEVELS; level++) {
            int load = lowLoad + Math.round((highLoad - lowLoad) * level / (float) (OSCILLATION_LEVELS - 1));
            if (loads.isEmpty() || loads.get(loads.size() - 1) != load) {
                loads.add(load);
            }
        }
        return List.copyOf(loads);
    }
}