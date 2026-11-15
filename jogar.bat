@echo off
rem Abre o jogo. Rode em duas janelas: uma escolhe 3 (servidor) e a outra 4 (cliente).
cd /d "%~dp0"
chcp 65001 > nul
if not exist xadrez.jar call compilar.bat || exit /b 1
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar xadrez.jar
pause
