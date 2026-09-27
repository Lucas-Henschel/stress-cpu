package br.furb.so.stress.cli;

/**
 * Argumentos de linha de comando.
 *
 * @param threads          threads a criar
 * @param durationSeconds  parada automatica; 0 significa executar ate Ctrl+C
 * @param loadPct          carga alvo por thread, em [1, 100]
 */
public record Args(int threads, int durationSeconds, int loadPct) {
    public static final String USO = """
        Uso: run.sh [opcoes]

          --threads=N     threads a criar (padrao: numero de nucleos, max 1024)
          --duration=Ns   parada automatica apos N segundos (padrao: ate Ctrl+C)
          --load=P        carga alvo por thread, 1 a 100 (padrao: 100)

        Exemplos:
          run.sh                          satura todos os nucleos
          run.sh --load=50                mantem a CPU em ~50%
          run.sh --threads=32 --duration=20s   oversubscription por 20s
        """;

    public static Args parse(String[] argv, int defaultThreads) {
        int threads = defaultThreads;
        int duration = 0;
        int load = 100;

        boolean durationInformada = false;

        for (String arg : argv) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("argumento invalido: " + arg);
            }

            int eq = arg.indexOf('=');
            String chave = arg.substring(2, eq);
            String valor = arg.substring(eq + 1);

            switch (chave) {
                case "threads" -> threads = exigirInteiro(valor, "threads");
                case "load" -> load = exigirInteiro(valor, "load");
                case "duration" -> {
                    String v = valor.endsWith("s")
                        ? valor.substring(0, valor.length() - 1)
                        : valor;

                    duration = exigirInteiro(v, "duration");
                    durationInformada = true;
                }
                default -> throw new IllegalArgumentException("argumento desconhecido: --" + chave);
            }
        }

        if (threads < 1 || threads > 1024) {
            throw new IllegalArgumentException("--threads deve estar entre 1 e 1024 (recebido: " + threads + ")");
        }

        if (load < 1 || load > 100) {
            throw new IllegalArgumentException("--load deve estar entre 1 e 100 (recebido: " + load + ")");
        }

        if (durationInformada && duration < 1) {
            throw new IllegalArgumentException("--duration deve ser de ao menos 1 segundo (recebido: " + duration + ")");
        }

        return new Args(threads, duration, load);
    }

    private static int exigirInteiro(String valor, String campo) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + campo + " exige um numero inteiro (recebido: " + valor + ")");
        }
    }
}
