# A resposta do auditor de marco, na §5 da A-1

Chamado nesta sessão, depois do commit de código e das tabelas escritas, com o pedido *"Audite o
marco 2. Não mude nenhum arquivo."*. Transcrita palavra por palavra; os caminhos absolutos que ele
devolveu na lista de arquivos foram encurtados para relativos à raiz do repositório.

---

## Auditoria do marco 2: a tabela confere com o repositório

**Resultado:** nenhuma divergência impede declarar. Também não há o que declarar: a própria tabela diz *"O marco não fecha"*, e a minha leitura chega à mesma conclusão. Nenhum critério que conta tem as quatro colunas cheias:

- 3.1c e 3.1d não foram exercidos;
- 3.2b não foi exercido, e o registro diz isso expressamente;
- 3.1a e 3.2a foram exercidos só em parte.

Encontrei duas divergências em que a tabela afirma menos do que o repositório. Elas só entram neste relatório.

Não sei se os testes passam: verde é do CI, e não suponho nada sobre isso.

---

### 1. A minha tabela da fase 1 (às cegas)

**As fontes.** O `docs/PRD.md:394` diz que o marco 2 entrega H3.1 e H3.2. Os critérios estão em `docs/PRD.md:152-162`. Nenhum critério da H3.1 ou da H3.2 tem *Verificável*: o `Grep` de "Verific" no PRD só acha linhas do E1 e do E4.

**Fora da conta, pela anotação do PRD:**
- Imagem do produto: *"Entra a partir do 7a"*, em `PRD.md:154`.
- Rascunho não aparece para o cliente: *"Entra a partir do 7a"*, em `PRD.md:157`.
- Esgotado não oferecido no canal nem no rascunho: *"Entra a partir do 7a"*, em `PRD.md:162`.

Nenhum critério do marco 2 espera gatilho do `docs/dividas.md`. As linhas que citam o `catalog` ali (35, 36, 37, 66, 69, 111 e outras) são gatilhos de ADR ou de peça, não de critério da H3.

**Os 5 critérios que contam:**

| # | Critério | Rota | Tela | Teste (o que a asserção afirma) | Exercida |
|---|---|---|---|---|---|
| 3.1a | Produto com preço-base e descrição | `ProdutoController.java:249` `@PostMapping` em `/api/v1/merchants/{estabelecimentoId}/catalog/produtos` (classe em `:70`); o gateway roteia em `gateway/application.yml:86` | `frontend/src/painel/NovoProduto.tsx` (campos "Preço" `:193`, "Descrição (opcional)" `:201`) | **sim.** `CriacaoDeProdutoIT.cria_rascunho` ("A1/A2/A6…", `:93-113`): o banco guarda `descricao = "350 ml"` e `precoBase = 6.00`. No front, `NovoProduto.test.tsx` "a descrição vai quando preenchida" (`:165-174`) e "C10" (`:97-103`, corpo com `precoBase: 6`) | **em parte.** Registro de 19/10/2026, "O que foi exercido" (`passagem-humana.md:187-188`): *"um produto publicado"*. Preço e descrição não estão escritos |
| 3.1c | Grupos com mín./máx.: tamanho obrigatório, adicionais opcionais, remoções | `ProdutoController.java:114` `@PostMapping /{produtoId}/grupos` | `ProdutoAberto.tsx` (`NovoGrupoNoProduto`, `:296-388`) | **sim.** `MontagemDeOpcoesIT.grupo_e_opcao_em_rascunho` (`:82-111`): o banco tem "Tamanho" com min 1 e max 1. `MontagemDeOpcoesIT.remocoes_com_acrescimo_zero` (`:119-134`): grupo com min 0 e max 2, opções a 0,00. Adicionais opcionais: `GrupoDeOpcoesTest.opcional_vazio` (`:107-114`, `obrigatorio()` falso) e `VendavelTest.grupo_opcional_esgotado_nao_impede` (`:100-108`). No front, `ProdutoAberto.test.tsx` "1" (`:121-142`, "Escolha 1" e "Opcional, até 2") e "M1" (`:328-345`). Nenhum teste cria um grupo de adicionais **pela rota**, mas a rota é a mesma do tamanho e da remoção | — |
| 3.1d | Cada opção com seu acréscimo | `ProdutoController.java:148` `@PostMapping /{produtoId}/grupos/{grupoId}/opcoes` | `ProdutoAberto.tsx` (`NovaOpcaoNoGrupo`, `:391-469`; o acréscimo é exibido em `:519-523`) | **sim.** `MontagemDeOpcoesIT.grupo_e_opcao_em_rascunho` (`:107`): o acréscimo 16,00 é lido do banco. `ProdutoRepositorioIT.a_arvore_inteira` (`:101-103`): -2,00 sobrevive à gravação com o sinal. No front, `ProdutoAberto.test.tsx` "2" (`:144-154`, positivo, negativo, zero) e "M2" (`:347-363`) | — |
| 3.2a | Os quatro estados por produto | `ProdutoController.java:359` `@PutMapping /{produtoId}/disponibilidade`; a da opção em `:390` | `MarcarDisponibilidade.tsx` (quatro botões, `:113`), renderizado por `ListaDeProdutos.tsx:156` | **sim.** `DisponibilidadeTest.qualitativo_aceita_os_quatro` (`:148-164`) afirma os quatro em sequência. Pela rota, `MarcacaoDeDisponibilidadeIT` cobre ESGOTADO_HOJE (`:123`), ESGOTADO_INDETERMINADO (`:168`) e DISPONIVEL (`:187`); ACABANDO pela rota só aparece no front: `MarcarDisponibilidade.test.tsx` "2" (`:90-114`). Também `disponibilidade.test.ts:164`, `ESTADOS` com tamanho 4 | **em parte.** 19/10/2026, itens 14 e 15 (`passagem-humana.md:233-238`): só "Acabou hoje" está escrito |
| 3.2b | Esgotado hoje volta sozinho a disponível na abertura do expediente seguinte | Consumidor `OuvinteDeExpedienteAlterado.java:54` `@RabbitListener`; produtor `merchant` `VarreduraDeAbertura.java:46` `@Scheduled`, que chama `PublicarAberturaDeExpediente.java:87` | Nenhuma tela dispara a volta, porque ela é automática. Quem mostra o resultado ao comerciante é `ListaDeProdutos.tsx` + `MarcarDisponibilidade.tsx` (o estado pressionado e o selo "não vendável"). Leio isso como **em parte** | **sim, em duas metades.** `ConsumoDeExpedienteIT.abertura_reativa` (`:80-89`): uma abertura de hoje sobre ESGOTADO_HOJE de ontem leva a DISPONIVEL no banco; `reentrega_e_idempotente` (`:125-145`). `merchant` `AberturaDeExpedienteIT.dentro_do_horario_a_abertura_e_publicada` (`:108-117`): a varredura dentro da faixa grava `ABERTURA_DE_EXPEDIENTE` no outbox; `a_madrugada_…` e `o_expediente_do_payload_…` (`:146-169`). Nenhum teste liga as duas metades de ponta a ponta | **—.** O registro de 19/10 diz que a volta **não** foi exercida (`passagem-humana.md:240-244`) |

Nenhum dos cinco é verdadeiro por vazio.

---

### 2. Divergências

| Critério | Coluna | A tabela diz | Eu achei | Peso |
|---|---|---|---|---|
| 3.2b | teste | só `ConsumoDeExpedienteIT` "1 · abertura bem formada…" | Esse teste cobre só o consumidor. Se o `merchant` deixar de publicar na abertura, ele continua verde. A metade do produtor tem teste e não está citada: `backend/services/merchant-service/src/test/java/com/deliveryplatform/merchant/integration/AberturaDeExpedienteIT.java:108-117` (e `:128-169`) | só relatório (afirma menos) |
| 3.2b | tela | — | O resultado da volta aparece em `frontend/src/painel/ListaDeProdutos.tsx:145-161` e `MarcarDisponibilidade.tsx:113-130`. Nenhuma tela dispara a volta. É leitura minha e vale como "em parte" | só relatório (afirma menos) |

Observação sem divergência: na 3.1c, a tabela cita testes do tamanho e das remoções, mas nenhum da parte "adicionais opcionais". O repositório a cobre por `GrupoDeOpcoesTest.opcional_vazio` e `VendavelTest.grupo_opcional_esgotado_nao_impede`. A tabela não afirma mais do que existe.

**Linha "Estado"** (*"5 critérios contam; 2 com as quatro colunas, os 2 em parte; 5 com teste; 2 exercidos, os 2 em parte; a 3.2b não tem tela e não foi exercida. O marco não fecha."*): confere com as células. Ressalva: chamar de "com as quatro colunas" uma linha cuja exercida é "em parte" é generoso diante da regra 4 do `fechar-marco.md`. A conclusão "não fecha" é a mesma.

---

### 3. Critérios sem divergência
- **3.1a:** rota, tela, teste e exercida (em parte), tudo igual.
- **3.1b:** fora da conta, igual (`PRD.md:154`).
- **3.1c:** rota, tela, teste e exercida ("—"), tudo igual.
- **3.1d:** rota, tela, teste e exercida ("—"), tudo igual.
- **3.1e:** fora da conta, igual (`PRD.md:157`).
- **3.2a:** rota, tela, teste e exercida (em parte, esgotado hoje), tudo igual.
- **3.2b:** rota igual; exercida igual ("—", com a ressalva do registro). As colunas tela e teste estão no item 2.
- **3.2c:** fora da conta, igual (`PRD.md:162`).

---

### 4. Arquivos abertos e caminhos de busca

**Abertos:**
- .claude/skills/metodo-delivery-platform/fechar-marco.md
- docs/PRD.md (trechos 150-171 e 387-436)
- backend/services/catalog-service/src/main/java/com/deliveryplatform/catalog/api/controller/ProdutoController.java
- frontend/src/painel/NovoProduto.tsx
- frontend/src/painel/ProdutoAberto.tsx
- frontend/src/painel/MarcarDisponibilidade.tsx
- frontend/src/painel/ListaDeProdutos.tsx (trecho 100-175)
- backend/services/catalog-service/src/test/java/com/deliveryplatform/catalog/integration/CriacaoDeProdutoIT.java
- backend/services/catalog-service/src/test/java/com/deliveryplatform/catalog/integration/MontagemDeOpcoesIT.java
- backend/services/catalog-service/src/test/java/com/deliveryplatform/catalog/integration/ProdutoRepositorioIT.java (trecho)
- backend/services/catalog-service/src/test/java/com/deliveryplatform/catalog/unit/VendavelTest.java
- backend/services/catalog-service/src/test/java/com/deliveryplatform/catalog/unit/GrupoDeOpcoesTest.java
- backend/services/catalog-service/src/test/java/com/deliveryplatform/catalog/unit/DisponibilidadeTest.java (trecho)
- backend/services/catalog-service/src/test/java/com/deliveryplatform/catalog/integration/ConsumoDeExpedienteIT.java (trecho)
- backend/services/merchant-service/src/main/java/com/deliveryplatform/merchant/infrastructure/agenda/VarreduraDeAbertura.java
- backend/services/merchant-service/src/test/java/com/deliveryplatform/merchant/integration/AberturaDeExpedienteIT.java
- frontend/src/painel/ProdutoAberto.test.tsx (trechos)
- frontend/src/painel/NovoProduto.test.tsx (trecho)
- docs/operacao/passagem-humana.md (inteiro)
- docs/marcos/marco-2.md, a tabela recebida, aberta só na fase 2

**Caminhos em que busquei (Grep/Glob):**
- docs/PRD.md
- docs/dividas.md
- docs/operacao/passagem-humana.md
- backend/services/catalog-service/src/main
- backend/services/catalog-service/src/test (Glob e Grep)
- MontagemDeOpcoesIT.java, CriacaoDeProdutoIT.java, MarcacaoDeDisponibilidadeIT.java, MarcarDisponibilidadeServiceTest.java, DisponibilidadeTest.java, ConsumoDeExpedienteIT.java
- backend/services/merchant-service/src e backend/services/merchant-service/src/main
- backend/infra/gateway/src/main/resources/application.yml
- frontend/src (Glob `**/*.tsx` e Grep sem os `*.test.tsx`)
- ProdutoAberto.test.tsx, NovoProduto.test.tsx, MarcarDisponibilidade.test.tsx

Não abri nem busquei nada em `docs/rodadas/`, `_to_delete/`, nenhum `APLICAR-*.md` e nada na pasta `a1/` da raiz. De `docs/marcos/` abri só o `marco-2.md`. Nenhum arquivo foi alterado.
