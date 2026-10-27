package ordenacao.benchmark;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import ordenacao.algoritmos.BubbleSort;
import ordenacao.algoritmos.Ordenador;
import ordenacao.algoritmos.Paralelo;
import ordenacao.benchmark.GeradorDados.Tipo;

/**
 * Framework de testes. Para cada combinação
 *     algoritmo × tamanho × tipo de entrada × configuração (serial, 2, 4, 8... threads)
 * executa {@code aquecimento} rodadas descartadas (aquecem o JIT da JVM) e depois
 * {@code amostras} rodadas medidas com System.nanoTime(). Cada rodada recebe uma cópia
 * nova do mesmo vetor de entrada; a cópia não entra no tempo. Depois de cada rodada
 * o resultado é conferido (ordenado + mesmos elementos).
 *
 * Uso:
 *   java -cp out ordenacao.benchmark.Benchmark [opções]
 *
 * Opções:
 *   --algoritmos bubble,quick,merge,counting
 *   --threads 2,4,8                (a versão serial é sempre incluída)
 *   --amostras 5
 *   --aquecimento 2
 *   --tipos aleatorio,ordenado,reverso,quase_ordenado,poucos_valores
 *   --tamanhos-quadratico 1000,5000,10000,25000,50000     (Bubble Sort)
 *   --tamanhos-nlogn 10000,50000,100000,1000000,5000000,10000000
 *   --saida resultados
 *   --controle-bubble              (roda também o Bubble "em blocos" com 1 thread)
 *   --rapido                       (tamanhos reduzidos, para testar se tudo funciona)
 */
public class Benchmark {

    private final List<String> algoritmos;
    private final int[] threads;
    private final int amostras;
    private final int aquecimento;
    private final List<Tipo> tipos;
    private final int[] tamanhosQuadratico;
    private final int[] tamanhosNlogn;
    private final Path saida;
    private final boolean controleBubble;

    /** Ouvinte opcional — usado pelo visualizador Swing para desenhar os resultados ao vivo. */
    private Consumer<Medicao> ouvinte = m -> { };

    /** Uma medição individual (uma linha do CSV bruto). */
    public static final class Medicao {
        public final String algoritmo, versao, tipo;
        public final int threads, tamanho, amostra;
        public final double tempoMs;

        Medicao(String algoritmo, String versao, int threads, int tamanho, String tipo, int amostra, double tempoMs) {
            this.algoritmo = algoritmo; this.versao = versao; this.threads = threads;
            this.tamanho = tamanho; this.tipo = tipo; this.amostra = amostra; this.tempoMs = tempoMs;
        }

        String csv() {
            return String.join(",", algoritmo, versao, String.valueOf(threads), String.valueOf(tamanho),
                    tipo, String.valueOf(amostra), String.format(Locale.US, "%.4f", tempoMs));
        }
    }

    public Benchmark(Map<String, String> op) {
        boolean rapido = op.containsKey("rapido");
        this.algoritmos = Arrays.asList(op.getOrDefault("algoritmos", "bubble,quick,merge,counting").split(","));
        this.threads = inteiros(op.getOrDefault("threads", "2,4,8"));
        this.amostras = Integer.parseInt(op.getOrDefault("amostras", "5"));
        this.aquecimento = Integer.parseInt(op.getOrDefault("aquecimento", "2"));
        List<Tipo> ts = new ArrayList<>();
        for (String t : op.getOrDefault("tipos", "aleatorio,ordenado,reverso,quase_ordenado,poucos_valores").split(",")) {
            ts.add(Tipo.porId(t));
        }
        this.tipos = ts;
        this.tamanhosQuadratico = inteiros(op.getOrDefault("tamanhos-quadratico",
                rapido ? "1000,5000" : "1000,5000,10000,25000,50000"));
        this.tamanhosNlogn = inteiros(op.getOrDefault("tamanhos-nlogn",
                rapido ? "10000,100000" : "10000,50000,100000,1000000,5000000,10000000"));
        this.saida = Paths.get(op.getOrDefault("saida", "resultados"));
        this.controleBubble = op.containsKey("controle-bubble");
    }

    public void setOuvinte(Consumer<Medicao> ouvinte) {
        this.ouvinte = ouvinte;
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> op = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            if (!args[i].startsWith("--")) continue;
            String chave = args[i].substring(2);
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                op.put(chave, args[++i]);
            } else {
                op.put(chave, "true");
            }
        }
        new Benchmark(op).executar();
    }

    public void executar() throws IOException {
        Files.createDirectories(saida);
        Path bruto = saida.resolve("resultados_brutos.csv");
        Path resumo = saida.resolve("resumo.csv");
        gravarAmbiente(saida.resolve("ambiente.txt"));

        long inicioGeral = System.nanoTime();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(bruto, StandardCharsets.UTF_8))) {
            out.println("algoritmo,versao,threads,tamanho,tipo_entrada,amostra,tempo_ms");
            for (String nomeAlg : algoritmos) {
                Ordenador alg = Ordenador.porNome(nomeAlg);
                int[] tamanhos = alg instanceof BubbleSort ? tamanhosQuadratico : tamanhosNlogn;
                for (int n : tamanhos) {
                    for (Tipo tipo : tipos) {
                        int[] base = GeradorDados.gerar(tipo, n);
                        long[] assinatura = GeradorDados.assinatura(base);

                        // configuração serial + configurações paralelas
                        List<Integer> configs = new ArrayList<>();
                        configs.add(1);
                        for (int t : threads) if (t > 1) configs.add(t);

                        for (int p : configs) {
                            String versao = p == 1 ? "serial" : "paralelo";
                            medir(out, alg.nome(), versao, p, n, tipo, base, assinatura,
                                    v -> alg.ordenar(v, p));
                        }
                        if (controleBubble && alg instanceof BubbleSort && tipo == Tipo.ALEATORIO) {
                            BubbleSort b = (BubbleSort) alg;
                            for (int p : threads) {
                                if (p > 1) {
                                    medir(out, alg.nome(), "blocos_seq", p, n, tipo, base, assinatura,
                                            v -> b.ordenarBlocosSequencial(v, p));
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            Paralelo.fecharPools();
        }
        Estatistica.gerarResumo(bruto, resumo);
        double totalS = (System.nanoTime() - inicioGeral) / 1e9;
        System.out.printf(Locale.US, "%nConcluido em %.1f s.%nBruto:  %s%nResumo: %s%n",
                totalS, bruto.toAbsolutePath(), resumo.toAbsolutePath());
    }

    private void medir(PrintWriter out, String nomeAlg, String versao, int p, int n, Tipo tipo,
                       int[] base, long[] assinatura, Consumer<int[]> ordenar) {
        // aquecimento: executa e descarta
        for (int w = 0; w < aquecimento; w++) {
            int[] v = base.clone();
            ordenar.accept(v);
        }
        double soma = 0;
        for (int a = 1; a <= amostras; a++) {
            int[] v = base.clone();
            System.gc(); // fora da medição: reduz a chance de coleta de lixo durante a ordenação
            long t0 = System.nanoTime();
            ordenar.accept(v);
            long t1 = System.nanoTime();
            if (!GeradorDados.estaOrdenado(v) || !GeradorDados.mesmaAssinatura(assinatura, GeradorDados.assinatura(v))) {
                throw new IllegalStateException("Resultado incorreto: " + nomeAlg + " " + versao + " p=" + p
                        + " n=" + n + " tipo=" + tipo.id);
            }
            double ms = (t1 - t0) / 1e6;
            soma += ms;
            Medicao m = new Medicao(nomeAlg, versao, p, n, tipo.id, a, ms);
            out.println(m.csv());
            ouvinte.accept(m);
        }
        out.flush();
        System.out.printf(Locale.US, "%-12s %-10s p=%-2d n=%-9d %-15s media=%10.3f ms%n",
                nomeAlg, versao, p, n, tipo.id, soma / amostras);
    }

    private void gravarAmbiente(Path arquivo) throws IOException {
        Runtime rt = Runtime.getRuntime();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(arquivo, StandardCharsets.UTF_8))) {
            out.println("data_hora=" + LocalDateTime.now());
            out.println("nucleos_disponiveis=" + rt.availableProcessors());
            out.println("memoria_max_jvm_mb=" + rt.maxMemory() / (1024 * 1024));
            out.println("java_version=" + System.getProperty("java.version"));
            out.println("java_vm=" + System.getProperty("java.vm.name"));
            out.println("os=" + System.getProperty("os.name") + " " + System.getProperty("os.version")
                    + " (" + System.getProperty("os.arch") + ")");
            out.println("jvm_args=" + ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
                    .filter(a -> a.startsWith("-X")).reduce((a, b) -> a + " " + b).orElse(""));
            out.println("threads=" + Arrays.toString(threads));
            out.println("amostras=" + amostras + " aquecimento=" + aquecimento);
            out.println("tamanhos_quadratico=" + Arrays.toString(tamanhosQuadratico));
            out.println("tamanhos_nlogn=" + Arrays.toString(tamanhosNlogn));
        }
    }

    private static int[] inteiros(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .mapToInt(Integer::parseInt).toArray();
    }
}
