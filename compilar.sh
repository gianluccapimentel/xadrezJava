#!/bin/sh
# Compila o jogo e gera xadrez.jar (precisa do JDK 17 ou mais novo)
cd "$(dirname "$0")"
rm -rf bin && mkdir -p bin
javac --release 17 -encoding UTF-8 -d bin -sourcepath src src/application/Program.java src/chess/ChessRulesSelfTest.java src/network/ProtocolSelfTest.java src/network/NetworkSelfTest.java src/network/SharedMemorySelfTest.java || exit 1
printf 'Main-Class: application.Program\n' > bin/MANIFEST.MF
jar cfm xadrez.jar bin/MANIFEST.MF -C bin application -C bin boardGame -C bin chess -C bin network || exit 1
echo "Pronto: xadrez.jar"
