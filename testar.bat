@echo off
rem Roda os testes automaticos: regras do xadrez, protocolo, rede e memoria compartilhada
cd /d "%~dp0"
chcp 65001 > nul
if not exist xadrez.jar if not exist bin\network\SharedMemorySelfTest.class call compilar.bat || exit /b 1
rem usa o xadrez.jar se existir; senao, as classes compiladas na pasta bin
set "CP=bin"
if exist xadrez.jar set "CP=xadrez.jar"
for %%t in (chess.ChessRulesSelfTest network.ProtocolSelfTest network.NetworkSelfTest network.SharedMemorySelfTest) do (
  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "%CP%" %%t || goto erro
)
echo Todos os testes passaram.
pause
exit /b 0
:erro
echo Um teste falhou.
pause
exit /b 1
