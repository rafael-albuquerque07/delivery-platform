# ADR-032 — A guarda da conferência pergunta, não escuta

**Status:** Aceita — 25/08/2026
**Corrige:** uma guarda de `docs/dominio/liquidacao.md` §3 que hoje é inexequível
**Relacionada:** ADR-002 (integração por API ou evento), ADR-010 (saga), ADR-022 (remuneração no vínculo), ADR-031 (matriz de eventos)
**Precisa existir antes do marco 6** — é quando o fechamento de expediente ganha código

## Contexto

O `liquidacao.md` §3 guarda a transição que inicia o acerto:

| De | Para | Guarda |
|---|---|---|
| `ABERTA` | `EM_CONFERENCIA` | **Nenhum pedido do entregador em `SAIU_PARA_ENTREGA` ou `NAO_ENTREGUE`** |

A guarda existe por um motivo direto: conferir o caixa do entregador enquanto ele
ainda tem dinheiro de um pedido na rua produz uma **divergência que não é
divergência**. O número acusa falta, o entregador não errou, e o produto passa a
mentir exatamente na métrica que ele existe para produzir (invariante 10 do
`CLAUDE.md`).

### O `settlement` não tem como saber nenhum dos dois estados

```
T16  →  SAIU_PARA_ENTREGA   publica PedidoSaiuParaEntregaV1  →  só conversation
T20  →  NAO_ENTREGUE        publica —
T21  →  SAIU_PARA_ENTREGA   publica —
```

As duas transições que entram e saem de `NAO_ENTREGUE` **não publicam nada** — a
coluna de efeito das duas é um traço na tabela do `pedido.md` §3. E o único
evento que anuncia a saída para entrega não chega ao `settlement`.

A guarda está escrita como se fosse estrutural. Hoje ela é uma frase.

### Isto não apareceu antes por um motivo que vale registrar

Nenhum dos dois documentos está errado isoladamente. O `pedido.md` publica o que
os consumidores conhecidos pediram; o `liquidacao.md` escreveu a regra correta de
negócio. **O defeito só existe no cruzamento**, e o cruzamento não tinha dono até
a matriz da ADR-031 existir.

## Decisão

### 1. A guarda vira uma pergunta síncrona ao `order`, no instante da transição

```
comerciante inicia o acerto
        ↓
settlement pergunta ao order:
    "este entregador tem pedido em SAIU_PARA_ENTREGA ou NAO_ENTREGUE
     neste estabelecimento, agora?"
        ↓
resposta sim  →  transição recusada, com a lista dos pedidos
resposta não  →  ABERTA → EM_CONFERENCIA
```

Uma porta, um método, uma resposta. Nenhum evento novo.

### 2. Por que perguntar é melhor que escutar, aqui

**A guarda é avaliada num instante, não continuamente.** Ela vale uma vez por
jornada, no momento em que uma pessoa clica em "iniciar acerto". Manter uma
projeção contínua para responder uma pergunta pontual é construir um cache para
uma leitura por turno.

**Projeção pode divergir, e o modo de falha é o pior possível.** Se um evento se
perder, a projeção do `settlement` fica com um pedido a menos em trânsito — e a
guarda **passa quando não devia**, em silêncio, que é exatamente o defeito que ela
existe para impedir. A pergunta síncrona ou responde a verdade ou falha
ruidosamente.

**O `order` já é a fonte da verdade do estado do pedido.** Duplicá-lo no
`settlement` cria um segundo dono de um fato — o mesmo que a ADR-023 recusou para
a liquidação e a ADR-031 acabou de recusar para nomes.

**Há precedente no próprio serviço.** O `vinculoSnapshot` é lido do
`merchant-service` na abertura da jornada, sincronamente, e congelado
(`liquidacao.md` §2). A política `responsabilizaEntregadorPorNaoLiquidado` vem
pelo mesmo caminho. Perguntar no momento certo já é como este serviço funciona.

### 3. A chamada síncrona é aceitável aqui, e não seria no caminho do pedido

O `CLAUDE.md` exige timeout em toda chamada entre serviços, e a ADR-023 aceitou
uma chamada síncrona no caminho do pedido só com disjuntor e caminho alternativo.
Aqui o cálculo é outro:

| | Caminho do pedido | Esta guarda |
|---|---|---|
| Frequência | toda venda | uma vez por jornada |
| Quem espera | o cliente, no meio da compra | o comerciante, que acabou de clicar |
| Se o outro serviço cair | não pode travar a venda | **pode travar**, e deve |

**Falha na consulta recusa a transição.** Não existe caminho alternativo, e é
deliberado: fechar às cegas é pior que não fechar. O comerciante espera o
`order-service` voltar e faz o acerto cinco minutos depois — a jornada não tem
pressa de segundos.

### 4. T20 e T21 continuam sem publicar evento

E agora isso está **decidido**, não esquecido. Nenhum consumidor precisa saber que
uma tentativa falhou: o `delivery` já modela tentativa como `Tentativa` dentro da
entrega (`entrega.md`), o `conversation` avisa o cliente por outro caminho, e o
`settlement` pergunta.

Se um dia alguém precisar, o evento nasce então — acrescentar consumidor a um
evento novo é o lado barato da ADR-027.

### 5. A porta pertence ao `settlement` e é implementada contra o `order`

Nome sugerido: `PedidosEmTransitoPort`, com uma operação —
`existePedidoEmTransito(entregadorId, estabelecimentoId)` devolvendo a lista, não
um booleano. A lista é o que a tela precisa mostrar: recusar sem dizer **quais**
pedidos travam o acerto transforma a guarda em obstáculo.

## Consequências

**Positivas**

- A guarda passa a ser exequível. Era uma frase; vira código verificável.
- Nenhum evento novo, nenhuma projeção nova, nenhuma tabela nova no `settlement`.
- Quatro declarações de consumidor que existiam só para alimentar essa projeção
  imaginária deixam de ser necessárias — ver `contracts/eventos.md`.
- O modo de falha vira ruidoso. Consulta que não responde recusa a transição, em
  vez de deixar passar com dado velho.

**Negativas**

- **O acerto depende do `order-service` estar de pé.** É acoplamento temporal
  novo, e assumido: o momento é humano, tolera espera, e a alternativa é fechar
  caixa sem saber.
- **Uma porta a mais para testar**, com o duplo de teste correspondente. Barato
  perto de manter uma projeção correta.
- **A resposta é uma foto do instante.** Se um pedido sair para entrega entre a
  consulta e a gravação da transição, a guarda passa. A janela é de
  milissegundos e o cenário exige o mesmo entregador saindo com pedido no
  segundo em que o comerciante inicia o acerto dele. Aceito, e registrado para
  não ser descoberto como surpresa.

## Alternativas consideradas

- **Publicar `PedidoNaoEntregueV1` e mandar `PedidoSaiuParaEntregaV1` ao
  `settlement`.** O reparo intuitivo, e o que eu ia fazer. Rejeitado no §2:
  projeção contínua para leitura pontual, com modo de falha silencioso na
  direção errada.
- **O `order` recusar a abertura da conferência.** Inverte quem guarda: o
  `order` teria de conhecer jornada e conferência, que são do `settlement`. Move
  a regra para longe do documento que a descreve.
- **Deixar a guarda como aviso na tela**, sem impedir. Rejeitada: uma guarda que
  não guarda é pior que nenhuma, porque quem lê o documento acredita nela.
- **Guardar em `SAIU_PARA_ENTREGA` apenas, ignorando `NAO_ENTREGUE`.** Reduziria
  o problema pela metade sem resolvê-lo — e `NAO_ENTREGUE` é justamente o estado
  em que o entregador está de volta à loja **com o dinheiro ou com a
  mercadoria**, que é quando o acerto parece possível e não é.

## Emenda que esta decisão provoca

`docs/dominio/liquidacao.md` §3 — a guarda deixa de ser uma condição solta e passa
a citar a porta e o comportamento em caso de falha. E a seção de eventos que o
documento ganha nesta mesma rodada registra que estes quatro **não** são
consumidos: `PedidoPagoV1`, `PedidoRetiradoV1`, `PedidoCanceladoV1` e
`PedidoSaiuParaEntregaV1`.
