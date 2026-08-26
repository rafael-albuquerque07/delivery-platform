# ADR-005 — PostGIS para áreas e Redis GEO para proximidade

**Status:** ⛔ **Sem objeto** — 25/08/2026
**Motivo:** o assunto saiu do MVP antes de a decisão ser escrita
**Removido por:** ADR-020 (taxa por área nomeada), ADR-021 (catálogo de serviços)

---

## Nunca escrita, não revogada

Vale a mesma distinção da ADR-003: esta decisão **não chegou a existir**. O
número foi reservado quando o assunto ainda estava de pé, e o assunto caiu antes
do texto. Não há versão anterior no histórico do Git — o que existe é este
registro.

## O que ela teria decidido

Duas tecnologias, para duas perguntas espaciais:

```
PostGIS       "este endereço está dentro da área de entrega?"
              polígono, coluna geométrica, índice GiST, geocodificação do
              endereço textual do cliente

Redis GEO     "qual entregador está mais perto deste ponto?"
              GEOADD a cada atualização de posição, GEOSEARCH no despacho
```

## Por que o assunto desapareceu

A **ADR-020** trocou a pergunta. A taxa de entrega passou a ser **por área
nomeada** — bairro ou faixa de CEP —, e a área não é deduzida:

> *Não depende de geocodificação. A área é **escolhida**, não deduzida* — no
> canal, o cliente seleciona o bairro numa lista das áreas ativas da loja.

Com isso, "este endereço está dentro do polígono?" deixa de ser uma consulta
espacial e vira **uma busca por chave**. Não há geometria, não há geocodificação,
não há índice espacial.

A segunda pergunta caiu junto. A **ADR-021** cortou o `geolocation-service`, e o
`entrega.md` §5 desenhou o rodízio sem proximidade nenhuma: o próximo é **quem
voltou há mais tempo**, com desempate por contagem de entregas e ordem de
abertura da jornada. Determinístico, e sem um metro de distância envolvido.

A **ADR-014** registra o rastro disso na infraestrutura: a imagem do Compose
voltou a ser `postgres:17-alpine`, e a emenda v1.1 dela diz, com todas as letras,
que o argumento original contra o H2 se apoiava justamente nos dois serviços que
saíram.

## O que sobreviveu

**A pergunta espacial virou uma busca por chave, e isso é a decisão.** Não é
ausência de decisão: é a ADR-020 tendo escolhido um modelo em que o problema não
aparece. Faixa de CEP sem sobreposição (invariante M10) faz o trabalho que o
polígono faria, com um índice comum.

**O Redis fica, para cache.** Cardápio público em `catalogo.md` §7, com TTL
curto. O que não existe é `GEOADD`.

## O que foi descartado

- A extensão PostGIS e a imagem correspondente do Postgres no Compose
- Coluna geométrica, índice GiST, e as migrações que os criariam
- Geocodificação do endereço textual do cliente
- `GEOADD` a cada posição e `GEOSEARCH` no despacho
- A dependência de um provedor de geocodificação — com contrato, custo por
  chamada e transferência internacional de dado

## O custo que voltar teria, e que não é o óbvio

Reintroduzir geoprocessamento não é acrescentar uma extensão e uma biblioteca.
Esbarra numa decisão de **proteção de dados**, tomada depois e por outro motivo:

> **ADR-013 §4** — coordenada exata **nunca** é gravada nem registrada em log.
> Guarda-se endereço textual e bairro.

O `entrega.md` guarda `situacao` do entregador — `NO_ESTABELECIMENTO`,
`EM_ROTA` — e **não** a posição dele. Então o dado que um `GEOSEARCH`
consumiria não existe em lugar nenhum, por escolha.

As duas decisões se reforçam sem terem sido tomadas juntas, e é isso que faz o
caminho de volta ser mais caro do que parece: quem trouxer geo de volta terá de
reabrir a ADR-013, não só instalar o PostGIS.

## Se o assunto voltar

Dois gatilhos, e são diferentes:

| Gatilho | O que muda |
|---|---|
| A premissa **P5** cair — taxa passar a ser por distância | Volta a cotação por distância, e a ADR-019 revogada é o ponto de partida |
| O **marco 11** acontecer — rastreamento em mapa | Volta a posição do entregador, e com ela a ADR-013 §4 |

Nos dois casos, **ADR nova com número novo**. Este número é endereço histórico,
não vaga.
