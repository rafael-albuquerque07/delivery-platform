# ADR-006 — O rascunho pertence à conversa; não existe carrinho

**Status:** Aceita — 26/08/2026
**Substitui o assunto reservado como:** "Carrinho no Redis: TTL, durabilidade e ordem do checkout"
**Relacionada:** ADR-004 (um estabelecimento por pedido), ADR-017 (MongoDB), ADR-018 (snapshot e cotação), ADR-024 (desconto de retirada)
**Emenda:** ADR-018, que citava esta decisão antes de ela existir
**Entra no marco 7** — é quando a conversa ganha código

## Contexto

O número 006 foi reservado para uma decisão chamada **"Carrinho no Redis: TTL,
durabilidade e ordem do checkout"**. O título presume a resposta: existe um
carrinho, ele é um objeto próprio, e a pergunta é onde guardá-lo.

Duas coisas aconteceram desde então.

### A premissa P4 mudou de onde o pedido nasce

> **P4** — O pedido nasce predominantemente em **conversa**.
> *Se deixar de valer:* o canal deixa de ser fundação e vira integração acessória.

Num produto com aplicativo de consumidor, o carrinho é objeto próprio porque a
sessão do cliente é o único lugar onde ele existe. Aqui não há aplicativo — o
PRD §7 lista "aplicativo nativo para consumidor" como **não-objetivo**, e o
cliente conversa num número de WhatsApp.

### E o rascunho já foi desenhado, com outro nome

`conversa.md` §1, dentro do agregado `Conversa`:

```
Conversa  (raiz)
├── estabelecimentoId, contatoId
├── rascunhoDePedido   identificadores apenas — nunca valores
```

É **exatamente** o desenho que a ADR-018 atribui ao "carrinho no Redis". A
decisão foi tomada ao escrever o documento de domínio da conversa, e nunca foi
registrada como decisão — o que produziu o defeito que a ADR-018 carrega: ela
cita a ADR-006 e descreve o conteúdo dela, apoiando-se numa decisão inexistente.

## Decisão

### 1. Não existe carrinho. Existe `rascunhoDePedido`, e ele é da `Conversa`.

Nenhum agregado novo, nenhum banco novo, nenhum TTL próprio. O rascunho é campo
do agregado `Conversa`, no `conversation-service`, em MongoDB (ADR-017).

```
antes                             agora
Carrinho (agregado próprio)       Conversa
├── ttl                           ├── estabelecimentoId
├── estabelecimentoId             ├── janela        ← o tempo vive aqui
└── itens [ids]                   └── rascunhoDePedido [ids]
```

### 2. A restrição da ADR-004 passa a ser estrutural

A ADR-004 exige que um pedido pertença a um estabelecimento só, e diz:

> A regra precisa de guarda **no carrinho**, não só no pedido.

Com o rascunho dentro da `Conversa`, essa guarda **deixa de precisar existir**.
`Conversa` tem `estabelecimentoId` como campo da raiz, e o roteamento é por
número (`conversa.md` §2): uma conversa é com **uma** loja. Não há como um
rascunho conter item de duas, porque não há onde o segundo estabelecimento
caberia.

Uma validação que some porque o modelo a tornou impossível é melhor que uma
validação correta — é a diferença entre garantir e lembrar de conferir.

A guarda no `order`, em T01, **continua** (I7). Estado externo não se confia
duas vezes, e é o que a própria ADR-004 manda.

### 3. Durabilidade: sobrevive a reinício, e é isso que se quer

Redis sem persistência perderia rascunhos numa reinicialização. Parece aceitável
até se pensar em quem usa: a Marli está montando um pedido de doze itens com a
cliente, pelo WhatsApp, e o rascunho some. A conversa continua — as mensagens
estão lá — e o pedido pela metade, não.

Dentro do `Conversa`, em MongoDB, o rascunho tem a mesma durabilidade das
mensagens. **A unidade de recuperação passa a ser a conversa inteira**, que é o
que o cliente percebe como "meu pedido".

Há um custo, e é de proteção de dados: o rascunho passa a ser dado do titular
sob as regras da ADR-013 — retenção de 180 dias após o encerramento, e exclusão
a pedido apaga junto. É correto, e é mais do que um Redis efêmero exigiria.

### 4. Tempo: o rascunho não tem TTL próprio. Ele vive enquanto a conversa vive.

O título reservado pedia um TTL. Ele não existe, e a razão é que já há **dois**
relógios no assunto, os dois da conversa:

| Relógio | O que é | Onde |
|---|---|---|
| `janela` | as 24 horas do WhatsApp | `conversa.md` §8 |
| validade do rascunho | quanto tempo um pedido pela metade continua fazendo sentido | `conversa.md` §16 — **valor ainda em aberto** |

Um terceiro relógio, próprio do carrinho, só criaria a pergunta de qual dos três
vence. O valor da validade do rascunho continua indecidido, e continua onde
está — é número de produto, e sai do primeiro cliente.

### 5. Ordem do fechamento: já escrita, e esta ADR só a nomeia

`conversa.md` §5 define a sequência, e ela não muda:

```
1. resumo         itens, opções, endereço, taxa da área e total
                  — na retirada, sem endereço e sem taxa (I10),
                    com o desconto de retirada nomeado (ADR-024)
2. botão          carrega identificador do rascunho + marca da cotação
3. cotação velha  reconfirma com os números novos — PRICE_CHANGED (ADR-018)
                  NUNCA cobra a diferença em silêncio
4. T01            o order cria o pedido em RECEBIDO e congela
```

O passo 3 é o H4.2 do PRD cumprido: *"preço alterado entre a montagem e a
confirmação exige nova confirmação explícita"*.

E o passo 2 é a razão de o rascunho guardar identificadores: o botão carrega
**referência**, não valores. Se o rascunho tivesse preço, o passo 3 não teria com
o que comparar — e a invariante 2 do `CLAUDE.md`, que manda recalcular tudo no
servidor, viraria intenção.

## Consequências

**Positivas**

- **Um agregado a menos, um banco a menos.** O Redis continua existindo para
  cache de cardápio (`catalogo.md` §7) e não ganha responsabilidade de estado de
  negócio.
- A guarda de estabelecimento único vira estrutural (§2).
- O rascunho herda durabilidade, retenção e exclusão da conversa, sem regra
  própria.
- A ADR-018 deixa de citar decisão inexistente.

**Negativas**

- **Sem conversa, não há rascunho.** É a consequência mais séria, e está no §"o
  que esta ADR não decide". Qualquer superfície futura de montagem — painel,
  telefone, balcão — não tem onde montar.
- **O `conversation-service` fica com mais responsabilidade.** Ele já era o mais
  denso do sistema — canal, conversa, interpretação e custo — e ganha o estado do
  pedido em construção.
- **O rascunho vira dado pessoal com prazo.** Um carrinho em Redis com TTL de
  horas some sozinho; este entra no inventário da ADR-013 e no procedimento de
  exclusão.
- **Acoplamento de disponibilidade.** Se o `conversation-service` cair, nenhum
  pedido pode ser montado. Com carrinho separado, cairia só a conversa. É custo
  real, e o mitigante é que sem canal também não há cliente conversando.

## Alternativas consideradas

- **Carrinho no Redis, como o título reservado presumia.** Rejeitada em §3 e §4:
  perde rascunho em reinicialização, cria um terceiro relógio, e faz o Redis
  guardar estado de negócio depois de a ADR-011 tê-lo mantido como cache. Era a
  resposta certa para um produto com aplicativo de consumidor — que este não é.
- **Carrinho como agregado próprio no `order-service`, antes de `RECEBIDO`.**
  Tentadora, porque o `order` é quem valida. Rejeitada: acrescentaria um estado
  anterior a `RECEBIDO` na máquina de `pedido.md`, e T01 deixaria de ser criação
  para virar transição. Vinte e cinco transições viram vinte e seis, e a mais
  usada do sistema passa a ter um estado antes dela que só existe por causa da
  montagem.
- **Rascunho só no cliente, sem persistir.** Não há cliente onde persistir — a
  conversa é o cliente, e ela é do servidor.
- **Rascunho no `Contato`, e não na `Conversa`.** Sobreviveria ao encerramento da
  conversa, o que parece bom. Rejeitada: `Contato` atravessa estabelecimentos
  (`conversa.md` §1, `estabelecimentosConhecidos`), e um rascunho lá poderia
  conter item de loja que não é a da conversa atual — reintroduzindo pela porta
  dos fundos exatamente o que o §2 acabou de tornar impossível.

## O que esta ADR não decide

**Onde um pedido é montado quando não há conversa.** Esta decisão cobre o único
caminho que o repositório documenta hoje. Se aparecer um segundo — atendente
montando no painel, pedido por telefone, balcão — ele **não** tem onde montar, e
esta ADR precisa de emenda, não de contorno.

E há uma pergunta em aberto que decide se esse segundo caminho é necessário:

> O PRD §10.1 afirma que **ao fim do marco 6 existe produto completo**. A
> conversa entra no **marco 7**. Se a conversa é a única superfície de montagem,
> ao fim do marco 6 não há por onde um pedido entrar.

Ou o painel tem entrada de pedido e isso não está escrito em lugar nenhum, ou o
§10.1 promete mais do que os seis primeiros marcos entregam. **É pergunta de
produto, não de arquitetura**, e a resposta dela é o que determina se esta ADR
ganha uma segunda metade.

**O valor da validade do rascunho.** Continua em `conversa.md` §16, onde já
estava. É número de produto e sai do primeiro cliente, não daqui.
