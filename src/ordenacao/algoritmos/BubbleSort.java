package ordenacao.algoritmos;

/**
 * Bubble Sort — O(n²) no caso médio e no pior caso; O(n) com o vetor já ordenado
 * (graças à parada antecipada quando uma passada não faz trocas).
 *
 * Versão paralela (decomposição de dados em blocos):
 *   1. o vetor é dividido em T blocos contíguos;
 *   2. cada thread ordena o seu bloco com Bubble Sort (fase totalmente paralela);
 *   3. os T blocos ordenados são intercalados dois a dois em log2(T) rodadas;
 *      em cada rodada, os pares são intercalados em paralelo.
 *
 * Observação importante para a análise: cada bloco tem n/T elementos, então o trabalho
 * total da fase 2 cai de ~n²/2 para ~n²/(2T). Por isso o Bubble paralelo pode ter
 * speedup maior que T (superlinear) — parte do ganho é algorítmico, não do paralelismo.
 */
public class BubbleSort implements Ordenador {

    @Override
    public String nome() {
        return "BubbleSort";
    }

    @Override
    public void ordenarSerial(int[] v) {
        bubble(v, 0, v.length);
    }

    /** Bubble Sort otimizado em v[lo..hi): encolhe o limite e para se não houver trocas. */
    static void bubble(int[] v, int lo, int hi) {
        int limite = hi - 1;
        while (limite > lo) {
            int ultimaTroca = lo;
            for (int i = lo; i < limite; i++) {
                if (v[i] > v[i + 1]) {
                    int t = v[i];
                    v[i] = v[i + 1];
                    v[i + 1] = t;
                    ultimaTroca = i;
                }
            }
            limite = ultimaTroca; // tudo depois da última troca já está no lugar
        }
    }

    @Override
    public void ordenarParalelo(int[] v, int threads) {
        int n = v.length;
        int t = Math.max(1, Math.min(threads, n));

        // limites dos blocos: bloco i = [lim[i], lim[i+1])
        int[] lim = new int[t + 1];
        for (int i = 0; i <= t; i++) lim[i] = Paralelo.inicioBloco(n, t, i);

        // Fase 1: cada thread ordena o seu bloco
        Paralelo.executar(t, i -> bubble(v, lim[i], lim[i + 1]));

        // Fase 2: intercalação em árvore dos blocos ordenados
        intercalarBlocos(v, lim, true);
    }

    /**
     * Experimento de controle: MESMO algoritmo em blocos da versão paralela, mas executado
     * por uma única thread. Serve para separar o ganho que vem de "ordenar blocos menores"
     * (redução algorítmica de n² para n²/T) do ganho que vem de usar vários núcleos.
     */
    public void ordenarBlocosSequencial(int[] v, int blocos) {
        int n = v.length;
        int t = Math.max(1, Math.min(blocos, n));
        int[] lim = new int[t + 1];
        for (int i = 0; i <= t; i++) lim[i] = Paralelo.inicioBloco(n, t, i);
        for (int i = 0; i < t; i++) bubble(v, lim[i], lim[i + 1]);
        intercalarBlocos(v, lim, false);
    }

    /**
     * Intercala blocos ordenados delimitados por {@code lim}, dois a dois, em paralelo,
     * até restar um único bloco. Usa um vetor auxiliar e alterna origem/destino a cada rodada.
     */
    static void intercalarBlocos(int[] v, int[] limInicial, boolean paralelo) {
        int[] lim = limInicial.clone();
        int blocos = lim.length - 1;
        if (blocos <= 1) return;

        int[] src = v;
        int[] dst = new int[v.length];
        while (blocos > 1) {
            final int[] s = src, d = dst, l = lim;
            int pares = blocos / 2;
            final boolean sobra = (blocos % 2 == 1);
            final int nb = blocos;
            java.util.function.IntConsumer passo = p -> {
                if (p < pares) {
                    int lo = l[2 * p], meio = l[2 * p + 1], hi = l[2 * p + 2];
                    Paralelo.intercalar(s, d, lo, meio, hi);
                } else { // bloco ímpar sem par: apenas copia
                    int lo = l[nb - 1], hi = l[nb];
                    System.arraycopy(s, lo, d, lo, hi - lo);
                }
            };
            int tarefas = pares + (sobra ? 1 : 0);
            if (paralelo) {
                Paralelo.executar(tarefas, passo);
            } else {
                for (int p = 0; p < tarefas; p++) passo.accept(p);
            }
            // novos limites: mantém os índices pares
            int novos = pares + (sobra ? 1 : 0);
            int[] novoLim = new int[novos + 1];
            for (int i = 0; i < novos; i++) novoLim[i] = lim[2 * i];
            novoLim[novos] = lim[blocos];
            lim = novoLim;
            blocos = novos;
            int[] tmp = src; src = dst; dst = tmp;
        }
        if (src != v) {
            System.arraycopy(src, 0, v, 0, v.length);
        }
    }
}
