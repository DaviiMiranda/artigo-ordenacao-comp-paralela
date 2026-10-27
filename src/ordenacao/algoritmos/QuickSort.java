package ordenacao.algoritmos;

import java.util.concurrent.RecursiveAction;

/**
 * Quick Sort — O(n log n) no caso médio.
 * Partição de Hoare com pivô pela mediana de três (evita o pior caso em vetores
 * ordenados/reversos) e ordenação por inserção para subvetores pequenos (≤ 16).
 *
 * Versão paralela: paralelismo de tarefas com Fork/Join. Depois de particionar,
 * as duas metades são independentes e viram duas tarefas; abaixo de um limiar
 * de tamanho a tarefa passa a executar a versão serial (evita criar tarefas
 * pequenas demais, cujo custo de escalonamento supera o ganho).
 *
 * Limitação inerente: a primeira partição percorre o vetor inteiro com uma única
 * thread — essa fração sequencial limita o speedup (Lei de Amdahl).
 */
public class QuickSort implements Ordenador {

    private static final int CORTE_INSERCAO = 16;

    @Override
    public String nome() {
        return "QuickSort";
    }

    @Override
    public void ordenarSerial(int[] v) {
        if (v.length > 1) quick(v, 0, v.length - 1);
    }

    @Override
    public void ordenarParalelo(int[] v, int threads) {
        if (v.length <= 1) return;
        int limiar = Math.max(8_192, v.length / (threads * 16));
        Paralelo.pool(threads).invoke(new TarefaQuick(v, 0, v.length - 1, limiar));
    }

    /** Quick Sort serial em v[lo..hi]; recursão no lado menor e laço no maior (pilha O(log n)). */
    static void quick(int[] v, int lo, int hi) {
        while (hi - lo > CORTE_INSERCAO) {
            int p = particionar(v, lo, hi);
            if (p - lo < hi - p) {
                quick(v, lo, p);
                lo = p + 1;
            } else {
                quick(v, p + 1, hi);
                hi = p;
            }
        }
        Paralelo.insercao(v, lo, hi);
    }

    /** Partição de Hoare; retorna j tal que v[lo..j] ≤ pivô ≤ v[j+1..hi]. */
    static int particionar(int[] v, int lo, int hi) {
        int meio = lo + (hi - lo) / 2;
        // mediana de três -> v[meio]
        if (v[meio] < v[lo]) troca(v, meio, lo);
        if (v[hi] < v[lo])   troca(v, hi, lo);
        if (v[hi] < v[meio]) troca(v, hi, meio);
        int pivo = v[meio];

        int i = lo - 1, j = hi + 1;
        while (true) {
            do { i++; } while (v[i] < pivo);
            do { j--; } while (v[j] > pivo);
            if (i >= j) return j;
            troca(v, i, j);
        }
    }

    private static void troca(int[] v, int a, int b) {
        int t = v[a];
        v[a] = v[b];
        v[b] = t;
    }

    /** Tarefa Fork/Join: particiona e dispara as duas metades em paralelo. */
    private static final class TarefaQuick extends RecursiveAction {
        private final int[] v;
        private final int lo, hi, limiar;

        TarefaQuick(int[] v, int lo, int hi, int limiar) {
            this.v = v; this.lo = lo; this.hi = hi; this.limiar = limiar;
        }

        @Override
        protected void compute() {
            if (hi - lo < limiar) {
                quick(v, lo, hi);
                return;
            }
            int p = particionar(v, lo, hi);
            invokeAll(new TarefaQuick(v, lo, p, limiar),
                      new TarefaQuick(v, p + 1, hi, limiar));
        }
    }
}
