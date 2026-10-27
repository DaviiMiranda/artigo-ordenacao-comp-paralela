package ordenacao.benchmark;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lê o CSV bruto (uma linha por execução) e produz o CSV de resumo com
 * estatística descritiva por configuração:
 *   média, desvio-padrão amostral, coeficiente de variação, mediana, mínimo, máximo,
 *   intervalo de confiança de 95% (t de Student), speedup e eficiência.
 *
 * Speedup  S(p) = T_serial / T_paralelo(p)   (usando as médias)
 * Eficiência E(p) = S(p) / p
 * Karp–Flatt  e(p) = (1/S − 1/p) / (1 − 1/p)  — fração serial estimada experimentalmente
 *
 * Também pode ser executado sozinho para regenerar o resumo:
 *   java -cp out ordenacao.benchmark.Estatistica resultados/resultados_brutos.csv resultados/resumo.csv
 */
public final class Estatistica {

    /** Valores críticos t(0,975; gl) para gl = 1..30. */
    private static final double[] T975 = {
        12.706, 4.303, 3.182, 2.776, 2.571, 2.447, 2.365, 2.306, 2.262, 2.228,
        2.201, 2.179, 2.160, 2.145, 2.131, 2.120, 2.110, 2.101, 2.093, 2.086,
        2.080, 2.074, 2.069, 2.064, 2.060, 2.056, 2.052, 2.048, 2.045, 2.042
    };

    private Estatistica() { }

    public static void main(String[] args) throws IOException {
        Path bruto = Paths.get(args.length > 0 ? args[0] : "resultados/resultados_brutos.csv");
        Path resumo = Paths.get(args.length > 1 ? args[1] : "resultados/resumo.csv");
        gerarResumo(bruto, resumo);
        System.out.println("Resumo gravado em " + resumo.toAbsolutePath());
    }

    public static double tCritico(int gl) {
        if (gl <= 0) return Double.NaN;
        return gl <= T975.length ? T975[gl - 1] : 1.96;
    }

    public static void gerarResumo(Path bruto, Path resumo) throws IOException {
        // chave: algoritmo|versao|threads|tamanho|tipo -> tempos
        Map<String, List<Double>> grupos = new LinkedHashMap<>();
        try (BufferedReader in = Files.newBufferedReader(bruto, StandardCharsets.UTF_8)) {
            String linha = in.readLine(); // cabeçalho
            while ((linha = in.readLine()) != null) {
                if (linha.isBlank()) continue;
                String[] c = linha.split(",");
                // algoritmo,versao,threads,tamanho,tipo_entrada,amostra,tempo_ms
                String chave = c[0] + "|" + c[1] + "|" + c[2] + "|" + c[3] + "|" + c[4];
                grupos.computeIfAbsent(chave, x -> new ArrayList<>()).add(Double.parseDouble(c[6]));
            }
        }

        // médias seriais para o speedup: algoritmo|tamanho|tipo -> média
        Map<String, Double> mediaSerial = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> e : grupos.entrySet()) {
            String[] k = e.getKey().split("\\|");
            if (k[1].equals("serial")) {
                mediaSerial.put(k[0] + "|" + k[3] + "|" + k[4], media(e.getValue()));
            }
        }

        Files.createDirectories(resumo.toAbsolutePath().getParent());
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(resumo, StandardCharsets.UTF_8))) {
            out.println("algoritmo,versao,threads,tamanho,tipo_entrada,amostras,media_ms,desvio_ms,cv_pct,"
                    + "mediana_ms,min_ms,max_ms,ic95_inf_ms,ic95_sup_ms,speedup,eficiencia,karp_flatt");
            for (Map.Entry<String, List<Double>> e : grupos.entrySet()) {
                String[] k = e.getKey().split("\\|");
                List<Double> t = e.getValue();
                int n = t.size();
                double m = media(t);
                double s = desvio(t, m);
                double[] ord = t.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                double med = (n % 2 == 1) ? ord[n / 2] : (ord[n / 2 - 1] + ord[n / 2]) / 2.0;
                double margem = n > 1 ? tCritico(n - 1) * s / Math.sqrt(n) : 0;
                int p = Integer.parseInt(k[2]);
                Double ts = mediaSerial.get(k[0] + "|" + k[3] + "|" + k[4]);
                double speedup = (ts != null && m > 0) ? ts / m : Double.NaN;
                double efic = speedup / p;
                double kf = (p > 1 && !Double.isNaN(speedup))
                        ? (1.0 / speedup - 1.0 / p) / (1.0 - 1.0 / p) : Double.NaN;
                out.println(String.join(",",
                        k[0], k[1], k[2], k[3], k[4], String.valueOf(n),
                        f(m), f(s), f(m > 0 ? 100 * s / m : 0), f(med), f(ord[0]), f(ord[n - 1]),
                        f(Math.max(0, m - margem)), f(m + margem), f(speedup), f(efic), f(kf)));
            }
        }
    }

    static double media(List<Double> v) {
        double s = 0;
        for (double x : v) s += x;
        return s / v.size();
    }

    static double desvio(List<Double> v, double m) {
        if (v.size() < 2) return 0;
        double s = 0;
        for (double x : v) s += (x - m) * (x - m);
        return Math.sqrt(s / (v.size() - 1));
    }

    private static String f(double x) {
        if (Double.isNaN(x) || Double.isInfinite(x)) return "";
        return String.format(Locale.US, "%.4f", x);
    }

    /** Utilitário usado pelo visualizador: lê um CSV simples em lista de mapas coluna->valor. */
    public static List<Map<String, String>> lerCsv(Path arquivo) throws IOException {
        List<Map<String, String>> linhas = new ArrayList<>();
        try (BufferedReader in = Files.newBufferedReader(arquivo, StandardCharsets.UTF_8)) {
            String cab = in.readLine();
            if (cab == null) return linhas;
            String[] colunas = cab.split(",");
            String l;
            while ((l = in.readLine()) != null) {
                if (l.isBlank()) continue;
                String[] c = Arrays.copyOf(l.split(",", -1), colunas.length);
                Map<String, String> m = new LinkedHashMap<>();
                for (int i = 0; i < colunas.length; i++) m.put(colunas[i], c[i] == null ? "" : c[i]);
                linhas.add(m);
            }
        }
        return linhas;
    }
}
