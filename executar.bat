@echo off
REM ===== Compila, testa e executa o benchmark completo; depois abre os graficos =====
REM Requisito: JDK 11 ou superior no PATH (javac e java).
REM Uso: executar.bat            (configuracao completa)
REM      executar.bat --rapido   (teste rapido, ~10 s)
REM      executar.bat --threads 2,4,6,8,12,16   (outras opcoes: veja README.md)
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
echo Compilando...
javac -encoding UTF-8 -d out -sourcepath src src\ordenacao\benchmark\Benchmark.java src\ordenacao\benchmark\TesteCorrecao.java src\ordenacao\grafico\VisualizadorGraficos.java || goto erro
echo Testando correcao dos algoritmos...
java -cp out ordenacao.benchmark.TesteCorrecao || goto erro
echo Executando benchmark (pode levar de 10 a 30 minutos)...
java -Xmx3g -cp out ordenacao.benchmark.Benchmark --controle-bubble %* || goto erro
echo Abrindo visualizador de graficos...
start "" javaw -cp out ordenacao.grafico.VisualizadorGraficos resultados\resumo.csv
goto fim
:erro
echo.
echo Ocorreu um erro. Verifique se o JDK esta instalado (javac -version).
pause
:fim
