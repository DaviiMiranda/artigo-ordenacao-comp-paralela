"""Gera as figuras do artigo a partir de resultados/resumo.csv (python 3 + pandas + matplotlib)."""
import sys, pathlib
import numpy as np, pandas as pd
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.colors import LinearSegmentedColormap, TwoSlopeNorm

raiz = pathlib.Path(__file__).resolve().parent.parent
d = pd.read_csv(raiz / "resultados" / "resumo.csv")
saida = raiz / "graficos"; saida.mkdir(exist_ok=True)

COR = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#4a3aa7", "#e34948"]
MARC = ["o", "s", "^", "D"]
ALGS = ["BubbleSort", "QuickSort", "MergeSort", "CountingSort"]
TXT, TXT2, GRADE = "#0b0b0b", "#52514e", "#e4e3df"
plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 10, "axes.edgecolor": "#9a9893",
    "axes.labelcolor": TXT, "xtick.color": TXT2, "ytick.color": TXT2, "axes.spines.top": False,
    "axes.spines.right": False, "axes.grid": True, "axes.axisbelow": True, "grid.color": GRADE, "grid.linewidth": 0.8,
    "legend.frameon": False, "savefig.dpi": 200, "figure.facecolor": "white"})

def fmt_n(n):
    return f"{n/1e6:g}M" if n >= 1e6 else f"{n/1e3:g}k"

# Figura 1 — tempo serial x n (log-log)
fig, ax = plt.subplots(figsize=(7.2, 4.0))
s = d[(d.versao == "serial") & (d.tipo_entrada == "aleatorio")]
for i, a in enumerate(ALGS):
    g = s[s.algoritmo == a].sort_values("tamanho")
    ax.plot(g.tamanho, g.media_ms, color=COR[i], marker=MARC[i], ms=6, lw=2, label=a)
ax.set_xscale("log"); ax.set_yscale("log")
ax.set_xlabel("Tamanho da entrada n (escala log)"); ax.set_ylabel("Tempo médio (ms, escala log)")
ax.set_xlim(800, 1.4e7)
ax.legend(loc="upper left")
fig.tight_layout(); fig.savefig(saida / "fig1_tempo_serial.png"); plt.close(fig)

# Figura 2 — speedup x threads (aleatório), um painel por algoritmo
tam = {"BubbleSort": [5000, 25000, 50000], "QuickSort": [100000, 1000000, 10000000],
       "MergeSort": [100000, 1000000, 10000000], "CountingSort": [100000, 1000000, 10000000]}
fig, axs = plt.subplots(2, 2, figsize=(7.4, 5.6))
for ax, a in zip(axs.flat, ALGS):
    g = d[(d.algoritmo == a) & (d.tipo_entrada == "aleatorio") & (d.versao != "blocos_seq")]
    ps = [1, 2, 4, 8]
    ax.plot(ps, ps, ls="--", color="#8d8b86", lw=1.2, label="Ideal (S = p)")
    ax.axhline(2, ls=":", color="#8d8b86", lw=1.2, label="Nº de núcleos (2)")
    ax.axhline(1, color="#b9b7b1", lw=0.8)
    for i, n in enumerate(tam[a]):
        h = g[g.tamanho == n].sort_values("threads")
        ax.plot(h.threads, h.speedup, color=COR[i], marker=MARC[i], ms=6, lw=2, label=f"n = {fmt_n(n)}")
    ax.set_title(a, fontsize=10.5, color=TXT, loc="left", fontweight="bold")
    ax.set_xticks(ps); ax.set_xlabel("Threads (p)"); ax.set_ylabel("Speedup")
    top = 22 if a == "BubbleSort" else 8.5
    ax.set_ylim(0, top)
    ax.legend(fontsize=7.5, loc="upper left")
fig.tight_layout(); fig.savefig(saida / "fig2_speedup.png"); plt.close(fig)

# Figura 3 — Bubble: ganho algorítmico x ganho do paralelismo (n = 50k)
b = d[(d.algoritmo == "BubbleSort") & (d.tamanho == 50000) & (d.tipo_entrada == "aleatorio")]
ps = [2, 4, 8]
alg = [b[(b.versao == "blocos_seq") & (b.threads == p)].speedup.iloc[0] for p in ps]
tot = [b[(b.versao == "paralelo") & (b.threads == p)].speedup.iloc[0] for p in ps]
fig, ax = plt.subplots(figsize=(7.0, 3.6))
x = np.arange(len(ps)); w = 0.36
r1 = ax.bar(x - w/2 - 0.01, alg, w, color=COR[0], label="Blocos, 1 thread (só ganho algorítmico)")
r2 = ax.bar(x + w/2 + 0.01, tot, w, color=COR[1], label="Blocos + threads (versão paralela)")
for r in list(r1) + list(r2):
    ax.annotate(f"{r.get_height():.1f}×", (r.get_x() + r.get_width()/2, r.get_height()),
                xytext=(0, 3), textcoords="offset points", ha="center", fontsize=9, color=TXT)
ax.set_xticks(x); ax.set_xticklabels([f"{p} blocos / {p} threads" for p in ps])
ax.set_ylabel("Speedup sobre o Bubble serial"); ax.set_ylim(0, 23)
ax.grid(axis="x", visible=False); ax.legend(loc="upper left")
fig.tight_layout(); fig.savefig(saida / "fig3_bubble_decomposicao.png"); plt.close(fig)

# Figura 4 — mapa de calor: speedup com 2 threads no maior n, por tipo de entrada
tipos = ["aleatorio", "ordenado", "reverso", "quase_ordenado", "poucos_valores"]
rot = ["Aleatório", "Ordenado", "Reverso", "Quase\nordenado", "Poucos\nvalores"]
M = np.zeros((4, 5))
for i, a in enumerate(ALGS):
    nmax = d[d.algoritmo == a].tamanho.max()
    for j, t in enumerate(tipos):
        M[i, j] = d[(d.algoritmo == a) & (d.tamanho == nmax) & (d.tipo_entrada == t) & (d.threads == 2)
                    & (d.versao == "paralelo")].speedup.iloc[0]
cmap = LinearSegmentedColormap.from_list("div", ["#c9302c", "#f0efec", "#2a78d6"])
fig, ax = plt.subplots(figsize=(7.2, 3.1))
im = ax.imshow(np.log2(M), cmap=cmap, norm=TwoSlopeNorm(0, -5, 5), aspect="auto")
for i in range(4):
    for j in range(5):
        ax.text(j, i, f"{M[i, j]:.2f}", ha="center", va="center", fontsize=9.5,
                color="white" if abs(np.log2(M[i, j])) > 3 else TXT)
ax.set_xticks(range(5)); ax.set_xticklabels(rot)
ax.set_yticks(range(4)); ax.set_yticklabels([f"{a} (n={fmt_n(d[d.algoritmo==a].tamanho.max())})" for a in ALGS])
ax.grid(False)
for sp in ax.spines.values(): sp.set_visible(False)
cb = fig.colorbar(im, ax=ax, ticks=[-5, -3, -1, 0, 1, 3, 5], fraction=0.04)
cb.ax.set_yticklabels(["1/32", "1/8", "1/2", "1", "2", "8", "32"]); cb.set_label("Speedup (p = 2)")
fig.tight_layout(); fig.savefig(saida / "fig4_tipos_entrada.png"); plt.close(fig)
print("Figuras gravadas em", saida)
