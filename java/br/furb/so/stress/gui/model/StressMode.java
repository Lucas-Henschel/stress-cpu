package br.furb.so.stress.gui.model;

/** Formas de escolher quais nucleos serao estressados. */
public enum StressMode {
    TODOS("Todos"),
    PARES("Pares"),
    IMPARES("Ímpares"),
    CIRCULAR("Circular");

    private final String label;

    StressMode(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
