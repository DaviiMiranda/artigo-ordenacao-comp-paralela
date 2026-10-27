package ordenacao.algoritmos;

/**
 * Counting Sort — O(n + k), onde k = (máximo − mínimo + 1) é a amplitude dos valores.
 * Não faz comparações entre elementos: conta quantas vezes cada valor aparece e
 * reescreve o vetor em ordem a partir das contagens.
 *
 * Versão paralela (quatro fases, todas com T threads via start()/join()):
 *   1. mínimo/máximo: cada thread varre um bloco do vetor; o resultado é reduzido;
 *   2. contagem local: cada thread conta o seu bloco em um histograma PRIVADO
 *      (sem compartilhamento → sem condição de corrida e sem locks);
 *   3. redução: a faixa de valores [0, k) é dividida em T fatias e cada thread soma
 *      os T histogramas na sua fatia;
 *   4. escrita: com a soma prefixada das fatias, cada thread sabe a posição inicial
 *      da sua fatia no vetor de saída e o preenche de forma independente.
 */
public class CountingSort implements Ordenador {

    /** Limite de segurança para a amplitude de valores (evita alocar histogramas gigantes). */
    private static final long AMPLITUDE_MAXIMA = 1L << 28;

    @Override
    public String nome() {
        return "CountingSort";
    }

    @Override
    public void ordenarSerial(int[] v) {
        int n = v.length;
        if (n <= 1) return;
        int min = v[0], max = v[0];
        for (int x : v) {
            if (x < min) min = x;
            if (x > max) max = x;
        }
        int k = amplitude(min, max);
        int[] cont = new int[k];
        for (int x : v) cont[x - min]++;
        int pos = 0;
        for (int val = 0; val < k; val++) {
            int c = cont[val];
            for (int r = 0; r < c; r++) v[pos++] = val + min;
        }
    }

    @Override
    public void ordenarParalelo(int[] v, int threads) {
        int n = v.length;
        if (n <= 1) return;
        int t = Math.max(1, Math.min(threads, n));

        // Fase 1: mínimo e máximo por bloco
        int[] mins = new int[t], maxs = new int[t];
        Paralelo.executar(t, i -> {
            int lo = Paralelo.inicioBloco(n, t, i), hi = Paralelo.inicioBloco(n, t, i + 1);
            int mn = Integer.MAX_VALUE, mx = Integer.MIN_VALUE;
            for (int j = lo; j < hi; j++) {
                int x = v[j];
                if (x < mn) mn = x;
                if (x > mx) mx = x;
            }
            mins[i] = mn; maxs[i] = mx;
        });
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int i = 0; i < t; i++) {
            min = Math.min(min, mins[i]);
            max = Math.max(max, maxs[i]);
        }
        final int base = min;
        final int k = amplitude(min, max);

        // Fase 2: histogramas locais (um por thread)
        int[][] local = new int[t][];
        Paralelo.executar(t, i -> {
            int[] c = new int[k];
            int lo = Paralelo.inicioBloco(n, t, i), hi = Paralelo.inicioBloco(n, t, i + 1);
            for (int j = lo; j < hi; j++) c[v[j] - base]++;
            local[i] = c;
        });

        // Fase 3: redução por fatias de valores; total[] reaproveita o histograma da thread 0
        int[] total = local[0];
        int fatias = Math.max(1, Math.min(t, k));
        long[] somaFatia = new long[fatias];
        Paralelo.executar(fatias, f -> {
            int a = Paralelo.inicioBloco(k, fatias, f), b = Paralelo.inicioBloco(k, fatias, f + 1);
            long soma = 0;
            for (int val = a; val < b; val++) {
                int c = total[val];
                for (int i = 1; i < t; i++) c += local[i][val];
                total[val] = c;
                soma += c;
            }
            somaFatia[f] = soma;
        });

        // Soma prefixada das fatias (T valores — custo desprezível)
        int[] inicio = new int[fatias];
        long acc = 0;
        for (int f = 0; f < fatias; f++) {
            inicio[f] = (int) acc;
            acc += somaFatia[f];
        }

        // Fase 4: cada thread escreve sua fatia de valores no vetor final
        Paralelo.executar(fatias, f -> {
            int a = Paralelo.inicioBloco(k, fatias, f), b = Paralelo.inicioBloco(k, fatias, f + 1);
            int pos = inicio[f];
            for (int val = a; val < b; val++) {
                int c = total[val];
                int x = val + base;
                for (int r = 0; r < c; r++) v[pos++] = x;
            }
        });
    }

    private static int amplitude(int min, int max) {
        long k = (long) max - min + 1;
        if (k > AMPLITUDE_MAXIMA) {
            throw new IllegalArgumentException("Amplitude de valores grande demais para Counting Sort: " + k);
        }
        return (int) k;
    }
}
