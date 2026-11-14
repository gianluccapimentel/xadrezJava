#!/bin/sh
# Roda os testes automáticos: protocolo, rede e memória compartilhada
cd "$(dirname "$0")"
[ -f xadrez.jar ] || ./compilar.sh || exit 1
for t in ProtocolSelfTest NetworkSelfTest SharedMemorySelfTest; do
  java -Dstdout.encoding=UTF-8 -cp xadrez.jar network.$t || exit 1
done
echo "Todos os testes passaram."
