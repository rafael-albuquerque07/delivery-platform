# Domínio — Cardápio, opções e disponibilidade

**Serviço:** `catalog-service` · **Status:** vigente (v1.1, 21/08/2026)
**Fontes:** PRD §5 (P3, P6), PRD §6 E3, PRD §10 (marco 2), ADR-004, ADR-007, ADR-008, ADR-017, ADR-018
**Invariantes do `CLAUDE.md` que este documento detalha:** 2, 4, 8
**Entrega no marco 2** — é o segundo serviço com valor operável, logo depois de conta e equipe.

O catálogo é o **dono do preço**. Nenhum outro serviço calcula quanto custa
alguma coisa; todos perguntam. Essa é a razão de a invariante 2 do `CLAUDE.md`
ser cumprível: o valor cobrado vem do pedido, e o pedido vem daqui.

---

## 1. Agregados

```
Produto  (raiz)
├── estabelecimentoId, categoriaId
├── nome, descricao, imagemRef
├── precoBase                 Money
├── estadoDePublicacao        RASCUNHO | ATIVO | INATIVO
├── modoDeControle            SEM_CONTROLE | QUALITATIVO | QUANTITATIVO
├── disponibilidade           estado, marcadoEm, expedienteDeReferencia
└── GrupoDeOpcoes      [n]
    ├── nome, minEscolhas, maxEscolhas, ordem
    └── Opcao          [n]    nome, acrescimo, disponivel, ordem

Categoria  (raiz)
├── estabelecimentoId, nome, ordem, ativa
```

**Por que `GrupoDeOpcoes` e `Opcao` ficam dentro do `Produto`.** A invariante C6
— produto com grupo obrigatório precisa ter opção disponível para ser vendável —
só é verificável se produto e opções mudarem na mesma transação. E a cotação
(ADR-018) lê o produto inteiro de uma vez: em MongoDB isso é uma leitura de
documento, que é exatamente o motivo de o catálogo ser documental (ADR-017).

**Por que `Categoria` é raiz própria.** Reordenar o cardápio move dezenas de
produtos entre categorias sem tocar em nenhum deles. Como parte do produto, a
ordem das categorias seria um dado replicado em N documentos, e a primeira
reordenação deixaria metade desatualizada.

> **Nota de nomenclatura.** A ADR-018 modela isto em inglês (`Product`,
> `optionGroups`, `minSelect`). Os documentos de domínio da v1.1 usam português,
> que é a linguagem em que o negócio fala — *bairro*, *troco*, *jornada*,
> *comanda* não têm tradução honesta. A divergência é real e precisa de decisão
> antes do primeiro código: sugestão é **domínio em português, infraestrutura e
> framework em inglês**. Está anotado como pendência em §9.

---

## 2. Estado de publicação

| Estado | Aparece ao cliente | Cotiza | Uso |
|---|---|---|---|
| `RASCUNHO` | não | **não** | Em edição. Nunca foi publicado |
| `ATIVO` | sim | sim | Vendável |
| `INATIVO` | não | **não** | Saiu do cardápio, histórico preservado |

Produto nunca é apagado. Pedidos antigos referenciam `produtoId`, e mesmo com o
snapshot completo (ADR-018) a referência precisa resolver para suporte e
relatório.

`RASCUNHO → ATIVO` é o ato de publicar, e ele valida tudo: preço, grupos,
mínimos e máximos. **Publicar é onde as invariantes são cobradas** — em rascunho
o comerciante pode deixar o produto pela metade, que é como se trabalha.

---

## 3. Disponibilidade qualitativa

A premissa P6: a maioria dos produtos não tem saldo contado. Tem "acabou a
calabresa".

| Estado | Oferecido | Volta sozinho |
|---|---|---|
| `DISPONIVEL` | sim | — |
| `ACABANDO` | sim, com aviso | — |
| `ESGOTADO_HOJE` | **não** | **sim — na abertura do próximo expediente** |
| `ESGOTADO_INDETERMINADO` | **não** | não |

`ACABANDO` não bloqueia nada. É sinal para o atendente e para o cliente, não
regra — quem transformar isso em bloqueio quebra a operação de quem usa o estado
para dizer "vai até umas dez da noite".

### A reativação automática, e por que ela não é um cron

O PRD é explícito sobre o valor disto:

> Se o comerciante precisar lembrar de reativar doze itens toda manhã, ele para
> de usar na segunda semana.

A tentação é um job à meia-noite. **Está errado.** A pizzaria abre às 18h e
fecha às 2h; à meia-noite ela está vendendo, e um job zeraria a calabresa que
acabou às 23h — voltando a oferecer o que não existe, no meio do pico.

O gatilho correto é o **evento de abertura do estabelecimento**:

```
DisponibilidadeAlteradaV1 (merchant-service)
  motivo = ABERTURA_DE_EXPEDIENTE
      ↓
catalog-service reativa todo produto e opção com:
      estado == ESGOTADO_HOJE
   ∧  expedienteDeReferencia != expediente atual
```

Três consequências que precisam estar no código:

1. **Retomada de pausa não reativa nada.** Pausar às 20h e retomar às 20h30 é o
   mesmo expediente. Só a transição fechado → aberto **por horário** conta. Por
   isso o evento carrega o motivo, e por isso `merchant-service` distingue
   pausa de abertura (`estabelecimento.md` §4).
2. **`expedienteDeReferencia` é obrigatório.** Sem ele, marcar um produto como
   esgotado depois da abertura e reprocessar o evento reativaria algo marcado no
   expediente corrente. O identificador do expediente é o que torna a operação
   idempotente — e ela **vai** ser reprocessada, porque a mensagem se repete
   (invariante 7 do `CLAUDE.md`).
3. **Loja que não abriu não reativa.** Segunda-feira fechada não zera o que
   acabou no domingo. É o comportamento certo: o estoque físico também não se
   repõe sozinho.

**`expedienteDeReferencia` é o `diaOperacional` da loja** (ADR-025). Isso
responde o caso que faltava: a loja que abre **duas vezes no mesmo dia**.

```
06:00  abre           expediente = D
11:00  acabou o pão   expedienteDeReferencia = D
14:00  fecha
18:00  abre de novo   expediente = D  →  D == D  →  NÃO reativa   ✓
```

O pão que acabou no almoço continua acabado no jantar, que é o comportamento
certo — o estoque físico também não se repôs. No dia seguinte, `D+1 ≠ D`, e
reativa.

### Disponibilidade da opção

**Acréscimo ao PRD, e necessário.** E3 fala de disponibilidade por produto.
Acabou a borda recheada, não a pizza — sem disponibilidade por opção, o
comerciante teria que tirar do ar todas as pizzas ou vender o que não tem.

`Opcao.disponivel` segue as mesmas quatro situações e a mesma reativação.

---

## 4. Preço e opções

```
GrupoDeOpcoes
├── nome            "Tamanho", "Adicionais", "Remover"
├── minEscolhas     0 = opcional · ≥1 = obrigatório
├── maxEscolhas     teto de seleções
└── ordem

Opcao
├── nome            "Grande", "Bacon", "Sem cebola"
├── acrescimo       Money — pode ser zero, pode ser negativo
└── disponivel
```

`acrescimo` negativo é legítimo: o comerciante que desconta R$ 2 por tirar o
queijo está exercendo política de preço, não erro de digitação. O que a
invariante protege é o resultado — `precoUnitario` tem que ser maior que zero.

Remoções são grupo com `acrescimo = 0` em todas as opções. Existem porque a
cozinha precisa da instrução e o pedido precisa registrá-la (ADR-018 congela o
nome), não porque mudam valor.

```
precoUnitario = precoBase + Σ acrescimo das opções escolhidas
subtotal      = precoUnitario × quantidade
```

**O preço não varia por modalidade** (ADR-024). A diferença entre entrega e
retirada vive na taxa por área (ADR-020) e no `descontoDeRetirada` do
estabelecimento — nunca no `precoBase`. Por isso `cotar` não recebe modalidade.

Arredonda-se ao formar cada componente, nunca no fim (ADR-009).

### Vendabilidade derivada

A regra menos óbvia deste documento.

```
vendavel  =  estadoDePublicacao == ATIVO
          ∧  disponibilidade ∈ { DISPONIVEL, ACABANDO }
          ∧  ∀ grupo com minEscolhas ≥ 1 :
                contar(opções disponíveis no grupo) ≥ minEscolhas
```

Pizza `DISPONIVEL` cujo grupo "Tamanho" é obrigatório e está com **todos** os
tamanhos esgotados **não é vendável** — não há seleção válida possível. Sem esta
regra o produto aparece no cardápio, o cliente escolhe, e a cotação recusa com
400 no fim do fluxo. No WhatsApp isso custa turnos, e turno custa dinheiro
(H8.3).

`vendavel` é **derivado, nunca gravado**. Gravado, ele desatualiza no instante
em que uma opção muda de estado.

---

## 5. Cotação — a operação que sustenta o preço

Contrato e justificativa na ADR-018. O que este documento fixa é o
comportamento de domínio.

```
cotar(estabelecimentoId, [ { produtoId, quantidade, opcoesEscolhidas[] } ])
```

| Situação | Resposta | Por quê |
|---|---|---|
| Seleção viola `minEscolhas`/`maxEscolhas` | **400** | Erro do chamador: pedido malformado |
| Opção não pertence ao produto | **400** | Idem |
| `quantidade < 1` | **400** | Idem |
| Produto não `ATIVO`, ou não vendável | **409** | Estado do mundo mudou, não erro do chamador |
| Opção escolhida indisponível | **409** | Idem |
| Produto de outro estabelecimento | **409** | ADR-004 — um estabelecimento por pedido |

A distinção 400/409 não é preciosismo. O `order-service` trata as duas de formas
diferentes: 400 é defeito e vira alerta; 409 é `ITEM_UNAVAILABLE` e volta ao
cliente com a lista do que caiu, para reconfirmação (ADR-018).

**A cotação é o ponto de verdade, não a listagem.** O cardápio exibido pode
estar em cache de segundos atrás; a cotação lê o estado corrente. Entre ver a
pizza na tela e confirmar o pedido, a calabresa pode ter acabado — e é a cotação
que descobre isso.

**A cotação não reserva nada.** Ela responde "quanto custa e está disponível
agora". Reserva só existe no modo `QUANTITATIVO`, no marco 10.

---

## 6. Modo de controle

| Modo | Comportamento | Marco |
|---|---|---|
| `SEM_CONTROLE` | Sempre disponível. Refrigerante em lata, taxa de serviço | 2 |
| `QUALITATIVO` | Os quatro estados da §3. **Padrão do MVP** | 2 |
| `QUANTITATIVO` | Saldo, reserva ao entrar no pedido, baixa na conclusão | **10** |

O campo existe desde o marco 2 e é congelado no item do pedido como
`stockControlledSnapshot` (ADR-018) — um boolean hoje, para evitar migration de
dados quando o marco 10 chegar. Enquanto isso, `QUANTITATIVO` é valor **inválido
na publicação**, com mensagem que diz o marco.

> **Divergência a corrigir no PRD.** H3.3 e H4.3 dizem "*entra a partir do marco
> 5*", e H10.1 diz "*marco 7*". A tabela do §10 — reescrita na revisão v1.1 —
> coloca controle quantitativo e substituição de item no **marco 10** e emissão
> fiscal no **marco 8**. As anotações inline não foram atualizadas junto. A
> tabela §10 é a correta; as três anotações estão velhas.

---

## 7. Persistência e cache

Documental por decisão de aprendizado (ADR-017), e o formato ajuda: produto com
grupos e opções é uma árvore lida inteira, gravada inteira.

| Aspecto | Regra |
|---|---|
| Esquema | Toda estrutura e todo índice nascem em `changeUnit` do Mongock — ADR-007. Nunca comando no shell |
| Transação | Replica set de nó único — sem ele não há transação multi-documento e portanto não há outbox — ADR-008 |
| Índices mínimos | `(estabelecimentoId, estadoDePublicacao)` · `(estabelecimentoId, categoriaId, ordem)` |
| Cache | Cardápio público no Redis, TTL curto, invalidado por evento |
| Imagem | `imagemRef` aponta para o MinIO. **Binário nunca entra no documento** |

O cache do cardápio pode servir dado de segundos atrás. É aceitável **porque a
cotação não usa cache** — a listagem é vitrine, a cotação é contrato.

---

## 8. Eventos publicados

Todos com `correlationId`, todos por outbox na mesma transação da alteração.

| Evento | Quando | Consumidores |
|---|---|---|
| `ProdutoPublicadoV1` | `RASCUNHO → ATIVO` | `conversation`, invalidação de cache |
| `ProdutoAlteradoV1` | Nome, preço, descrição, opções | `conversation`, invalidação de cache |
| `ProdutoDespublicadoV1` | `ATIVO → INATIVO` | `conversation` |
| `DisponibilidadeAlteradaV1` | Produto ou opção muda de estado | `conversation` — retirar do menu **rápido** |
| `CategoriasReordenadasV1` | Ordem do cardápio | `conversation` |

`DisponibilidadeAlteradaV1` é o mais sensível a atraso. Entre a Marli marcar
"acabou" e o canal parar de oferecer, cada segundo é um pedido que vai ser
cancelado na cozinha.

O `catalog-service` **consome** `DisponibilidadeAlteradaV1` do
`merchant-service` (§3) — mesmo nome de evento, serviços diferentes, significados
diferentes. **Renomeie um dos dois antes de escrever o primeiro consumidor**: um
tópico ambíguo é uma noite perdida daqui a três meses. Sugestão:
`ExpedienteAlteradoV1` no `merchant-service`.

---

## 9. Invariantes

| # | Invariante | O que quebra sem ela |
|---|---|---|
| C1 | `precoBase > 0` | Produto de graça por descuido |
| C2 | `precoUnitario > 0` para toda combinação válida | Acréscimo negativo zera o preço |
| C3 | `0 ≤ minEscolhas ≤ maxEscolhas` | Grupo impossível de satisfazer |
| C4 | `maxEscolhas ≤ contagem de opções do grupo` | Teto que nunca é alcançável |
| C5 | `minEscolhas ≤ contagem de opções do grupo` | Publicação de produto inconsultável |
| C6 | `vendavel` derivado, nunca gravado | Cardápio oferece o que a cotação recusa |
| C7 | Opção pertence a um grupo; grupo a um produto | Opção órfã aplicada ao produto errado |
| C8 | Produto pertence a **um** estabelecimento (ADR-004) | Cardápio vaza entre lojas |
| C9 | Produto nunca é apagado — `INATIVO` | Pedido antigo com referência morta |
| C10 | `RASCUNHO` e `INATIVO` nunca cotizam | Cliente pede o que não está à venda |
| C11 | Reativação de `ESGOTADO_HOJE` é idempotente por `expedienteDeReferencia` | Mensagem repetida reativa o que acabou agora |
| C12 | `QUANTITATIVO` é inválido na publicação até o marco 10 | Promete contagem que não existe |
| C13 | Toda estrutura e índice via Mongock | Ambiente diverge do outro em silêncio |

---

## 10. O que este documento deliberadamente não decide

- **Combos e promoções.** Não estão no PRD. Um combo não é produto com opções: é
  composição com preço próprio, e forçá-lo no modelo de grupos produz um
  cardápio que ninguém consegue manter.
- **Cardápio por faixa de horário** — café da manhã até as 10h. Recorrente em
  padaria e lanchonete, ausente do PRD.
- **Idioma da nomenclatura** (§1). Domínio em português, infraestrutura em
  inglês é a sugestão; a ADR-018 usa inglês e precisa de um passe de tradução ou
  de uma decisão explícita de conviver com os dois. **Decidir antes do primeiro
  código do `catalog-service`.**
- **Limite de opções por grupo e grupos por produto.** A lista interativa do
  WhatsApp tem teto de linhas por seção (H8.1), o que na prática impõe um limite
  — mas ele é do canal, e é o `conversation-service` que deve paginá-lo, não o
  catálogo que deve se mutilar por causa dele.
