package ordenacao.grafico;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 * Componente Swing que desenha gráficos de linha/dispersão com Java2D puro
 * (sem bibliotecas externas). Suporta escala logarítmica nos dois eixos,
 * marcas do eixo X explícitas, legenda e exportação para PNG.
 * As séries podem ser alteradas a qualquer momento: basta chamar repaint().
 */
public class PainelGrafico extends JPanel {

    /** Uma série de dados do gráfico. */
    public static class Serie {
        public final String nome;
        public final Color cor;
        public final List<double[]> pontos = new ArrayList<>();
        public boolean linha = true;
        public boolean tracejada = false;
        public boolean marcadores = true;

        public Serie(String nome, Color cor) {
            this.nome = nome;
            this.cor = cor;
        }

        public Serie add(double x, double y) {
            pontos.add(new double[] { x, y });
            return this;
        }
    }

    /** Paleta categórica (distinguível também em tons de cinza pela ordem/marcadores). */
    public static final Color[] PALETA = {
        new Color(0x2A78D6), new Color(0xEB6834), new Color(0x1BAF7A), new Color(0xEDA100),
        new Color(0xE87BA4), new Color(0x008300), new Color(0x4A3AA7), new Color(0xE34948)
    };

    private final List<Serie> series = new ArrayList<>();
    private String titulo = "";
    private String rotuloX = "";
    private String rotuloY = "";
    private boolean logX, logY;
    private double[] marcasX;      // marcas explícitas no eixo X (opcional)
    private String mensagemVazia = "Sem dados para os filtros selecionados";

    public PainelGrafico() {
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(900, 520));
    }

    public synchronized void limpar() { series.clear(); }
    public synchronized void adicionar(Serie s) { series.add(s); }
    public synchronized List<Serie> getSeries() { return series; }
    public void setTitulo(String t) { titulo = t; }
    public void setRotulos(String x, String y) { rotuloX = x; rotuloY = y; }
    public void setLog(boolean x, boolean y) { logX = x; logY = y; }
    public void setMarcasX(double[] m) { marcasX = m; }
    public void setMensagemVazia(String m) { mensagemVazia = m; }

    @Override
    protected synchronized void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        desenhar(g, getWidth(), getHeight());
        g.dispose();
    }

    private void desenhar(Graphics2D g, int w, int h) {
        Font fonte = new Font("SansSerif", Font.PLAIN, 12);
        Font fonteTitulo = new Font("SansSerif", Font.BOLD, 15);
        g.setFont(fonte);
        FontMetrics fm = g.getFontMetrics();

        int esq = 78, dir = 24, topo = 44, base = 58;
        int pw = w - esq - dir, ph = h - topo - base;

        g.setColor(new Color(0x222222));
        g.setFont(fonteTitulo);
        g.drawString(titulo, esq, 26);
        g.setFont(fonte);

        // limites dos dados
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean temDados = false;
        for (Serie s : series) {
            for (double[] p : s.pontos) {
                if (Double.isNaN(p[1]) || (logX && p[0] <= 0) || (logY && p[1] <= 0)) continue;
                minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
                minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
                temDados = true;
            }
        }
        if (!temDados || pw < 50 || ph < 50) {
            g.setColor(Color.GRAY);
            g.drawString(mensagemVazia, esq + 10, topo + ph / 2);
            return;
        }
        if (marcasX != null) {
            for (double m : marcasX) { minX = Math.min(minX, m); maxX = Math.max(maxX, m); }
        }

        // eixo Y
        double[] limY;
        List<Double> ticksY;
        if (logY) {
            double lo = Math.pow(10, Math.floor(Math.log10(minY)));
            double hi = Math.pow(10, Math.ceil(Math.log10(maxY)));
            if (hi <= lo) hi = lo * 10;
            limY = new double[] { lo, hi };
            ticksY = new ArrayList<>();
            for (double t = lo; t <= hi * 1.0001; t *= 10) ticksY.add(t);
        } else {
            double lo = Math.min(0, minY);
            double passo = passoBonito((maxY - lo) / 6);
            double hi = Math.ceil(maxY * 1.05 / passo) * passo;
            if (hi <= lo) hi = lo + 1;
            limY = new double[] { lo, hi };
            ticksY = new ArrayList<>();
            for (double t = lo; t <= hi + passo / 2; t += passo) ticksY.add(t);
        }

        // eixo X
        double[] limX;
        List<Double> ticksX = new ArrayList<>();
        if (logX) {
            double lo = Math.pow(10, Math.floor(Math.log10(minX)));
            double hi = Math.pow(10, Math.ceil(Math.log10(maxX)));
            if (hi <= lo) hi = lo * 10;
            limX = new double[] { lo, hi };
            if (marcasX != null) {
                for (double m : marcasX) ticksX.add(m);
            } else {
                for (double t = lo; t <= hi * 1.0001; t *= 10) ticksX.add(t);
            }
        } else {
            double margem = (maxX - minX) * 0.05;
            if (margem == 0) margem = 1;
            limX = new double[] { minX - margem, maxX + margem };
            if (marcasX != null) {
                for (double m : marcasX) ticksX.add(m);
            } else {
                double passo = passoBonito((maxX - minX) / 8);
                for (double t = Math.ceil(limX[0] / passo) * passo; t <= limX[1]; t += passo) ticksX.add(t);
            }
        }

        // grade e rótulos
        Stroke normal = g.getStroke();
        for (double t : ticksY) {
            int y = py(t, limY, topo, ph);
            g.setColor(new Color(0xE6E6E6));
            g.drawLine(esq, y, esq + pw, y);
            g.setColor(new Color(0x555555));
            String s = formatar(t);
            g.drawString(s, esq - 8 - fm.stringWidth(s), y + fm.getAscent() / 2 - 1);
        }
        for (double t : ticksX) {
            int x = px(t, limX, esq, pw);
            g.setColor(new Color(0xEFEFEF));
            g.drawLine(x, topo, x, topo + ph);
            g.setColor(new Color(0x555555));
            String s = formatar(t);
            g.drawString(s, x - fm.stringWidth(s) / 2, topo + ph + 18);
        }
        g.setColor(new Color(0x888888));
        g.drawLine(esq, topo + ph, esq + pw, topo + ph);
        g.drawLine(esq, topo, esq, topo + ph);

        g.setColor(new Color(0x333333));
        g.drawString(rotuloX, esq + pw / 2 - fm.stringWidth(rotuloX) / 2, h - 16);
        Graphics2D gr = (Graphics2D) g.create();
        gr.rotate(-Math.PI / 2);
        gr.drawString(rotuloY, -(topo + ph / 2) - fm.stringWidth(rotuloY) / 2, 18);
        gr.dispose();

        // séries
        java.awt.Shape clipAntigo = g.getClip();
        g.clipRect(esq - 6, topo - 6, pw + 12, ph + 12);
        int idx = 0;
        for (Serie s : series) {
            g.setColor(s.cor);
            g.setStroke(s.tracejada
                    ? new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] { 6f, 5f }, 0f)
                    : new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] ant = null;
            for (double[] p : s.pontos) {
                if (Double.isNaN(p[1]) || (logX && p[0] <= 0) || (logY && p[1] <= 0)) { ant = null; continue; }
                int x = px(p[0], limX, esq, pw), y = py(p[1], limY, topo, ph);
                if (s.linha && ant != null) g.drawLine(ant[0], ant[1], x, y);
                ant = new int[] { x, y };
            }
            g.setStroke(normal);
            if (s.marcadores) {
                for (double[] p : s.pontos) {
                    if (Double.isNaN(p[1]) || (logX && p[0] <= 0) || (logY && p[1] <= 0)) continue;
                    int x = px(p[0], limX, esq, pw), y = py(p[1], limY, topo, ph);
                    marcador(g, idx, x, y, s.linha ? 7 : 5);
                }
            }
            idx++;
        }
        g.setClip(clipAntigo);

        // legenda
        int lx = esq + 12, ly = topo + 8, largura = 0;
        for (Serie s : series) largura = Math.max(largura, fm.stringWidth(s.nome));
        if (!series.isEmpty()) {
            int alt = series.size() * 18 + 8;
            g.setColor(new Color(255, 255, 255, 225));
            g.fillRoundRect(lx - 6, ly - 4, largura + 46, alt, 8, 8);
            g.setColor(new Color(0xCCCCCC));
            g.drawRoundRect(lx - 6, ly - 4, largura + 46, alt, 8, 8);
            idx = 0;
            for (Serie s : series) {
                int yy = ly + 10 + idx * 18;
                g.setColor(s.cor);
                if (s.linha) {
                    g.setStroke(s.tracejada
                            ? new BasicStroke(1.6f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[] { 4f, 3f }, 0f)
                            : new BasicStroke(2.2f));
                    g.drawLine(lx, yy, lx + 24, yy);
                    g.setStroke(normal);
                }
                if (s.marcadores) marcador(g, idx, lx + 12, yy, 7);
                g.setColor(new Color(0x333333));
                g.drawString(s.nome, lx + 32, yy + 4);
                idx++;
            }
        }
    }

    /** Formas diferentes por série: círculo, quadrado, triângulo, losango... */
    private static void marcador(Graphics2D g, int i, int x, int y, int t) {
        int r = t / 2;
        switch (i % 4) {
            case 0: g.fillOval(x - r, y - r, t, t); break;
            case 1: g.fillRect(x - r, y - r, t, t); break;
            case 2: g.fillPolygon(new int[] { x, x - r - 1, x + r + 1 }, new int[] { y - r - 1, y + r, y + r }, 3); break;
            default: g.fillPolygon(new int[] { x, x - r - 1, x, x + r + 1 }, new int[] { y - r - 1, y, y + r + 1, y }, 4);
        }
    }

    private int px(double x, double[] lim, int esq, int pw) {
        double f = logX ? (Math.log10(x) - Math.log10(lim[0])) / (Math.log10(lim[1]) - Math.log10(lim[0]))
                        : (x - lim[0]) / (lim[1] - lim[0]);
        return esq + (int) Math.round(f * pw);
    }

    private int py(double y, double[] lim, int topo, int ph) {
        double f = logY ? (Math.log10(y) - Math.log10(lim[0])) / (Math.log10(lim[1]) - Math.log10(lim[0]))
                        : (y - lim[0]) / (lim[1] - lim[0]);
        return topo + ph - (int) Math.round(f * ph);
    }

    private static double passoBonito(double bruto) {
        if (bruto <= 0) return 1;
        double e = Math.pow(10, Math.floor(Math.log10(bruto)));
        double f = bruto / e;
        double n = f < 1.5 ? 1 : f < 3 ? 2 : f < 7 ? 5 : 10;
        return n * e;
    }

    /** 1500 -> "1.5k", 10000000 -> "10M", 0.25 -> "0.25". */
    static String formatar(double v) {
        double a = Math.abs(v);
        if (a >= 1e6 && a % 1e5 == 0) return limpar(v / 1e6) + "M";
        if (a >= 1e3 && a % 100 == 0) return limpar(v / 1e3) + "k";
        if (a >= 100 || a == Math.rint(a)) return String.format(Locale.US, "%.0f", v);
        if (a >= 1) return String.format(Locale.US, "%.1f", v);
        if (a >= 0.01) return String.format(Locale.US, "%.2f", v);
        return String.format(Locale.US, "%.3f", v);
    }

    private static String limpar(double v) {
        return v == Math.rint(v) ? String.format(Locale.US, "%.0f", v) : String.format(Locale.US, "%.1f", v);
    }

    /** Desenha o gráfico em uma imagem PNG (funciona inclusive sem monitor/headless). */
    public void exportarPng(File arquivo, int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        synchronized (this) {
            desenhar(g, w, h);
        }
        g.dispose();
        ImageIO.write(img, "png", arquivo);
    }
}
