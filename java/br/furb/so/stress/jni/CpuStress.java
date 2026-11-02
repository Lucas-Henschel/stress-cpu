package br.furb.so.stress.jni;

/**
 * Ponte para o motor de stress nativo. Singleton: o motor e global em C.
 *
 * <p>ATENCAO: os nomes das funcoes em native/jni_bridge.c sao derivados
 * MECANICAMENTE do pacote e do nome desta classe
 * (Java_br_furb_so_stress_jni_CpuStress_&lt;metodo&gt;). Renomear a classe ou
 * move-la de pacote quebra a ligacao em tempo de execucao com
 * UnsatisfiedLinkError — o javac nao acusa nada. Ao mexer aqui, ajuste
 * jni_bridge.c e o caminho de CpuStress.java no CMakeLists.txt.
 */
public final class CpuStress {
    private CpuStress() {}

    static {
        try {
            System.loadLibrary("cpustress");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Nao foi possivel carregar a biblioteca nativa 'cpustress'.");
            System.err.println("Compile com:  cmake -B build && cmake --build build");
            System.err.println("E execute via scripts/run.sh (ou run.bat no Windows).");
            System.err.println("Detalhe: " + e.getMessage());
            System.exit(1);
        }
    }

    public static int snapshotSize(int nThreads) {
        return 2 + 2 * nThreads;
    }

    public static native int cpuCount();

    /**
     * Inicia uma thread por posicao de {@code loads}.
     *
     * @param cores nucleo logico em que cada thread deve ser fixada, ou
     *              {@code null} para deixar o escalonador decidir
     * @param loads carga alvo de cada thread, em [1, 100]
     * @return {@code true} se cada thread foi fixada no nucleo pedido;
     *         {@code false} se {@code cores} for null ou se o SO nao oferece
     *         afinidade rigida (macOS) -- o teste roda mesmo assim, sem fixar
     * @throws IllegalStateException se os argumentos forem invalidos, o motor
     *         ja estiver em execucao ou o SO recusar fixar algum nucleo.
     */
    public static native boolean start(int[] cores, int[] loads);

    public static native void stop();

    /**
     * Preenche o buffer. Retorna a quantidade de posicoes preenchidas.
     * @throws IllegalArgumentException se o buffer for pequeno demais.
     */
    public static native int snapshot(long[] buffer);

    /**
     * Habilita sequencias ANSI no console (SetConsoleMode no Windows;
     * no-op no Linux/macOS, que ja interpretam ANSI nativamente). Chame
     * antes do primeiro redesenho do renderer.
     */
    public static native void enableVtMode();
}
