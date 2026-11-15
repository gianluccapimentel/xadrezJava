@echo off
rem Abre o jogo. Rode em duas janelas: uma escolhe 3 (servidor) e a outra 4 (cliente).
cd /d "%~dp0"
chcp 65001 > nul
if not exist xadrez.jar if not exist bin\network\SharedMemorySelfTest.class call compilar.bat || exit /b 1
rem usa o xadrez.jar se existir; senao, as classes compiladas na pasta bin
set "CP=bin"
if exist xadrez.jar set "CP=xadrez.jar"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "%CP%" application.Program
pause
