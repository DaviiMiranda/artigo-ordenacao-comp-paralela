package ordenacao.algoritmos;

/**
 * Contrato comum a todos os algoritmos de ordenação do estudo.
 * Cada algoritmo oferece uma versão serial e uma versão paralela,
 * ambas ordenando o vetor no próprio lugar (in-place do ponto de vista de quem chama).
 */
public interface Ordenador {

    /** Nome curto usado nos arquivos CSV e nos gráficos. */
    String nome();

    /** Ordena o vetor usando uma única thread. */
    void ordenarSerial(int[] v);

    /** Ordena o vetor usando {@code threads} threads de trabalho. */
    void ordenarParalelo(int[] v, int threads);

    /** Ponto único de entrada usado pelo framework: threads == 1 significa serial. */
    default void ordenar(int[] v, int threads) {
        if (threads <= 1) {
            ordenarSerial(v);
        } else {
            ordenarParalelo(v, threads);
        }
    }

    /** Fábrica a partir do nome usado na linha de comando. */
    static Ordenador porNome(String nome) {
        switch (nome.trim().toLowerCase()) {
            case "bubble":   case "bubblesort":   return new BubbleSort();
            case "quick":    case "quicksort":    return new QuickSort();
            case "merge":    case "mergesort":    return new MergeSort();
            case "counting": case "countingsort": return new CountingSort();
            default: throw new IllegalArgumentException("Algoritmo desconhecido: " + nome);
        }
    }
}
