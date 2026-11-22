# As rodadas aplicadas

**Cada pacote aplicado fica aqui, no commit da própria rodada** (S-7, `aplicar-rodada.md` passo 12).
Uma pasta por rodada, com o nome dela — `M3-2/`, `S-7/` —, e dentro:

- **o pacote como foi aplicado** — o `APLICAR-*.md` da versão que rodou e os arquivos que vieram com
  ele (scripts, modelos, textos para copiar);
- **`RELATORIO.md`** — o relatório resumido, na forma abaixo.

**Rodada de medição** não tem commit: a pasta dela entra aqui no commit da rodada que vem logo
depois. **Versão que parou não se guarda:** o `RELATORIO.md` da versão aplicada diz que ela existiu,
até onde foi e por que parou.

Os pacotes citam os comandos do razão, as fórmulas dos gatilhos e as formas da segunda passada: é o
trabalho deles. **Por isso esta pasta fica fora das duas contas do razão** (`--exclude-dir=rodadas`
nos comandos do cabeçalho do `docs/dividas.md`, e o mesmo no `scripts/razao.mjs`).

**O que vale é o repositório.** Um pacote guardado aqui registra o que se pediu e o que se previu;
o que ficou valendo está nas ADRs, nos documentos e no código. Se um pacote e um documento
divergirem, o documento vence.

## A forma do `RELATORIO.md`

Curto — até umas sessenta linhas. Sem linha do `.env`, sem token, sem caminho com nome de usuário
(`aplicar-rodada.md`, "O relatório").

```markdown
# <RODADA> — <título do pacote>

- **Aplicada em:** <data do date> · **sobre:** `<commit de partida>` · **versão do pacote:** <v1, v2…>
- **Commits:** `<hash do commit de código, se houver>` e o commit que traz esta pasta

## O que se mediu
<as medições que mudaram alguma coisa, uma linha cada, com o número>

## O que mudou
<os arquivos, agrupados, uma linha cada>

## Perguntas ao Rafael
| Pergunta | Resposta |

## Previsões × resultado
| A previsão | O que saiu |

## Defeitos achados
<os do pacote — de quem escreveu — e os achados no caminho que foram para o relatório>

## O razão
| | antes | depois |
```
