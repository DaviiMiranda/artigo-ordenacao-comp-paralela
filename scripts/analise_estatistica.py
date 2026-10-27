"""Análise estatística complementar (python 3 + pandas + scipy).
- Teste t de Welch (alfa = 0,05) entre as amostras seriais e paralelas de cada configuração.
- Expoente empírico de crescimento (inclinação log-log do tempo serial em função de n).
Saídas: resultados/teste_welch.csv e resultados/expoentes.csv
"""
import pathlib
import numpy as np, pandas as pd
from scipy import stats

raiz = pathlib.Path(__file__).resolve().parent.parent
b = pd.read_csv(raiz / "resultados" / "resultados_brutos.csv")
d = pd.read_csv(raiz / "resultados" / "resumo.csv")

linhas = []
for (a, n, t), g in b.groupby(["algoritmo", "tamanho", "tipo_entrada"]):
    ser = g[g.versao == "serial"].tempo_ms
    for p in sorted(g[g.versao == "paralelo"].threads.unique()):
        par = g[(g.versao == "paralelo") & (g.threads == p)].tempo_ms
        r = stats.ttest_ind(ser, par, equal_var=False)
        S = ser.mean() / par.mean()
        conclusao = ("paralelo mais rápido" if (r.pvalue < 0.05 and S > 1) else
                     "paralelo mais lento" if (r.pvalue < 0.05 and S < 1) else "sem diferença significativa")
        linhas.append(dict(algoritmo=a, tamanho=n, tipo_entrada=t, threads=p, speedup=round(S, 4),
                           t=round(r.statistic, 4), p_valor=r.pvalue, conclusao=conclusao))
w = pd.DataFrame(linhas)
w.to_csv(raiz / "resultados" / "teste_welch.csv", index=False)

exp = []
s = d[(d.versao == "serial") & (d.tipo_entrada == "aleatorio")]
for a, g in s.groupby("algoritmo"):
    g = g[g.tamanho >= (10000 if a == "BubbleSort" else 100000)]
    k, _ = np.polyfit(np.log10(g.tamanho), np.log10(g.media_ms), 1)
    exp.append(dict(algoritmo=a, faixa_n=f"{g.tamanho.min()}-{g.tamanho.max()}", expoente=round(k, 3)))
pd.DataFrame(exp).to_csv(raiz / "resultados" / "expoentes.csv", index=False)

print(pd.DataFrame(exp).to_string(index=False))
print()
print(w.groupby(["algoritmo", "conclusao"]).size().unstack(fill_value=0))
print()
print("Somente n >= 1M:")
print(w[w.tamanho >= 1_000_000].groupby(["algoritmo", "conclusao"]).size().unstack(fill_value=0))
