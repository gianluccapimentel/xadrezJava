@echo off
rem Roda os testes automaticos: protocolo, rede e memoria compartilhada
cd /d "%~dp0"
chcp 65001 > nul
if not exist xadrez.jar call compilar.bat || exit /b 1
for %%t in (ProtocolSelfTest NetworkSelfTest SharedMemorySelfTest) do (
  java -Dstdout.encoding=UTF-8 -cp xadrez.jar network.%%t || goto erro
)
echo Todos os testes passaram.
pause
exit /b 0
:erro
echo Um teste falhou.
pause
exit /b 1
