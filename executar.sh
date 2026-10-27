#!/usr/bin/env sh
# Compila, testa e executa o benchmark completo (Linux/macOS). Opcoes extras sao repassadas ao Benchmark.
set -e
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out -sourcepath src src/ordenacao/benchmark/Benchmark.java src/ordenacao/benchmark/TesteCorrecao.java src/ordenacao/grafico/VisualizadorGraficos.java
java -cp out ordenacao.benchmark.TesteCorrecao
java -Xmx3g -cp out ordenacao.benchmark.Benchmark --controle-bubble "$@"
java -cp out ordenacao.grafico.VisualizadorGraficos resultados/resumo.csv &
