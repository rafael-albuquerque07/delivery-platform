# M3-3 — a fatia 3: a permissão de lançar pedido

- **Aplicada em:** 23/11/2026 · **sobre:** `1172fee` · **versão do pacote:** v2
- **Commits:** o de código (enum, `V9`, testes, contrato, front) e o de documentos, que traz esta pasta

A v1 não chegou a esta máquina: a v2 é a v1 com as buscas na forma da S-8.

## O que se mediu
- §0: os controles da S-8, da S-7 e da S-6 acharam; os 13 workflows verdes; o razão em 112 · 28 · 132
- §2: onze valores no enum; a `V8` era a última; `migration=1`; seis `CONFIGURAR_LOJA` no contrato
- **o banco local** (só contagens): 8 membros; 7 com as dez permissões antigas, 3 deles com a
  `CONFIGURAR_LOJA` — os mesmos 7 e 3 da emenda de 19/10. Dos 7, 6 são administrador e primeiro
  membro da loja (3 com a `CONFIGURAR_LOJA`), e 1 é colaborador com as dez; nenhum não ativo;
  nenhuma loja com empate no primeiro membro
- **a ADR-027, nos três consumidores de 17/10:** o `OuvinteDeVinculoAlterado` não lê `permissoes`;
  `contracts/events/` não tem esquema do `VinculoAlteradoV1`, só o envelope, sem o enum; o
  `PermissaoDoCatalogo.de` devolve vazio para o que não conhece
- nenhum IT usava o Flyway pela API: o `PermissaoDeLancarIT` é o primeiro (Flyway 12.4.0,
  `schemas`, `defaultSchema` e `target(String)` conferidos no jar)
- o contrato: `LANCAR_PEDIDO` nos seis `enum` da `CONFIGURAR_LOJA`, e nada mais; `merchant.ts`, as mesmas seis
- testes: `merchant` 511 → 516, verdes; front 215 → 216, os seis passos do `frontend.yml` verdes. O
  "antes" é o da V-1, sem mudança no `merchant` nem no front desde então

## O que mudou
- `Permissao.java`: `LANCAR_PEDIDO`; "mais três"; o javadoc da `CONFIGURAR_LOJA` diz *fundador*
- `V9__a_permissao_de_lancar_alcanca_os_fundadores.sql`; o `PermissaoDeLancarIT`
- `ConvidarParaEquipe.tsx` (o rótulo), `secoes.ts` (sem seção), um `it` no `ConvidarParaEquipe.test.tsx`
- ADR-011 e ADR-086 (status e emenda), o índice, `estabelecimento.md` §2, `marco-3.md` (4.5f como peça)
- o script de documentos rodou como veio; depois, pelas decisões abaixo, o critério da `V9`, a frase
  da rota e a medição da ADR-027 entraram por cima no texto que ele gravou

## Perguntas ao Rafael
| Pergunta | Resposta |
|---|---|
| "Tem as dez" não é "é fundador": colaborador e promovido podem ter as dez | a `V9` exige as três juntas: `ADMINISTRADOR`, o primeiro membro criado na loja, as dez; sem filtro de estado |
| A regra 15 acha também ADR-073:90, ADR-078:26, ADR-011:344 e a emenda nova da ADR-086 | ficam, com o motivo: são corpo ou emenda de ADR datada |
| A ADR-027 manda medir de novo a cada valor novo | medir os três; parar se o esquema fechar o enum ou o ouvinte recusar |
| O caso 3 copiava o `ConviteIT`; casos 4 e 5 dependiam da ordem; a rota no presente | as três correções: `ConvidarIT` (400), preparação única, "nenhuma rota a exige hoje" |
| M2 e M4 sobreviveram na primeira fixture | dois membros a mais no caso 5: o fundador que perdeu permissões e o colaborador com o mesmo `criado_em` do fundador |

## Previsões × resultado
| # | Caso | Resultado |
|---|---|---|
| 1 | o fundador de uma loja cadastrada tem `LANCAR_PEDIDO` | passou |
| 2 | convite com `LANCAR_PEDIDO`, aceito: o contexto traz só as do convite | passou |
| 3 | quem não a tem não a oferece: 400, *"você só pode oferecer permissões que você mesmo tem"* | passou |
| 4 | a `V9` dá ao fundador antigo `LANCAR_PEDIDO` e `CONFIGURAR_LOJA` | passou |
| 5 | a `V9` não dá nada a seis outros, dá só `LANCAR_PEDIDO` ao da E-4 e as duas ao do empate | passou |

| Mutação | Previsão | Saiu |
|---|---|---|
| M1 · sem `('LANCAR_PEDIDO')` | caem 4 e 5 | caíram 4 e 5 |
| M2 · `= 10` → `>= 3` | cai o 5 | na primeira fixture, nada; com o fundador que perdeu permissões, caiu o 5 |
| M3 · sem a condição do primeiro membro | cai o 5 | caiu o 5 |
| M4 · sem a condição do papel | cai o 5 | na primeira fixture, nada; com o colaborador do empate, caiu o 5 |

Cada mutação restaurada da cópia da `V9`, com `diff` vazio.

## Defeitos achados
- do pacote: o caso 3 apontava a recusa do aceite (409), e não a da emissão (400); casos 4 e 5 no
  mesmo esquema dependiam da ordem do JUnit; o critério da `V9` alcançava colaborador e promovido
  com as dez; a regra 15 prevista não contava a ADR-073, a ADR-078 e a emenda nova da ADR-086; a
  ADR-027 medida em um consumidor só; "caso 3" e "caso 1" não existem no teste do front — o 4.5f
  cita o `it` pelo título
- frases datadas que ficam: ADR-040:217 (*"dez permissões"*), `passagem-humana.md`:274–279 (a dívida
  de 19/10, paga aqui) e `metodo-das-rodadas.md`:119–120 — registros com data
- o `catalog` guarda a autorização por 60 s, e a `V9` não publica evento: no primeiro minuto depois do
  deploy ele pode responder o conjunto antigo. Nenhuma rota do `catalog` exige `LANCAR_PEDIDO` nem
  `CONFIGURAR_LOJA`, então nenhuma resposta muda
- no caminho: a ponte do Docker caiu com a VM viva — `wsl --shutdown`, sessão mantida, `./gradlew --stop`

## O revisor de diff
Nada impede o commit. Três achados, só para relatório: o corpo da fatia 3 da ADR-086 chama a nova
de acréscimo ao PRD, e ela nasce da H4.5 — a emenda não responde a essa frase; o 4.5f cita o `it` do
front pelo começo do título, não por ele inteiro; este relatório passa das sessenta linhas.

## O razão
| | antes | depois |
|---|---|---|
| recorte estreito | 112 | 112 |
| segunda passada | 28 | 28 |
| linhas numeradas | 132 | 132 |
