#!/bin/sh
set -e
DIR=$(cd "$(dirname "$0")/.." && pwd)

# Configura so quando ainda nao existe cache
if [ ! -f "$DIR/build/CMakeCache.txt" ]; then
    cmake -B "$DIR/build" -S "$DIR"
fi

LOG="$DIR/build/last-build.log"
if ! cmake --build "$DIR/build" > "$LOG" 2>&1; then
    cat "$LOG" >&2
    echo "" >&2
    echo "Falha na compilacao — veja as mensagens acima." >&2
    exit 1
fi

exec java -Dstdout.encoding=UTF-8 \
      -cp "$DIR/build/classes" \
      -Djava.library.path="$DIR/build/lib" \
      br.furb.so.stress.Main "$@"
