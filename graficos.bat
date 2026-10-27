@echo off
REM Abre apenas o visualizador de graficos (Swing) sobre os resultados ja gerados.
cd /d "%~dp0"
if not exist out\ordenacao\grafico\VisualizadorGraficos.class (
  if not exist out mkdir out
  javac -encoding UTF-8 -d out -sourcepath src src\ordenacao\benchmark\Benchmark.java src\ordenacao\grafico\VisualizadorGraficos.java
)
start "" javaw -cp out ordenacao.grafico.VisualizadorGraficos resultados\resumo.csv
