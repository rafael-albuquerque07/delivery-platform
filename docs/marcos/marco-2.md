# Marco 2 — o cardápio

**A coluna do PRD §10:** *"Publicar o cardápio e marcar o que acabou"* · **as histórias:** H3.1 e
H3.2.

**Como esta tabela se lê:** a skill `metodo-delivery-platform`, em `fechar-marco.md`. Rota e tela
medidas na R-13, em `43f41cf`; teste medido na S-3, sobre `3ff45f8`; exercida, pelo
`docs/operacao/passagem-humana.md`.

**Estado:** 5 critérios contam; 2 com as quatro colunas, os 2 em parte; 5 com teste, 1 deles em
parte; 2 exercidos, os 2 em parte; a 3.2b não tem tela e não foi exercida. O marco não fecha.

| # | Critério | Rota | Tela | Teste | Exercida |
|---|---|---|---|---|---|
| 3.1a | Produto com preço-base e descrição | `POST …/catalog/produtos` | `NovoProduto` | `CriacaoDeProdutoIT` — "A1/A2/A6: 201, nasce RASCUNHO, Location para o GET, e o banco tem o categoriaId do pedido" (o banco guarda nome, descrição e preço-base); `NovoProduto.test.tsx` — "C10 · a ordem enviada é a maior da seção escolhida, mais um" (o corpo leva o `precoBase`) | 19/10, "O que foi exercido", em parte: um produto publicado; o preço e a descrição não estão escritos |
| 3.1b | Imagem do produto | *Entra a partir do 7a* (decisão de 17/11/2026) — fora da conta | | | |
| 3.1c | Grupos de opções com mínimo e máximo de escolhas: tamanho obrigatório, adicionais opcionais, remoções | `POST …/produtos/{produtoId}/grupos` | `ProdutoAberto` | `MontagemDeOpcoesIT` — "o rascunho ganha um grupo vazio, e depois a opção dentro dele — pelo banco"; `ProdutoAberto.test.tsx` — "M1 · produto em rascunho: criar grupo manda o POST com a ordem derivada, e relê" | — |
| 3.1d | Cada opção com seu acréscimo de preço | `POST …/grupos/{grupoId}/opcoes` | `ProdutoAberto` | `MontagemDeOpcoesIT` — "o rascunho ganha um grupo vazio, e depois a opção dentro dele — pelo banco" (o acréscimo de R$ 16,00 lido do banco); `ProdutoAberto.test.tsx` — "M2 · criar opção num grupo: o POST vai ao grupo certo, com o acréscimo negativo e a ordem derivada" | — |
| 3.1e | Produto rascunho não aparece para o cliente | *Entra a partir do 7a* — fora da conta | | | |
| 3.2a | Estado por produto: disponível, acabando, esgotado hoje, esgotado por tempo indeterminado | `PUT …/produtos/{produtoId}/disponibilidade` | `MarcarDisponibilidade` | em parte: `MarcacaoDeDisponibilidadeIT` — "ESGOTADO_HOJE: 200, o corpo traz o estado novo, e o banco guarda o carimbo do merchant"; `MarcarDisponibilidade.test.tsx` — "2 · o item é substituído pelo produto da resposta — inteiro, com o vendavel recalculado" (acabando). Marcar disponível com sucesso: nenhum | 19/10, itens 13 e 15, em parte: esgotado hoje; os outros três estados não estão escritos |
| 3.2b | Esgotado hoje volta automaticamente a disponível na abertura do expediente seguinte | o consumidor do `ExpedienteAlteradoV1` | — | `ConsumoDeExpedienteIT` — "1 · abertura bem formada: o produto de ontem volta, e a fila morta fica vazia" | — (o item 15 diz que a volta na abertura seguinte não foi exercida) |
| 3.2c | Produto esgotado não é oferecido no canal nem aceito no rascunho da conversa | *Entra a partir do 7a* — fora da conta | | | |
