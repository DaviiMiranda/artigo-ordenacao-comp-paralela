package ordenacao.benchmark;

import java.util.Arrays;
import java.util.Random;

/**
 * Gera os conjuntos de entrada do experimento. A semente é fixa e derivada do
 * tamanho e do tipo, de modo que todos os algoritmos e todas as configurações
 * de threads recebem exatamente o mesmo vetor — a comparação é justa e reprodutível.
 */
public final class GeradorDados {

    /** Natureza do conjunto de dados de entrada. */
    public enum Tipo {
        ALEATORIO("aleatorio", "Aleatório: valores uniformes em [0, n)"),
        ORDENADO("ordenado", "Já ordenado (crescente)"),
        REVERSO("reverso", "Ordenado de forma decrescente"),
        QUASE_ORDENADO("quase_ordenado", "Ordenado com 5% das posições trocadas"),
        POUCOS_VALORES("poucos_valores", "Muitas repetições: valores em [0, 100)");

        public final String id;
        public final String descricao;

        Tipo(String id, String descricao) {
            this.id = id;
            this.descricao = descricao;
        }

        public static Tipo porId(String id) {
            for (Tipo t : values()) if (t.id.equalsIgnoreCase(id.trim())) return t;
            throw new IllegalArgumentException("Tipo de entrada desconhecido: " + id);
        }
    }

    private GeradorDados() { }

    public static int[] gerar(Tipo tipo, int n) {
        Random rnd = new Random(42L * 1_000_003L + n * 31L + tipo.ordinal());
        int[] v = new int[n];
        switch (tipo) {
            case ALEATORIO:
                for (int i = 0; i < n; i++) v[i] = rnd.nextInt(Math.max(1, n));
                break;
            case ORDENADO:
                for (int i = 0; i < n; i++) v[i] = i;
                break;
            case REVERSO:
                for (int i = 0; i < n; i++) v[i] = n - 1 - i;
                break;
            case QUASE_ORDENADO:
                for (int i = 0; i < n; i++) v[i] = i;
                int trocas = Math.max(1, n / 20);
                for (int t = 0; t < trocas; t++) {
                    int a = rnd.nextInt(n), b = rnd.nextInt(n);
                    int x = v[a]; v[a] = v[b]; v[b] = x;
                }
                break;
            case POUCOS_VALORES:
                for (int i = 0; i < n; i++) v[i] = rnd.nextInt(100);
                break;
            default:
                throw new IllegalStateException();
        }
        return v;
    }

    /** Verifica se o vetor está em ordem não decrescente. */
    public static boolean estaOrdenado(int[] v) {
        for (int i = 1; i < v.length; i++) if (v[i - 1] > v[i]) return false;
        return true;
    }

    /** Assinatura simples do multiconjunto (soma e soma de quadrados) para detectar perda/duplicação de elementos. */
    public static long[] assinatura(int[] v) {
        long s = 0, s2 = 0;
        for (int x : v) { s += x; s2 += (long) x * x; }
        return new long[] { s, s2 };
    }

    public static boolean mesmaAssinatura(long[] a, long[] b) {
        return Arrays.equals(a, b);
    }
}
