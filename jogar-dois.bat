@echo off
rem Abre as duas janelas da partida (e o espectador) nesta mesma maquina.
cd /d "%~dp0"
if not exist xadrez.jar if not exist bin\network\SharedMemorySelfTest.class call compilar.bat || exit /b 1
start "Xadrez - BRANCAS (servidor)" cmd /k jogar.bat
timeout /t 2 > nul
start "Xadrez - PRETAS (cliente)" cmd /k jogar.bat
echo Nas janelas: a das BRANCAS escolhe 3, a das PRETAS escolhe 4. Porta e endereco: so Enter.
echo Para ver o espectador da memoria compartilhada, abra outra janela com jogar.bat e escolha 5.
exit /b 0
