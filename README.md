# Ordenação serial × paralela em Java

Trabalho de Computação Concorrente e Paralela. O projeto compara o desempenho de quatro algoritmos de ordenação (Bubble Sort, Quick Sort, Merge Sort e Counting Sort), cada um com uma versão serial e uma paralela.

## Como executar

Você precisa do JDK 11 ou mais recente (`javac -version` deve funcionar no terminal).

**Windows:** dê dois cliques em `executar.bat` ou rode-o no terminal. O script:
1. compila o projeto em `out/`;
2. roda o teste de correção (`TesteCorrecao`, 1.006 casos comparados com `Arrays.sort`);
3. executa o benchmark completo e grava os CSVs em `resultados/` (leva de 10 a 30 minutos, dependendo da máquina);
4. abre o visualizador de gráficos em Swing.

Para só abrir os gráficos sobre resultados que já existem, use `graficos.bat`.

**Linux/macOS:** `./executar.sh`

Opções do benchmark (podem ser passadas aos scripts):

| Opção | Padrão | Descrição |
|---|---|---|
| `--algoritmos` | `bubble,quick,merge,counting` | algoritmos a testar |
| `--threads` | `2,4,8` | configurações paralelas (a serial sempre roda) |
| `--amostras` | `5` | execuções medidas por configuração |
| `--aquecimento` | `2` | execuções descartadas antes das medições (aquecimento do JIT) |
| `--tipos` | os 5 tipos | `aleatorio,ordenado,reverso,quase_ordenado,poucos_valores` |
| `--tamanhos-quadratico` | `1000,5000,10000,25000,50000` | tamanhos usados no Bubble Sort |
| `--tamanhos-nlogn` | `10000,...,10000000` | tamanhos usados nos demais algoritmos |
| `--saida` | `resultados` | pasta de saída |
| `--rapido` | – | tamanhos reduzidos, para um teste de ~10 s |

Exemplo para uma máquina de 8 núcleos: `executar.bat --threads 2,4,6,8,12,16`

## Estrutura

```
src/ordenacao/algoritmos/   Ordenador (interface), BubbleSort, QuickSort, MergeSort, CountingSort, Paralelo
src/ordenacao/benchmark/    Benchmark, GeradorDados, Estatistica, TesteCorrecao
src/ordenacao/grafico/      VisualizadorGraficos e PainelGrafico (Swing, sem bibliotecas externas)
resultados/                 CSVs gerados (bruto, resumo, teste de Welch, expoentes) e ambiente.txt
graficos/                   figuras usadas no artigo
scripts/                    análise estatística complementar e figuras (Python: pandas, scipy, matplotlib)
```

## Arquivos CSV

- `resultados_brutos.csv`: uma linha por execução: `algoritmo,versao,threads,tamanho,tipo_entrada,amostra,tempo_ms`.
- `resumo.csv`: uma linha por configuração, com média, desvio-padrão, CV, mediana, mínimo, máximo, IC 95%, speedup, eficiência e Karp–Flatt.
- `teste_welch.csv`: teste t de Welch entre a versão serial e cada versão paralela (gerado por `scripts/analise_estatistica.py`).
- `expoentes.csv`: expoente empírico de crescimento do tempo serial.

Os CSVs usam vírgula como separador e ponto como separador decimal. No Excel em português, importe pelo menu Dados > De Texto/CSV e escolha o delimitador "vírgula".

## Estratégias paralelas

- **Bubble Sort**: divide o vetor em p blocos, ordena cada bloco em uma thread (`start`/`join`) e intercala os blocos em paralelo. A opção `--controle-bubble` mede também os mesmos blocos com 1 thread (`blocos_seq`), o que separa o ganho algorítmico do ganho do paralelismo.
- **Quick Sort**: Fork/Join (`RecursiveAction`); depois de cada partição, as duas metades viram tarefas.
- **Merge Sort**: Fork/Join; as duas metades são ordenadas em paralelo e depois intercaladas.
- **Counting Sort**: histogramas privados por thread, redução por fatias de valores e escrita paralela.

## Visualizador (Swing)

```
java -cp out ordenacao.grafico.VisualizadorGraficos resultados/resumo.csv
```

- A aba **Resultados (CSV)** tem gráficos de tempo × tamanho, speedup × threads, eficiência × threads, comparação entre algoritmos e entre tipos de entrada. O CSV é monitorado, então o gráfico se atualiza enquanto o benchmark roda.
- A aba **Execução ao vivo** roda testes dentro da janela e desenha cada amostra assim que ela termina.
- `--png pasta` gera os PNGs sem abrir a janela.
