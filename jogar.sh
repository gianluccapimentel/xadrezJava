#!/bin/sh
# Abre o jogo. Rode em dois terminais: um escolhe 3 (servidor) e o outro 4 (cliente).
cd "$(dirname "$0")"
[ -f xadrez.jar ] || ./compilar.sh || exit 1
java -Dstdout.encoding=UTF-8 -jar xadrez.jar
