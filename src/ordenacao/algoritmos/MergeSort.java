package ordenacao.algoritmos;

import java.util.concurrent.RecursiveAction;

/**
 * Merge Sort — O(n log n) em todos os casos, estável, usa O(n) de memória auxiliar.
 * Implementação top-down com um único vetor auxiliar alocado uma vez e
 * ordenação por inserção para subvetores pequenos (≤ 32).
 *
 * Versão paralela: Fork/Join. As duas metades são ordenadas em paralelo e depois
 * intercaladas. A intercalação de cada nível é sequencial dentro da tarefa; em
 * particular a intercalação final (n elementos) é feita por uma única thread,
 * o que é a principal fração serial do algoritmo (Lei de Amdahl).
 */
public class MergeSort implements Ordenador {

    private static final int CORTE_INSERCAO = 32;

    @Override
    public String nome() {
        return "MergeSort";
    }

    @Override
    public void ordenarSerial(int[] v) {
        if (v.length <= 1) return;
        int[] aux = new int[v.length];
        merge(v, aux, 0, v.length);
    }

    @Override
    public void ordenarParalelo(int[] v, int threads) {
        if (v.length <= 1) return;
        int[] aux = new int[v.length];
        int limiar = Math.max(8_192, v.length / (threads * 16));
        Paralelo.pool(threads).invoke(new TarefaMerge(v, aux, 0, v.length, limiar));
    }

    /** Ordena v[lo..hi) usando aux como área de trabalho. */
    static void merge(int[] v, int[] aux, int lo, int hi) {
        if (hi - lo <= CORTE_INSERCAO) {
            Paralelo.insercao(v, lo, hi - 1);
            return;
        }
        int meio = (lo + hi) >>> 1;
        merge(v, aux, lo, meio);
        merge(v, aux, meio, hi);
        juntar(v, aux, lo, meio, hi);
    }

    /** Intercala as metades ordenadas v[lo..meio) e v[meio..hi). */
    static void juntar(int[] v, int[] aux, int lo, int meio, int hi) {
        if (v[meio - 1] <= v[meio]) return; // já em ordem: nada a fazer
        System.arraycopy(v, lo, aux, lo, hi - lo);
        Paralelo.intercalar(aux, v, lo, meio, hi);
    }

    private static final class TarefaMerge extends RecursiveAction {
        private final int[] v, aux;
        private final int lo, hi, limiar;

        TarefaMerge(int[] v, int[] aux, int lo, int hi, int limiar) {
            this.v = v; this.aux = aux; this.lo = lo; this.hi = hi; this.limiar = limiar;
        }

        @Override
        protected void compute() {
            if (hi - lo <= limiar) {
                merge(v, aux, lo, hi);
                return;
            }
            int meio = (lo + hi) >>> 1;
            invokeAll(new TarefaMerge(v, aux, lo, meio, limiar),
                      new TarefaMerge(v, aux, meio, hi, limiar));
            juntar(v, aux, lo, meio, hi);
        }
    }
}
