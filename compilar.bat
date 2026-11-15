@echo off
rem Compila o jogo e gera xadrez.jar (precisa do JDK 17 ou mais novo)
cd /d "%~dp0"
if exist bin rmdir /s /q bin
mkdir bin
javac --release 17 -encoding UTF-8 -d bin -sourcepath src src\application\Program.java src\chess\ChessRulesSelfTest.java src\network\ProtocolSelfTest.java src\network\NetworkSelfTest.java src\network\SharedMemorySelfTest.java || goto erro
echo Main-Class: application.Program> bin\MANIFEST.MF
jar cfm xadrez.jar bin\MANIFEST.MF -C bin application -C bin boardGame -C bin chess -C bin network || goto erro
echo Pronto: xadrez.jar
exit /b 0
:erro
echo Falha ao compilar. Confira se o JDK 17+ esta instalado (javac -version).
exit /b 1
