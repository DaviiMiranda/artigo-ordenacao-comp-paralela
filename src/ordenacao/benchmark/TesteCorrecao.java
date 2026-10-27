package ordenacao.benchmark;

import java.util.Arrays;
import java.util.Random;

import ordenacao.algoritmos.BubbleSort;
import ordenacao.algoritmos.Ordenador;
import ordenacao.algoritmos.Paralelo;

/**
 * Teste de correção: compara a saída de cada algoritmo (serial e paralelo, com 1..8 threads)
 * com java.util.Arrays.sort em casos-limite e em milhares de vetores aleatórios.
 *
 *   java -cp out ordenacao.benchmark.TesteCorrecao
 */
public class TesteCorrecao {

    public static void main(String[] args) {
        String[] nomes = { "bubble", "quick", "merge", "counting" };
        Random rnd = new Random(7);
        int casos = 0;

        int[][] limites = {
            {}, { 5 }, { 2, 1 }, { 1, 2 }, { 3, 3, 3, 3 }, { -5, 10, -5, 0, 7, -100, 100 },
            { Integer.MAX_VALUE - 2, Integer.MAX_VALUE, Integer.MAX_VALUE - 1 },
            { 9, 8, 7, 6, 5, 4, 3, 2, 1, 0 }
        };

        for (String nome : nomes) {
            Ordenador alg = Ordenador.porNome(nome);
            for (int[] caso : limites) {
                for (int p = 1; p <= 8; p++) { verificar(alg, caso, p); casos++; }
            }
            int maxN = nome.equals("bubble") ? 3_000 : 200_000;
            for (int r = 0; r < 150; r++) {
                int n = r < 100 ? rnd.nextInt(200) : rnd.nextInt(maxN);
                int amplitude = 1 + rnd.nextInt(r % 3 == 0 ? 10 : 1_000_000);
                int[] v = new int[n];
                for (int i = 0; i < n; i++) v[i] = rnd.nextInt(amplitude) - amplitude / 2;
                int p = 1 + rnd.nextInt(8);
                verificar(alg, v, p);
                casos++;
            }
            for (GeradorDados.Tipo tipo : GeradorDados.Tipo.values()) {
                int[] v = GeradorDados.gerar(tipo, nome.equals("bubble") ? 4_000 : 300_000);
                for (int p : new int[] { 1, 2, 3, 4, 8 }) { verificar(alg, v, p); casos++; }
            }
            System.out.println("OK  " + alg.nome());
        }
        // controle do Bubble em blocos (1 thread)
        BubbleSort b = new BubbleSort();
        for (int r = 0; r < 50; r++) {
            int[] v = rnd.ints(rnd.nextInt(2_000), -1000, 1000).toArray();
            int[] esperado = v.clone();
            Arrays.sort(esperado);
            b.ordenarBlocosSequencial(v, 1 + rnd.nextInt(8));
            if (!Arrays.equals(v, esperado)) throw new AssertionError("Bubble blocos_seq falhou");
            casos++;
        }
        Paralelo.fecharPools();
        System.out.println("Todos os " + casos + " casos passaram.");
    }

    private static void verificar(Ordenador alg, int[] original, int threads) {
        int[] v = original.clone();
        int[] esperado = original.clone();
        Arrays.sort(esperado);
        alg.ordenar(v, threads);
        if (!Arrays.equals(v, esperado)) {
            throw new AssertionError(alg.nome() + " falhou com " + threads + " threads, n=" + original.length);
        }
    }
}
