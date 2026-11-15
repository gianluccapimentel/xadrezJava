#!/bin/sh
# Roda os testes automáticos: regras do xadrez, protocolo, rede e memória compartilhada
cd "$(dirname "$0")"
[ -f xadrez.jar ] || ./compilar.sh || exit 1
for t in chess.ChessRulesSelfTest network.ProtocolSelfTest network.NetworkSelfTest network.SharedMemorySelfTest; do
  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp xadrez.jar $t || exit 1
done
echo "Todos os testes passaram."
