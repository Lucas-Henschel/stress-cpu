package br.furb.so.stress.cli;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Argumentos de linha de comando.
 *
 * @param threads          threads a criar
 * @param durationSeconds  parada automatica; 0 significa executar ate Ctrl+C
 * @param cores            nucleo logico de cada thread, ou null para deixar o
 *                         escalonador decidir; quando presente, cores.length == threads
 * @param loads            carga alvo de cada thread, em [1, 100]; loads.length == threads
 */
public record Args(int threads, int durationSeconds, int[] cores, int[] loads) {
    public static final String USO = """
        Uso: run.sh [opcoes]

          --threads=N     threads a criar (padrao: numero de nucleos, max 1024)
          --cores=LISTA   nucleos a estressar, uma thread fixada em cada
                          (ex.: 0,2,4-6). Nao combina com --threads
          --duration=Ns   parada automatica apos N segundos (padrao: ate Ctrl+C)
          --load=P        carga alvo, 1 a 100 (padrao: 100). Um valor vale para
                          todas as threads; uma lista (ex.: 80,30) da uma carga
                          por thread, na ordem de --cores

        Exemplos:
          run.sh                          satura todos os nucleos
          run.sh --load=50                mantem a CPU em ~50%
          run.sh --cores=0,2 --load=80,30 nucleo 0 a 80% e nucleo 2 a 30%
          run.sh --cores=1-3 --load=50    nucleos 1, 2 e 3 a 50%
          run.sh --threads=32 --duration=20s   oversubscription por 20s

        No PowerShell, coloque entre aspas os argumentos com virgula:
          .\\scripts\\run.bat "--cores=0,2" "--load=80,30"
        """;

    public static Args parse(String[] argv, int cpuCount) {
        Integer threads = null;
        int[] cores = null;
        int[] loads = {100};
        int duration = 0;

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
                case "cores" -> cores = parseCores(valor, cpuCount);
                case "load" -> loads = parseLoads(valor);
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

        if (threads != null && cores != null) {
            throw new IllegalArgumentException(
                "use --threads ou --cores, nao os dois: com --cores, cada nucleo listado recebe uma thread");
        }

        int n = cores != null ? cores.length : (threads != null ? threads : cpuCount);

        if (n < 1 || n > 1024) {
            throw new IllegalArgumentException("--threads deve estar entre 1 e 1024 (recebido: " + n + ")");
        }

        if (loads.length == 1) {
            int unica = loads[0];
            loads = new int[n];
            Arrays.fill(loads, unica);
        } else if (loads.length != n) {
            String alvo = cores != null ? "nucleos em --cores" : "threads";
            throw new IllegalArgumentException(
                "--load recebeu " + loads.length + " valores, mas ha " + n + " " + alvo
                + " (use um valor para todos, ou um por " + (cores != null ? "nucleo" : "thread") + ")");
        }

        if (durationInformada && duration < 1) {
            throw new IllegalArgumentException("--duration deve ser de ao menos 1 segundo (recebido: " + duration + ")");
        }

        return new Args(n, duration, cores, loads);
    }

    /** true se todas as threads tem a mesma carga alvo. */
    public boolean cargaUniforme() {
        return Arrays.stream(loads).allMatch(l -> l == loads[0]);
    }

    /** Aceita indices e intervalos separados por virgula: "0,2,4-6". */
    private static int[] parseCores(String valor, int cpuCount) {
        Set<Integer> vistos = new LinkedHashSet<>();

        for (String parte : valor.split(",", -1)) {
            String p = parte.trim();
            if (p.isEmpty()) {
                throw new IllegalArgumentException("--cores tem um item vazio (recebido: " + valor + ")");
            }

            int traco = p.indexOf('-');
            int ini;
            int fim;
            if (traco > 0) {
                ini = exigirInteiro(p.substring(0, traco), "cores");
                fim = exigirInteiro(p.substring(traco + 1), "cores");
                if (fim < ini) {
                    throw new IllegalArgumentException("--cores tem intervalo invertido: " + p);
                }
            } else {
                ini = fim = exigirInteiro(p, "cores");
            }

            for (int c = ini; c <= fim; c++) {
                if (c < 0 || c >= cpuCount) {
                    throw new IllegalArgumentException(
                        "--cores: o nucleo " + c + " nao existe; esta maquina tem os nucleos 0 a " + (cpuCount - 1));
                }
                if (!vistos.add(c)) {
                    throw new IllegalArgumentException("--cores: o nucleo " + c + " aparece mais de uma vez");
                }
            }
        }

        return vistos.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] parseLoads(String valor) {
        String[] partes = valor.split(",", -1);
        int[] loads = new int[partes.length];

        for (int i = 0; i < partes.length; i++) {
            loads[i] = exigirInteiro(partes[i], "load");
            if (loads[i] < 1 || loads[i] > 100) {
                throw new IllegalArgumentException("--load deve estar entre 1 e 100 (recebido: " + loads[i] + ")");
            }
        }

        return loads;
    }

    private static int exigirInteiro(String valor, String campo) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + campo + " exige um numero inteiro (recebido: " + valor + ")");
        }
    }
}
