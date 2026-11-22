# ADR-088 — A cotação do catálogo: a loja na URL, o vínculo basta, e o 409 traz tudo o que caiu

- **Status:** aceita
- **Data:** <<DATA>>
- **Rodada:** M3-2
- **Relacionada:** ADR-018 (a cotação e o congelamento das opções), ADR-086 (o marco 3 em fatias —
  esta é a fatia 2), ADR-087 (a rota irmã no `merchant`), ADR-056 (a loja da URL autoriza),
  ADR-034 (resposta congelada não vem de cache), ADR-024 (o preço não varia por modalidade),
  ADR-054 (a contradição do `/internal/` sem portador), o `catalogo.md` §4, §5 e §9

## Contexto

**A ADR-018 desenhou a cotação** — `POST /internal/catalog/quote`, com o id da loja no corpo — e o
`catalogo.md` §5 fixou o comportamento: 400 para o que é erro de quem chama, 409 para o mundo que
mudou. **A ADR-086 deixou duas perguntas para a rodada da cotação:** que permissão ela exige, e,
pela ADR-056, que autoriza *"pela loja da URL"*, onde a loja vai.

| Fato | Reverifica com |
|---|---|
| As rotas `/internal/` que existem põem a loja na URL | `grep -rn 'Mapping("/internal' backend/services/*/src/main` — em `b275220`, as quatro do `merchant`, todas `/internal/merchants/{estabelecimentoId}` |
| O `catalogo.md` §5 não diz o que responde para produto inexistente; o §1 já fixa, para identificador que vem no corpo, *"não existe, ou é de outra loja: a mesma recusa, com a frase idêntica (M7)"* | `sed -n '/^## 5. Cotação/,/^## 6/p' docs/dominio/catalogo.md`; `grep -n "Não existe, ou é de outra loja" docs/dominio/catalogo.md` |
| Nenhum documento trata a mesma opção enviada duas vezes no mesmo item | `grep -rn -i -e "repetid" -e "duas vezes" docs/dominio/catalogo.md` — nada sobre seleção |
| O modelo de opção não tem quantidade: `Opcao` tem nome, acréscimo e disponibilidade | `catalogo.md` §4 |

## Decisão

1. **A loja vai na URL:** `POST /internal/merchants/{estabelecimentoId}/catalog/quote`. A
   autorização é a de sempre — a loja da URL, conferida antes de qualquer produto. *Decisão do
   Rafael.*
2. **O vínculo ativo basta, sem permissão.** A cotação só lê preço de cardápio; a permissão de
   lançar pedido fica num lugar só, a rota de lançar do `order`. É a regra da rota irmã, o
   `/operacao` do `merchant` (ADR-087, Decisão 4). *Decisão do Rafael.*
3. **O preço não varia por modalidade, e a cotação não recebe modalidade** — a ADR-024 decidiu e
   pediu confirmação antes de escrever o `cotar`; a confirmação veio nesta rodada (ADR-024, emenda
   desta data). *Decisão do Rafael.*
4. **A mesma opção duas vezes no mesmo item é 400.** O modelo não tem quantidade por opção, e a
   contagem de `maxEscolhas` ficaria ambígua. Quem vende "bacon em dobro" cadastra a opção "bacon
   duplo". *Decisão do Rafael.*
5. **Produto inexistente responde igual a produto de outra loja: 409, a mesma entrada na lista.** É a
   regra que o `catalogo.md` §1 já fixa para identificador que vem no corpo — *"não existe, ou é de
   outra loja: a mesma recusa"* (M7) —, aplicada à cotação. E não é 404: a ADR-056 (linhas 91 e 92)
   reserva o 404 para *"qualquer outro identificador na URL"*, e o produto da cotação vem no corpo.
   A skill `novo-caso-de-uso` passa a dizer essa fronteira.
6. **400 vence 409.** A estrutura de cada item — opção que pertence ao produto, repetição, mínimo e
   máximo de cada grupo, quantidade — é conferida antes da disponibilidade, e o primeiro 400
   interrompe a cotação inteira. O 400 é defeito de quem chama e vira alerta.
7. **O 409 traz tudo o que caiu, de uma vez:** cada produto que caiu, e **cada** opção escolhida
   indisponível de cada produto — não só a primeira. O atendente vê tudo numa resposta, como a
   ADR-018 pede (*"com a lista do que caiu"*). O corpo é o `ProblemDetail`, com a propriedade
   `indisponiveis`, uma entrada por coisa caída — `{ item, produtoId, opcaoId }`, em que `item` é a
   posição do item no pedido, a partir de zero, e `opcaoId` é nulo quando quem caiu foi o produto.
   **Sem nome de produto** — o de outra loja não pode ter nome na resposta —, e **sem repetição**:
   o mesmo produto em dois itens do pedido são duas entradas, distintas pelo `item`. O `detail` não
   conta nada: *"há itens que não estão à venda agora; confira a lista"*.
8. **Sem cache.** Cada cotação lê o produto do banco (ADR-034, `catalogo.md` §7). E não reserva
   nada (`catalogo.md` §5).

| Situação | Resposta |
|---|---|
| lista vazia, `quantidade < 1`, identificador que não é UUID | 400 |
| opção que não pertence ao produto; a mesma opção duas vezes; escolhas fora do mínimo ou do máximo do grupo | 400 |
| produto não `ATIVO`, ou não vendável (C6, C10); produto de outra loja; produto inexistente | 409, o produto na lista |
| opção escolhida indisponível | 409, o produto e a opção na lista |
| sem token | 401 |
| sem vínculo ativo, ou autorização indisponível | 403, o corpo único |
| vínculo ativo, sem nenhuma permissão | 200 |

## Consequências

**O contrato não declara o corpo do 409.** É a regra do `ContratoDeErrosIT`: resposta de erro sem
corpo declarado. Quem fixa a propriedade `indisponiveis` é o `CotacaoControllerIT`, e o consumidor da
fatia 4 a lê de lá.

**Uma cotação, uma ida ao banco**, pelo `ProdutoRepositorio.buscarPorIds`.

**O `catalog` ganha a primeira rota interna**, e ela exige o token do portador como as outras — cai
no `anyRequest().authenticated()` do `SecurityConfig` dele. A contradição da ADR-054 — a cotação que
um consumidor de WhatsApp chamaria sem portador — **não aparece no marco 3**, porque quem chama é o
atendente; ela continua com o 7a, pela linha 48 do razão.

## Alternativas consideradas

**A permissão `VER_PRODUTO`.** **Recusada:** o `catalog` já a conhece, mas um atendente com a
permissão de lançar e sem `VER_PRODUTO` não conseguiria lançar — duas permissões para configurar no
convite, para um mesmo ato.

**A permissão nova de lançar.** **Recusada:** o `catalog` passaria a nomear uma permissão que nasce
na fatia 3, em outro serviço; e o canal do 7a, sem portador, precisa de outro caminho de qualquer
jeito.

**A loja no corpo, como a ADR-018 a desenhou.** **Recusada:** seria a única rota do repositório que
autoriza por um campo do corpo, e a ADR-056 autoriza pela loja da URL.

**A opção repetida contando duas vezes.** **Recusada:** exige quantidade por opção, que o modelo não
tem, e a repetição entraria duas vezes no congelamento da ADR-018 sem ninguém ter decidido como
imprimi-la.

**Só a primeira opção indisponível de cada item.** **Recusada:** a segunda só apareceria na cotação
seguinte, e o atendente descobriria um item por vez.

## Gatilho escrito

**Nenhum.**
