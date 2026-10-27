package ordenacao.algoritmos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.function.IntConsumer;

/**
 * Utilitários de paralelismo compartilhados pelos algoritmos.
 *
 * - {@link #executar(int, IntConsumer)}: cria N threads, dispara todas com start()
 *   e espera todas com join() — o mesmo padrão visto em aula (Tarefa/ExemploUso).
 * - {@link #pool(int)}: ForkJoinPool com paralelismo fixo, usado pelos algoritmos
 *   de divisão e conquista (Quick e Merge). Os pools são criados uma única vez por
 *   quantidade de threads e reaproveitados, para que o custo de criação não entre na medição.
 */
public final class Paralelo {

    private static final Map<Integer, ForkJoinPool> POOLS = new ConcurrentHashMap<>();

    private Paralelo() { }

    /**
     * Executa {@code tarefa.accept(i)} para i = 0..n-1, cada uma em sua própria thread,
     * e só retorna depois que todas terminarem (barreira implícita via join()).
     */
    public static void executar(int n, IntConsumer tarefa) {
        if (n == 1) {
            tarefa.accept(0);
            return;
        }
        Thread[] threads = new Thread[n];
        Throwable[] erro = new Throwable[1];
        for (int i = 0; i < n; i++) {
            final int id = i;
            threads[i] = new Thread(() -> tarefa.accept(id), "trabalhador-" + i);
            threads[i].setUncaughtExceptionHandler((t, e) -> {
                synchronized (erro) { if (erro[0] == null) erro[0] = e; }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrompido aguardando threads", e);
            }
        }
        if (erro[0] != null) {
            throw new RuntimeException("Falha em thread de trabalho", erro[0]);
        }
    }

    /** Índice inicial do bloco {@code i} quando [0, n) é dividido em {@code partes} blocos contíguos. */
    public static int inicioBloco(int n, int partes, int i) {
        return (int) ((long) n * i / partes);
    }

    /** ForkJoinPool com exatamente {@code paralelismo} threads de trabalho (reaproveitado). */
    public static ForkJoinPool pool(int paralelismo) {
        return POOLS.computeIfAbsent(paralelismo, ForkJoinPool::new);
    }

    /** Encerra os pools criados (chamado ao fim do benchmark). */
    public static void fecharPools() {
        POOLS.values().forEach(ForkJoinPool::shutdown);
        POOLS.clear();
    }

    /** Ordenação por inserção em v[lo..hi] (inclusive) — caso base dos algoritmos recursivos. */
    static void insercao(int[] v, int lo, int hi) {
        for (int i = lo + 1; i <= hi; i++) {
            int x = v[i];
            int j = i - 1;
            while (j >= lo && v[j] > x) {
                v[j + 1] = v[j];
                j--;
            }
            v[j + 1] = x;
        }
    }

    /** Intercala src[lo..meio) e src[meio..hi) em dst[lo..hi). */
    static void intercalar(int[] src, int[] dst, int lo, int meio, int hi) {
        int i = lo, j = meio, k = lo;
        while (i < meio && j < hi) {
            dst[k++] = (src[i] <= src[j]) ? src[i++] : src[j++];
        }
        while (i < meio) dst[k++] = src[i++];
        while (j < hi)   dst[k++] = src[j++];
    }
}
