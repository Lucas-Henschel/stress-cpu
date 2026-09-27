# Estressador de CPU multiplataforma — C + JNI + Java

Ferramenta acadêmica (Sistemas Operacionais — FURB) que satura a CPU usando
múltiplas threads nativas, com o motor de processamento em C e visualização
em tempo real em Java, integrados via JNI.

## Requisitos

- CMake ≥ 3.15
- Um compilador C11
- JDK 17 ou superior (`javac`/`java` no `PATH`, ou `JAVA_HOME` apontando para
  o JDK). O código usa *records* e *pattern matching* para `instanceof`, que
  exigem Java 16+; a verificação foi feita com o JDK 21.

## Executando

```bash
./scripts/run.sh [opções]        # Linux / macOS
scripts\run.bat [opções]         # Windows (quando o suporte existir)
```

Um único comando compila e executa — não é necessário rodar o CMake antes. O
script configura o build na primeira chamada, refaz o build incremental nas
seguintes e só então sobe a aplicação. Se a compilação falhar, ele imprime os
erros e sai com código **1** sem chegar ao `java`, para que o erro real
apareça em vez de um `UnsatisfiedLinkError` ou de classes desatualizadas
rodando em silêncio.

O script resolve os próprios caminhos a partir de `dirname "$0"`, então
funciona de qualquer diretório de trabalho, e repassa todos os argumentos
para a aplicação. A saída do compilador vai para `build/last-build.log` em
vez da tela — ela rolaria o terminal no instante em que o renderer ANSI
desenha o primeiro quadro — e só é exibida se o build falhar.

### Argumentos

| Argumento | Padrão | Faixa | Efeito |
|---|---|---|---|
| `--threads=N` | número de núcleos lógicos | 1–1024 | Threads criadas. Acima do número de núcleos é permitido de propósito, para demonstrar *oversubscription* |
| `--duration=Ns` | infinito (até Ctrl+C) | ≥ 1 | Parada automática após N segundos. Aceita sufixo `s` opcional (`--duration=10` ou `--duration=10s`) |
| `--load=P` | 100 | 1–100 | Carga alvo por thread (duty cycle), em percentual |

Argumento desconhecido, fora de faixa, ou malformado imprime uma mensagem de
erro seguida do texto de uso, e encerra com código de saída **2**.

### Exemplos

```bash
./scripts/run.sh                              # satura todos os nucleos
./scripts/run.sh --load=50                    # mantem a CPU em ~50%
./scripts/run.sh --threads=40 --duration=10s  # oversubscription por 10s
```

Pressione **Ctrl+C** para encerrar a qualquer momento — um shutdown hook para
as threads nativas antes do processo sair (ver "Encerramento e Ctrl+C").

## Compilação manual

Só é necessária para compilar sem executar; `scripts/run.sh` já faz isso
sozinho. O mesmo comando serve para Linux e macOS:

```bash
cmake -B build
cmake --build build
```

Isso gera:

- `build/lib/libcpustress.{dylib,so}` — biblioteca nativa carregada via JNI
- `build/classes/` — as classes Java, compiladas pelo próprio alvo `java` do
  CMake, sem `javac` manual

## Estrutura

```
native/                       camada C
  include/platform.h            contrato com o SO (threads, tempo, memória)
  include/stress_engine.h       API do motor + contrato de concorrência
  platform_posix.c              Linux e macOS
  stress_engine.c               motor: threads, loop de trabalho, duty cycle
  jni_bridge.c                  tradução de tipos C <-> Java (nada mais)

java/br/furb/so/stress/       camada Java
  Main.java                     entry point e laço de amostragem (1s)
  jni/CpuStress.java            declarações native; espelha jni_bridge.c
  metrics/StressStats.java      cálculo puro de delta e utilização
  cli/Args.java                 parsing de --threads, --duration, --load
  ui/Renderer.java              interface de apresentação
  ui/ConsoleRenderer.java       implementação em console ANSI
```

O pacote `jni` é o único acoplado ao C: os nomes das funções em
`jni_bridge.c` (`Java_br_furb_so_stress_jni_CpuStress_*`) são derivados
mecanicamente do pacote e do nome da classe, então renomear a classe ou
movê-la de pacote quebra a ligação em tempo de execução, sem o `javac`
acusar nada. (O nome `native` seria o mais óbvio para esse pacote, mas é
palavra reservada em Java e não pode compor um nome de pacote.)

`ui` depende de `cli` e `metrics`; `metrics` e `cli` não dependem de nada —
são lógica pura e funcionam sem a biblioteca nativa carregada.

A interface `Renderer` é o ponto de extensão para a GUI Swing: um
`SwingRenderer` em `ui` entra sem tocar em C, JNI ou `Main`.

### Divisão da medição

O C mede o que só ele consegue medir: tempo de CPU **por thread**
(`clock_gettime(CLOCK_THREAD_CPUTIME_ID)` no Linux, `thread_info()` no
macOS) e a contagem de iterações de cada worker. O Java mede a carga
**global** do sistema via `OperatingSystemMXBean`.

O motor nativo devolve apenas contadores brutos monotônicos; todo o cálculo
de delta e percentual acontece em `StressStats.delta()`, como função pura.
É por isso que essa lógica não precisa de JNI nem de CPU ocupada para ser
exercitada.

## Encerramento e Ctrl+C

Sem um shutdown hook, `Ctrl+C` mataria a JVM mas deixaria as threads nativas
rodando — consumindo 100% da CPU até o processo ser morto à força pelo SO.
`Main` registra um `Runtime.getRuntime().addShutdownHook(...)` que chama
`CpuStress.stop()` e fecha o renderer.

O hook roda em sua própria thread, tanto no encerramento normal (quando
`--duration` esgota) quanto no `Ctrl+C` — a JVM sempre executa os hooks
registrados antes de sair. Para evitar que a thread principal e a thread do
hook chamem `CpuStress.stop()` ao mesmo tempo (o motor documenta, em
`stress_engine.h`, que `stress_start`/`stress_stop` devem vir de uma única
thread de controle), `Main` usa um `AtomicBoolean` com *compare-and-set*
para que o encerramento efetivo (`stop()` + `renderer.close()`) só aconteça
uma vez, não importa qual caminho chegue primeiro.

Verificado manualmente: rodando `./scripts/run.sh` sem `--duration` e
enviando `SIGINT`, o processo `java` desaparece de `ps`/`pgrep` em poucos
segundos, sem sobra.

## Quadro "piscando vazio" — comportamento intencional

`stress_snapshot()` pode devolver `threadCount() == 0` legitimamente enquanto
o motor está rodando normalmente, não só quando está parado: é o resultado de
uma colisão transitória (microssegundos) do seqlock de geração interno do
motor — ver o bloco "CONTRATO DE CONCORRENCIA" em
`native/include/stress_engine.h`. Se o `ConsoleRenderer` desenhasse essa
amostra como veio, o usuário veria o quadro inteiro piscar vazio no meio de
um teste saudável.

`ConsoleRenderer.render()` trata isso explicitamente: quando
`threadCount() == 0`, a amostra é descartada e o último quadro real permanece
na tela — a função retorna sem escrever nada e **sem atualizar o contador de
linhas desenhadas**, então o próximo quadro válido continua redesenhando
corretamente por cima do último quadro exibido.

## Limitações conhecidas

- **Suporte a Windows: planejado, não implementado.** `native/platform_win.c`
  (implementação Win32 de `platform.h`) ainda não foi escrito. O
  `CMakeLists.txt` já tem a seleção condicional (`if(WIN32)` referenciando
  esse arquivo), mas como o arquivo não existe, `cmake -B build` no Windows
  falha já no configure, com "Cannot find source file:
  native/platform_win.c". Isto não é "implementado mas não testado", é
  ausência de implementação. O `scripts/run.bat` já existe e já configura o
  console para UTF-8 (`chcp 65001` e `-Dstdout.encoding=UTF-8`), preparado
  para quando a camada for escrita.
- **Linux não verificado.** O caminho POSIX é o mesmo do macOS e não há nada
  específico de Darwin fora dos blocos `#ifdef __APPLE__`, mas nenhuma
  execução em Linux foi feita para confirmar.
- **Granularidade do `GetThreadTimes` no Windows** (~15,6 ms, o tick do
  escalonador) é o comportamento **previsto** para quando a camada Win32
  existir: deve gerar até ~1,5% de ruído na utilização medida em janelas de
  amostragem de 1s, por ser característica da própria API do SO. Não é algo
  já observado nesta versão, já que a camada ainda não existe.
- **A utilização por thread é limitada a `[0, 100]` na exibição.** O
  numerador (`Δcpu`) pode ultrapassar o denominador (`Δreal`) por duas causas
  independentes: uma janela de corrida no motor nativo entre o registro do
  fim do teste e o último incremento de um worker (real, hoje, em qualquer
  plataforma), e a granularidade do `GetThreadTimes` no Windows (prevista,
  quando essa camada existir).
- **A carga do sistema (`Sistema: NN%`) depende do HotSpot.** Ela vem de
  `com.sun.management.OperatingSystemMXBean`, presente no HotSpot mas não
  garantida por outras JVMs; sem ela, o renderer exibe `--`. A primeira
  amostra também mostra `--`, pois ainda não há duas leituras para comparar.
- **Afinidade de núcleo não implementada.** O motor não fixa threads em
  núcleos específicos (equivalente a `sched_setaffinity` /
  `SetThreadAffinityMask`). No macOS não existe um equivalente direto e
  utilizável para esse fim, então a funcionalidade ficou fora de escopo por
  esse motivo — e não apenas por Windows não estar implementado.
- **`--threads` acima do número de núcleos lógicos é permitido de propósito**
  (oversubscription): a soma das utilizações por thread se aproxima do número
  de núcleos, não de `threads × 100%`. É o comportamento esperado do
  escalonador do SO sob concorrência excedente, não um bug.
- **Sem testes automatizados.** As suítes em C e Java foram removidas do
  projeto; a verificação é manual, executando a aplicação.
