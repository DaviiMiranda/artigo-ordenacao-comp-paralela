package ordenacao.grafico;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import ordenacao.algoritmos.Ordenador;
import ordenacao.algoritmos.Paralelo;
import ordenacao.benchmark.Estatistica;
import ordenacao.benchmark.GeradorDados;

/**
 * Visualizador de gráficos dinâmicos em Swing (item EXTRA do trabalho).
 *
 * Aba "Resultados (CSV)": lê resultados/resumo.csv e permite trocar, em tempo real,
 *   o tipo de gráfico, o algoritmo, o tipo de entrada, a configuração de threads e a escala.
 *   O arquivo é monitorado: se o benchmark estiver rodando em outro terminal e o
 *   resumo for regravado, o gráfico se atualiza sozinho.
 * Aba "Execução ao vivo": roda uma bateria de testes dentro da própria janela e desenha
 *   cada amostra no momento em que ela termina.
 *
 *   java -cp out ordenacao.grafico.VisualizadorGraficos [resultados/resumo.csv]
 *   java -cp out ordenacao.grafico.VisualizadorGraficos resultados/resumo.csv --png pasta   (gera PNGs sem abrir janela)
 */
public class VisualizadorGraficos extends JFrame {

    static final String G_TEMPO = "Tempo médio × Tamanho da entrada";
    static final String G_SPEEDUP = "Speedup × Número de threads";
    static final String G_EFIC = "Eficiência × Número de threads";
    static final String G_ALGS = "Comparação entre algoritmos";
    static final String G_TIPOS = "Comparação entre tipos de entrada";
    static final String[] GRAFICOS = { G_TEMPO, G_SPEEDUP, G_EFIC, G_ALGS, G_TIPOS };

    private Path arquivo;
    private long ultimaModificacao = -1;
    private List<Map<String, String>> linhas = new ArrayList<>();

    private final PainelGrafico grafico = new PainelGrafico();
    private final JComboBox<String> cbGrafico = new JComboBox<>(GRAFICOS);
    private final JComboBox<String> cbAlgoritmo = new JComboBox<>();
    private final JComboBox<String> cbTipo = new JComboBox<>();
    private final JComboBox<String> cbConfig = new JComboBox<>();
    private final JCheckBox ckLog = new JCheckBox("Escala log no eixo Y", true);
    private final DefaultTableModel tabela = new DefaultTableModel();
    private final JLabel status = new JLabel(" ");
    private boolean atualizandoCombos = false;

    public VisualizadorGraficos(Path arquivo) {
        super("Ordenação serial × paralela — gráficos dinâmicos");
        this.arquivo = arquivo;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Resultados (CSV)", criarAbaResultados());
        abas.addTab("Execução ao vivo", new AbaAoVivo());
        add(abas);

        setSize(1150, 820);
        setLocationRelativeTo(null);

        recarregar(true);
        // monitora o arquivo a cada 2 s: atualiza os gráficos se o CSV mudar
        new Timer(2000, e -> recarregar(false)).start();
    }

    // ------------------------------------------------------------------ aba de resultados

    private JPanel criarAbaResultados() {
        JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        topo.add(new JLabel("Gráfico:"));
        topo.add(cbGrafico);
        topo.add(new JLabel("Algoritmo:"));
        topo.add(cbAlgoritmo);
        topo.add(new JLabel("Entrada:"));
        topo.add(cbTipo);
        topo.add(new JLabel("Configuração:"));
        topo.add(cbConfig);
        topo.add(ckLog);

        JButton abrir = new JButton("Abrir CSV…");
        abrir.addActionListener(e -> escolherArquivo());
        JButton png = new JButton("Salvar PNG…");
        png.addActionListener(e -> salvarPng());
        topo.add(abrir);
        topo.add(png);

        cbGrafico.addActionListener(e -> atualizarGrafico());
        cbAlgoritmo.addActionListener(e -> atualizarGrafico());
        cbTipo.addActionListener(e -> atualizarGrafico());
        cbConfig.addActionListener(e -> atualizarGrafico());
        ckLog.addActionListener(e -> atualizarGrafico());

        JTable jt = new JTable(tabela);
        jt.setAutoCreateRowSorter(true);
        jt.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, grafico, new JScrollPane(jt));
        split.setResizeWeight(0.72);

        JPanel p = new JPanel(new BorderLayout());
        p.add(topo, BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        p.add(status, BorderLayout.SOUTH);
        return p;
    }

    private void escolherArquivo() {
        JFileChooser fc = new JFileChooser(arquivo.toAbsolutePath().getParent().toFile());
        fc.setFileFilter(new FileNameExtensionFilter("CSV de resumo", "csv"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            arquivo = fc.getSelectedFile().toPath();
            ultimaModificacao = -1;
            recarregar(true);
        }
    }

    private void salvarPng() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("grafico.png"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                grafico.exportarPng(fc.getSelectedFile(), 1200, 700);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void recarregar(boolean forcar) {
        try {
            if (!Files.exists(arquivo)) {
                status.setText("Arquivo não encontrado: " + arquivo.toAbsolutePath() + " — rode o benchmark ou use \"Abrir CSV…\"");
                return;
            }
            long mod = Files.getLastModifiedTime(arquivo).toMillis();
            if (!forcar && mod == ultimaModificacao) return;
            ultimaModificacao = mod;
            linhas = Estatistica.lerCsv(arquivo);
            preencherCombos();
            atualizarGrafico();
            status.setText(linhas.size() + " configurações carregadas de " + arquivo.toAbsolutePath()
                    + "   (atualização automática ativa)");
        } catch (IOException | RuntimeException ex) {
            status.setText("Erro lendo CSV: " + ex.getMessage());
        }
    }

    private void preencherCombos() {
        atualizandoCombos = true;
        repor(cbAlgoritmo, valores("algoritmo"));
        repor(cbTipo, valores("tipo_entrada"));
        List<String> configs = new ArrayList<>();
        TreeSet<Integer> ts = new TreeSet<>();
        for (String t : valores("threads")) ts.add(Integer.parseInt(t));
        for (int t : ts) configs.add(t == 1 ? "Serial" : t + " threads");
        repor(cbConfig, configs);
        atualizandoCombos = false;
    }

    private List<String> valores(String coluna) {
        List<String> v = new ArrayList<>();
        for (Map<String, String> l : linhas) if (!v.contains(l.get(coluna))) v.add(l.get(coluna));
        return v;
    }

    private static void repor(JComboBox<String> cb, List<String> itens) {
        Object sel = cb.getSelectedItem();
        cb.removeAllItems();
        for (String i : itens) cb.addItem(i);
        if (sel != null && itens.contains(sel)) cb.setSelectedItem(sel);
    }

    private void atualizarGrafico() {
        if (atualizandoCombos) return;
        String tipoGrafico = (String) cbGrafico.getSelectedItem();
        String alg = (String) cbAlgoritmo.getSelectedItem();
        String tipo = (String) cbTipo.getSelectedItem();
        String cfg = (String) cbConfig.getSelectedItem();
        int threadsCfg = cfg == null ? 1 : (cfg.equals("Serial") ? 1 : Integer.parseInt(cfg.split(" ")[0]));

        cbAlgoritmo.setEnabled(!G_ALGS.equals(tipoGrafico));
        cbTipo.setEnabled(!G_TIPOS.equals(tipoGrafico));
        cbConfig.setEnabled(G_ALGS.equals(tipoGrafico) || G_TIPOS.equals(tipoGrafico));

        montar(grafico, linhas, tipoGrafico, alg, tipo, threadsCfg, ckLog.isSelected());
        grafico.repaint();
        preencherTabela(alg, tipo, tipoGrafico);
    }

    /** Monta as séries do gráfico a partir das linhas do resumo (também usado no modo --png). */
    static void montar(PainelGrafico g, List<Map<String, String>> linhas, String tipoGrafico,
                       String alg, String tipo, int threadsCfg, boolean logY) {
        g.limpar();
        g.setMarcasX(null);
        Predicate<Map<String, String>> naoControle = l -> !"blocos_seq".equals(l.get("versao"));

        if (G_TEMPO.equals(tipoGrafico)) {
            g.setTitulo(alg + " — tempo médio (" + tipo + ")");
            g.setRotulos("Tamanho da entrada (n)", "Tempo médio (ms)");
            g.setLog(true, logY);
            Map<Integer, PainelGrafico.Serie> porThreads = new TreeMap<>();
            for (Map<String, String> l : linhas) {
                if (!l.get("algoritmo").equals(alg) || !l.get("tipo_entrada").equals(tipo) || !naoControle.test(l)) continue;
                int p = Integer.parseInt(l.get("threads"));
                porThreads.computeIfAbsent(p, x -> new PainelGrafico.Serie(x == 1 ? "Serial" : x + " threads",
                        PainelGrafico.PALETA[porThreads.size() % PainelGrafico.PALETA.length]))
                        .add(num(l, "tamanho"), num(l, "media_ms"));
            }
            porThreads.values().forEach(s -> { ordenar(s); g.adicionar(s); });
            g.setMarcasX(marcas(linhas, l -> l.get("algoritmo").equals(alg), "tamanho"));

        } else if (G_SPEEDUP.equals(tipoGrafico) || G_EFIC.equals(tipoGrafico)) {
            boolean sp = G_SPEEDUP.equals(tipoGrafico);
            String col = sp ? "speedup" : "eficiencia";
            g.setTitulo(alg + " — " + (sp ? "speedup" : "eficiência") + " (" + tipo + ")");
            g.setRotulos("Número de threads (p)", sp ? "Speedup S(p) = Ts / Tp" : "Eficiência E(p) = S(p) / p");
            g.setLog(false, false);
            Map<Integer, PainelGrafico.Serie> porTamanho = new TreeMap<>();
            TreeSet<Integer> ps = new TreeSet<>();
            for (Map<String, String> l : linhas) {
                if (!l.get("algoritmo").equals(alg) || !l.get("tipo_entrada").equals(tipo) || !naoControle.test(l)) continue;
                int n = Integer.parseInt(l.get("tamanho"));
                int p = Integer.parseInt(l.get("threads"));
                ps.add(p);
                porTamanho.computeIfAbsent(n, x -> new PainelGrafico.Serie("n = " + PainelGrafico.formatar(x),
                        PainelGrafico.PALETA[porTamanho.size() % PainelGrafico.PALETA.length]))
                        .add(p, num(l, col));
            }
            if (!ps.isEmpty()) {
                PainelGrafico.Serie ideal = new PainelGrafico.Serie(sp ? "Ideal (S = p)" : "Ideal (E = 1)", Color.GRAY);
                ideal.tracejada = true;
                ideal.marcadores = false;
                for (int p : ps) ideal.add(p, sp ? p : 1);
                g.adicionar(ideal);
            }
            porTamanho.values().forEach(s -> { ordenar(s); g.adicionar(s); });
            g.setMarcasX(ps.stream().mapToDouble(Integer::doubleValue).toArray());

        } else if (G_ALGS.equals(tipoGrafico)) {
            g.setTitulo("Comparação entre algoritmos — " + rotuloCfg(threadsCfg) + " (" + tipo + ")");
            g.setRotulos("Tamanho da entrada (n)", "Tempo médio (ms)");
            g.setLog(true, logY);
            Map<String, PainelGrafico.Serie> porAlg = new LinkedHashMap<>();
            for (Map<String, String> l : linhas) {
                if (!l.get("tipo_entrada").equals(tipo) || Integer.parseInt(l.get("threads")) != threadsCfg
                        || !naoControle.test(l)) continue;
                porAlg.computeIfAbsent(l.get("algoritmo"), x -> new PainelGrafico.Serie(x,
                        PainelGrafico.PALETA[porAlg.size() % PainelGrafico.PALETA.length]))
                        .add(num(l, "tamanho"), num(l, "media_ms"));
            }
            porAlg.values().forEach(s -> { ordenar(s); g.adicionar(s); });

        } else if (G_TIPOS.equals(tipoGrafico)) {
            g.setTitulo(alg + " — efeito do tipo de entrada — " + rotuloCfg(threadsCfg));
            g.setRotulos("Tamanho da entrada (n)", "Tempo médio (ms)");
            g.setLog(true, logY);
            Map<String, PainelGrafico.Serie> porTipo = new LinkedHashMap<>();
            for (Map<String, String> l : linhas) {
                if (!l.get("algoritmo").equals(alg) || Integer.parseInt(l.get("threads")) != threadsCfg
                        || !naoControle.test(l)) continue;
                porTipo.computeIfAbsent(l.get("tipo_entrada"), x -> new PainelGrafico.Serie(x,
                        PainelGrafico.PALETA[porTipo.size() % PainelGrafico.PALETA.length]))
                        .add(num(l, "tamanho"), num(l, "media_ms"));
            }
            porTipo.values().forEach(s -> { ordenar(s); g.adicionar(s); });
            g.setMarcasX(marcas(linhas, l -> l.get("algoritmo").equals(alg), "tamanho"));
        }
    }

    private static String rotuloCfg(int p) {
        return p == 1 ? "serial" : p + " threads";
    }

    private static double[] marcas(List<Map<String, String>> linhas, Predicate<Map<String, String>> filtro, String col) {
        TreeSet<Double> s = new TreeSet<>();
        for (Map<String, String> l : linhas) if (filtro.test(l)) s.add(num(l, col));
        return s.stream().mapToDouble(Double::doubleValue).toArray();
    }

    private static void ordenar(PainelGrafico.Serie s) {
        s.pontos.sort(Comparator.comparingDouble(p -> p[0]));
    }

    private static double num(Map<String, String> l, String col) {
        String v = l.get(col);
        return v == null || v.isEmpty() ? Double.NaN : Double.parseDouble(v);
    }

    private void preencherTabela(String alg, String tipo, String tipoGrafico) {
        String[] cols = { "algoritmo", "versao", "threads", "tamanho", "tipo_entrada", "media_ms", "desvio_ms",
                          "cv_pct", "ic95_inf_ms", "ic95_sup_ms", "speedup", "eficiencia" };
        tabela.setRowCount(0);
        tabela.setColumnIdentifiers(cols);
        for (Map<String, String> l : linhas) {
            boolean ok = (G_ALGS.equals(tipoGrafico) || l.get("algoritmo").equals(alg))
                      && (G_TIPOS.equals(tipoGrafico) || l.get("tipo_entrada").equals(tipo));
            if (!ok) continue;
            Object[] r = new Object[cols.length];
            for (int i = 0; i < cols.length; i++) r[i] = l.get(cols[i]);
            tabela.addRow(r);
        }
    }

    // ------------------------------------------------------------------ aba ao vivo

    /** Executa testes dentro da janela e desenha cada amostra assim que ela termina. */
    private static final class AbaAoVivo extends JPanel {
        private final PainelGrafico g = new PainelGrafico();
        private final JComboBox<String> cbAlg = new JComboBox<>(new String[] { "quick", "merge", "counting", "bubble" });
        private final JComboBox<String> cbTipo = new JComboBox<>();
        private final JComboBox<String> cbN = new JComboBox<>(new String[] { "1000000", "5000000", "10000000", "100000", "20000" });
        private final JTextField tfThreads = new JTextField("2,4,8", 8);
        private final JSpinner spAmostras = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        private final JButton btExec = new JButton("Executar");
        private final JButton btParar = new JButton("Parar");
        private final JLabel lb = new JLabel("Escolha os parâmetros e clique em Executar.");
        private SwingWorker<Void, double[]> worker;

        AbaAoVivo() {
            super(new BorderLayout());
            for (GeradorDados.Tipo t : GeradorDados.Tipo.values()) cbTipo.addItem(t.id);
            cbN.setEditable(true);
            JPanel topo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
            topo.add(new JLabel("Algoritmo:")); topo.add(cbAlg);
            topo.add(new JLabel("Entrada:"));   topo.add(cbTipo);
            topo.add(new JLabel("n:"));         topo.add(cbN);
            topo.add(new JLabel("Threads:"));   topo.add(tfThreads);
            topo.add(new JLabel("Amostras:"));  topo.add(spAmostras);
            topo.add(btExec); topo.add(btParar);
            btParar.setEnabled(false);
            add(topo, BorderLayout.NORTH);
            add(g, BorderLayout.CENTER);
            lb.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            add(lb, BorderLayout.SOUTH);
            g.setMensagemVazia("Os pontos aparecem aqui à medida que cada execução termina.");
            btExec.addActionListener(e -> iniciar());
            btParar.addActionListener(e -> { if (worker != null) worker.cancel(false); });
        }

        private void iniciar() {
            final Ordenador alg = Ordenador.porNome((String) cbAlg.getSelectedItem());
            final GeradorDados.Tipo tipo = GeradorDados.Tipo.porId((String) cbTipo.getSelectedItem());
            final int n;
            final int[] cfgs;
            try {
                n = Integer.parseInt(String.valueOf(cbN.getSelectedItem()).trim());
                int[] ts = Arrays.stream(tfThreads.getText().split(",")).map(String::trim)
                        .filter(s -> !s.isEmpty()).mapToInt(Integer::parseInt).filter(x -> x > 1).toArray();
                cfgs = new int[ts.length + 1];
                cfgs[0] = 1;
                System.arraycopy(ts, 0, cfgs, 1, ts.length);
            } catch (NumberFormatException ex) {
                lb.setText("Parâmetro inválido: " + ex.getMessage());
                return;
            }
            final int amostras = (Integer) spAmostras.getValue();

            g.limpar();
            PainelGrafico.Serie pts = new PainelGrafico.Serie("Amostras individuais", new Color(0x9DB8DC));
            pts.linha = false;
            PainelGrafico.Serie med = new PainelGrafico.Serie("Média por configuração", PainelGrafico.PALETA[0]);
            g.adicionar(pts);
            g.adicionar(med);
            g.setTitulo(alg.nome() + " ao vivo — n = " + PainelGrafico.formatar(n) + " (" + tipo.id + ")");
            g.setRotulos("Número de threads (1 = serial)", "Tempo (ms)");
            g.setLog(false, false);
            g.setMarcasX(Arrays.stream(cfgs).asDoubleStream().toArray());
            g.repaint();
            btExec.setEnabled(false);
            btParar.setEnabled(true);

            worker = new SwingWorker<Void, double[]>() {
                final Map<Integer, double[]> somas = new LinkedHashMap<>(); // p -> {soma, cont}

                @Override
                protected Void doInBackground() {
                    int[] base = GeradorDados.gerar(tipo, n);
                    for (int p : cfgs) {
                        alg.ordenar(base.clone(), p); // aquecimento
                        for (int a = 1; a <= amostras && !isCancelled(); a++) {
                            int[] v = base.clone();
                            long t0 = System.nanoTime();
                            alg.ordenar(v, p);
                            double ms = (System.nanoTime() - t0) / 1e6;
                            publish(new double[] { p, ms, a });
                        }
                        if (isCancelled()) break;
                    }
                    return null;
                }

                @Override
                protected void process(List<double[]> lote) {
                    for (double[] r : lote) {
                        int p = (int) r[0];
                        pts.add(p, r[1]);
                        double[] s = somas.computeIfAbsent(p, x -> new double[2]);
                        s[0] += r[1];
                        s[1]++;
                        synchronized (g) {
                            med.pontos.clear();
                            for (Map.Entry<Integer, double[]> e : somas.entrySet()) {
                                med.add(e.getKey(), e.getValue()[0] / e.getValue()[1]);
                            }
                        }
                        double[] serial = somas.get(1);
                        String sp = (serial != null && p != 1)
                                ? String.format(Locale.US, " | speedup parcial: %.2f",
                                    (serial[0] / serial[1]) / (s[0] / s[1])) : "";
                        lb.setText(String.format(Locale.US, "p = %d, amostra %d: %.2f ms%s",
                                p, (int) r[2], r[1], sp));
                    }
                    g.repaint();
                }

                @Override
                protected void done() {
                    btExec.setEnabled(true);
                    btParar.setEnabled(false);
                    if (isCancelled()) lb.setText("Execução interrompida.");
                    else lb.setText(lb.getText() + "   — concluído.");
                }
            };
            worker.execute();
        }
    }

    // ------------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        Path csv = Paths.get(args.length > 0 && !args[0].startsWith("--") ? args[0] : "resultados/resumo.csv");
        int iPng = Arrays.asList(args).indexOf("--png");
        if (iPng >= 0) {
            exportarTodos(csv, Paths.get(args.length > iPng + 1 ? args[iPng + 1] : "graficos_swing"));
            return;
        }
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorado) { }
        Runtime.getRuntime().addShutdownHook(new Thread(Paralelo::fecharPools));
        SwingUtilities.invokeLater(() -> new VisualizadorGraficos(csv).setVisible(true));
    }

    /** Modo sem janela: gera um PNG por algoritmo para os gráficos principais. */
    static void exportarTodos(Path csv, Path pasta) throws IOException {
        Files.createDirectories(pasta);
        List<Map<String, String>> linhas = Estatistica.lerCsv(csv);
        List<String> algs = new ArrayList<>(), tipos = new ArrayList<>();
        for (Map<String, String> l : linhas) {
            if (!algs.contains(l.get("algoritmo"))) algs.add(l.get("algoritmo"));
            if (!tipos.contains(l.get("tipo_entrada"))) tipos.add(l.get("tipo_entrada"));
        }
        PainelGrafico g = new PainelGrafico();
        String tipo = tipos.contains("aleatorio") ? "aleatorio" : tipos.get(0);
        for (String a : algs) {
            montar(g, linhas, G_TEMPO, a, tipo, 1, true);
            g.exportarPng(pasta.resolve(a + "_tempo.png").toFile(), 1100, 620);
            montar(g, linhas, G_SPEEDUP, a, tipo, 1, false);
            g.exportarPng(pasta.resolve(a + "_speedup.png").toFile(), 1100, 620);
        }
        montar(g, linhas, G_ALGS, null, tipo, 1, true);
        g.exportarPng(pasta.resolve("comparacao_serial.png").toFile(), 1100, 620);
        System.out.println("PNGs gravados em " + pasta.toAbsolutePath());
    }
}
