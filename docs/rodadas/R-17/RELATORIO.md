# R-17 — o que falta para operar como SaaS, guardado com gatilho

- **Aplicada em:** 23/11/2026 · **sobre:** `4819195` · **versão do pacote:** v3
- **Commit:** um só — a proposta, o `docs/dividas.md` e esta pasta

A v2 parou antes de qualquer escrita: o revisor de pacote achou três frases da proposta falsas
contra o repositório (acesso e portabilidade *"sem procedimento"*, o cofre com o certificado A1
dentro, a área de entrega que *"se desativa"*). O Rafael pediu a v3, que as corrige.

`proposta/` guarda a proposta como chegou; `docs/propostas/` tem a versão com as três correções
abaixo. `razao-r17.mjs` é o script que escreveu as cinco linhas e a conciliação.

## O que se mediu
- §0: `main` em `4819195`; só a v3 em `r17/`; controle da S-8 `1`, da M3-2 `2`; `docs/propostas/`
  só com a proposta fiscal; os 13 workflows verdes no `main`
- §2: `deploy=1` (o controle acha dois workflows); `cofre=1` (o controle acha a ADR-013);
  `exclusao-de-titular.md` com *"esqueleto"*, prazo de 15 dias para todos os direitos, `1` para
  *"no sentido inverso"*, `0` títulos de acesso e `1` de exclusão; a R3 e a R5 da proposta fiscal,
  o marco 4 do `pagamento.md` e o *"substitui"* da ADR-081 onde o pacote diz; as linhas 118, 121 e
  124 a 127 do razão são os gatilhos da ADR-085; `inadimplencia=1`, controle `1`; o `restaura` acha
  o que a §3.2 diz. Nenhuma linha citada mudou de lugar

## Perguntas ao Rafael
| Achado do revisor na v3 | Resposta |
|---|---|
| a linha 84 do razão espera um **pedido** de titular, não o titular — o §4 e a 135 diziam *"o primeiro titular"* / *"o mesmo titular"* | corrigir aqui: *"o primeiro pedido de titular"* |
| o 3º gatilho disparava no primeiro cliente, e o `exclusao-de-titular.md`:103 manda escrever **antes** dele | corrigir aqui, com a redação dele, e a 135 a cita palavra por palavra |
| a §5.1 decifrava o segredo para *"a assinatura de um documento fiscal"*, contra a R3 | corrigir aqui: *"a chamada ao provedor fiscal"* |

O 3º gatilho ficou com a primeira frase em negrito, como os outros, e o nome do arquivo em código.
Na citação do razão, as aspas internas de *"A escrever antes do primeiro cliente"* perdem o itálico:
itálico dentro de itálico fecha a citação.

## O razão
| | antes | depois | diferença prevista |
|---|---|---|---|
| o recorte estreito | 112 | 113 | +1 |
| linhas vindas do recorte | 57 | 58 | +1 |
| segundos gatilhos numa seção | +43 | +47 | +4 |
| linhas que contam | 129 | 134 | +5 |
| linhas numeradas | 132 | 137 | +5 |
| a segunda passada | 28 | 28 | 0 |

Cabeçalho: a linha 3 ganha *"e na R-17, sobre `4819195` mais a proposta do que falta para operar
como SaaS"*; *"A conta, em 22/11/2026, na M3-2:"* passa a *"em 23/11/2026, na R-17:"*. A regra 15
(`grep -rn "o-que-falta-para-operar" docs --exclude-dir=rodadas`) acha as linhas 133 a 137 e nada mais.

## Só para relatório
- o controle do §0 procura a M3-2, e passaria também num `main` parado nela; o hash cobre
- a 134 dispara pelo mesmo evento da 118 e só alarga o que se decide: duas linhas, mesma condição
- a metade *"a primeira rodada que desenhar o 7a"* da 135 não tem medida própria na coluna
- a célula da 137 é amostra: o comando devolve mais linhas por arquivo (ADR-013:176, exclusão :59,
  :60 e :62, revisão :213), todas do banco inteiro
- o recorte estreito conta, entre os 113, três cópias em `frontend/.tsbuild/` que o razão já desconta
