# Estressador de CPU multiplataforma — C + JNI + Java

Ferramenta acadêmica (Sistemas Operacionais — FURB) que satura a CPU usando
múltiplas threads nativas, com o motor de processamento em C e visualização
em tempo real em Java, integrados via JNI.

## Requisitos

- **CMake ≥ 3.15** — no Windows com MSVC, ≥ 3.20: é a primeira versão que
  emite `/std:c11`, sem o qual o `<stdatomic.h>` usado pelo motor não compila.
- **Compilador C11 com `<stdatomic.h>`** — GCC, Clang, ou o MSVC do Visual
  Studio 2022 17.5+ (versões anteriores do MSVC não trazem o cabeçalho).
- **JDK 17 ou superior** (`javac`/`java` no `PATH`, ou `JAVA_HOME` apontando
  para o JDK). O código usa *records* e *pattern matching* para `instanceof`,
  que exigem Java 16+.

### Instalando o CMake

Download oficial, com instaladores para os três sistemas:
**<https://cmake.org/download/>**

Pelo gerenciador de pacotes:

```bash
brew install cmake                          # macOS (Homebrew)
sudo apt install cmake build-essential      # Debian / Ubuntu
sudo dnf install cmake gcc                  # Fedora
sudo pacman -S cmake base-devel             # Arch
```

```bat
winget install Kitware.CMake                :: Windows
```

No Windows o CMake sozinho não basta: é preciso também o **Visual Studio 2022**
com a carga de trabalho *Desenvolvimento para desktop com C++*, que fornece o
compilador.

Confira a versão instalada — distribuições mais antigas (Ubuntu 20.04, por
exemplo) trazem CMake abaixo do mínimo:

```bash
cmake --version
```

Se for antiga demais, use o instalador oficial do link acima ou o
[repositório APT da Kitware](https://apt.kitware.com/).

## Executando

```bash
./scripts/run.sh [opções]        # Linux / macOS
scripts\run.bat [opções]         # Windows
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
  platform_win.c                Windows (Win32)
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

O C mede o que só ele consegue medir: o tempo de CPU **por thread** (via API
nativa de cada sistema — ver "Plataformas") e a contagem de iterações de cada
worker. O Java mede a carga **global** do sistema via `OperatingSystemMXBean`.

O motor nativo devolve apenas contadores brutos monotônicos; todo o cálculo
de delta e percentual acontece em `StressStats.delta()`, como função pura,
sem JNI e sem estado.

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

## Plataformas

A única parte do código que muda entre sistemas é a camada de plataforma; o
motor, a ponte JNI e o Java inteiro são os mesmos nos três.

| Sistema | Arquivo | Tempo de CPU por thread |
|---|---|---|
| Linux | `platform_posix.c` | `clock_gettime(CLOCK_THREAD_CPUTIME_ID)` |
| macOS | `platform_posix.c`, blocos `#ifdef __APPLE__` | `thread_info()` do Mach — o macOS não tem `CLOCK_THREAD_CPUTIME_ID` |
| Windows | `platform_win.c` | `GetThreadTimes` |

O desenvolvimento e as medições foram feitos em macOS; Linux e Windows.

No Windows, o `GetThreadTimes` tem resolução de ~15,6 ms (o tick do
escalonador), então a utilização por thread sai em degraus e traz até ~1,5%
de ruído em janelas de amostragem de 1 s. É característica da API do sistema,
não do programa.

## Limitações conhecidas

- **A utilização por thread é limitada a `[0, 100]` na exibição.** O
  numerador (`Δcpu`) pode ultrapassar o denominador (`Δreal`) por duas causas
  independentes: uma janela de corrida no motor nativo entre o registro do
  fim do teste e o último incremento de um worker, e a granularidade do
  `GetThreadTimes` no Windows.
- **A carga do sistema (`Sistema: NN%`) depende do HotSpot.** Ela vem de
  `com.sun.management.OperatingSystemMXBean`, presente no HotSpot mas não
  garantida por outras JVMs; sem ela, o renderer exibe `--`. A primeira
  amostra também mostra `--`, pois ainda não há duas leituras para comparar.
- **Afinidade de núcleo não implementada.** O motor não fixa threads em
  núcleos específicos (equivalente a `sched_setaffinity` /
  `SetThreadAffinityMask`). No macOS não existe um equivalente direto e
  utilizável para esse fim, então a funcionalidade ficou fora de escopo por
  ser assimétrica entre as plataformas.
- **`--threads` acima do número de núcleos lógicos é permitido de propósito**
  (oversubscription): a soma das utilizações por thread se aproxima do número
  de núcleos, não de `threads × 100%`. É o comportamento esperado do
  escalonador do SO sob concorrência excedente, não um bug.
