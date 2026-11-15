@echo off
rem Compila o jogo na pasta bin e gera xadrez.jar (precisa do JDK 17 ou mais novo)
cd /d "%~dp0"
if exist bin rmdir /s /q bin
mkdir bin
javac --release 17 -encoding UTF-8 -d bin -sourcepath src src\application\Program.java src\chess\ChessRulesSelfTest.java src\network\ProtocolSelfTest.java src\network\NetworkSelfTest.java src\network\SharedMemorySelfTest.java || goto erro
echo Main-Class: application.Program> bin\MANIFEST.MF
rem A ferramenta jar pode nao estar no PATH (acontece com o instalador da Oracle).
rem Sem ela o jogo roda direto da pasta bin, entao nao e erro.
set "JAR="
where jar >nul 2>nul && set "JAR=jar"
if not defined JAR if exist "%JAVA_HOME%\bin\jar.exe" set "JAR=%JAVA_HOME%\bin\jar.exe"
if not defined JAR goto semjar
"%JAR%" cfm xadrez.jar bin\MANIFEST.MF -C bin application -C bin boardGame -C bin chess -C bin network || goto erro
echo Pronto: xadrez.jar
exit /b 0
:semjar
if exist xadrez.jar del xadrez.jar
echo Pronto: classes compiladas na pasta bin.
echo A ferramenta jar nao esta no PATH, entao o xadrez.jar nao foi gerado. O jogar.bat e o testar.bat funcionam do mesmo jeito.
exit /b 0
:erro
echo Falha ao compilar. Confira se o JDK 17+ esta instalado (javac -version).
exit /b 1
