# ADR-087 — A operação da loja e as condições do pedido, em duas consultas

- **Status:** aceita
- **Data:** 18/11/2026
- **Rodada:** M3-1
- **Relacionada:** ADR-086 (o marco 3 em fatias — esta é a fatia 1), ADR-020 (a taxa por área, e a
  `DeliveryQuotePort`), ADR-034 (resposta congelada não vem de cache), ADR-045 (o token de quem
  pediu), ADR-049 (a rota irmã), o `estabelecimento.md` §3 e o `pedido.md`

## Contexto

**O `estabelecimento.md` §3 declara a `OperacaoDoEstabelecimentoPort` com três campos:**
`OperacaoAtual(boolean aberto, TipoDeOperacao tipoDeOperacao, int maxEntregasSimultaneas)`, com
cache curto no consumidor. A Decisão 1 da ADR-086 pôs nela também os métodos, a política de troco,
o pedido mínimo e o desconto de retirada — campos que a porta não declara. **E o
`maxEntregasSimultaneas` não existe no domínio:**

| Fato | Reverifica com |
|---|---|
| Nenhum código do back nomeia `maxEntregasSimultaneas` | `grep -rn "maxEntregasSimultaneas" backend --include=*.java` — vazio em 18/11/2026; o mesmo comando com `"tipoDeOperacao"` acha o `Operacao` |
| O `pedido.md` lista o `ConfiguracaoOperacionalAlteradaV1` entre os eventos que o `order` consome, para modalidades, métodos e troco | `grep -n "ConfiguracaoOperacionalAlteradaV1" docs/dominio/pedido.md` |
| Nenhum serviço produz o `ConfiguracaoOperacionalAlteradaV1` | `find backend/services -path "*/src/main/*" -name "ConfiguracaoOperacionalAlteradaV1.java"` — vazio; com `"ExpedienteAlteradoV1.java"`, o mesmo comando acha um |

O que a T01 congela — métodos, mínimo, desconto, troco e a taxa da área — é resposta que **vira
valor gravado no pedido**. A ADR-034 já decidiu que resposta congelada não vem de cache, e uma
projeção mantida por evento é um cache.

## Decisão

> **O `merchant` responde em duas consultas internas: a operação da loja, que o consumidor pode
> guardar em cache curto, e as condições do pedido, que nenhum consumidor guarda.**

1. **A operação — `GET /internal/merchants/{estabelecimentoId}/operacao`.** Responde
   `estabelecimentoId`, `aberto` e `tipoDeOperacao`, e nada mais. `aberto` vem do
   `Estabelecimento.estaAberta`, que compõe horário e pausa num lugar só. É o que a T02 e a I8
   perguntam.
2. **As condições do pedido — `POST /internal/merchants/{estabelecimentoId}/condicoes-do-pedido`.**
   O corpo traz a modalidade e, na entrega, o bairro ou o CEP. Responde:
   - **modalidade que a loja não aceita** — `aceita: false`, e nada mais;
   - **retirada** — os métodos, o pedido mínimo (M17: sempre presente, zero é sem mínimo), o
     desconto de retirada e a política de troco;
   - **entrega numa área ativa** — os métodos, o mínimo, a política de troco, sem desconto (I12),
     e `atendida: true` com o nome da área e a taxa. **Zero é taxa**: a área entrega de graça;
   - **entrega fora de toda área ativa** — **200**, `atendida: false`, **sem taxa** (M11), e os
     nomes das áreas ativas, para a recusa dizer onde a loja entrega (ADR-020). É resposta, não
     erro de quem chamou;
   - **entrega sem bairro e sem CEP**, ou CEP que não é CEP — **400**, com a frase do tratador.

   **O bairro manda e o CEP é a alternativa**: a área é escolhida, e o CEP só pré-seleciona
   (ADR-020). Área inativa não cota e não entra na lista (ADR-020, *"as áreas ativas daquela
   loja"*). É o lado do `merchant` da `DeliveryQuotePort` da ADR-020.
3. **`POST` nas condições**, para que o bairro e o CEP fiquem no corpo, e não na URL nem em log de
   acesso. A rota não grava nada.
4. **A autorização é a da rota irmã, o `expediente-corrente`:** o token de quem pediu (ADR-045),
   vínculo ativo, nenhuma permissão, e **403 também para loja inexistente**. Códigos: 200, 400, 401
   e 403.
5. **O `ConfiguracaoOperacionalAlteradaV1` continua sem produtor.** No marco 3 o `order` não guarda
   cópia da configuração: o que a T01 congela vem das condições do pedido, perguntado no instante.

## Consequências

**Duas consultas onde a ADR-086 desenhava uma.** A T01 faz uma chamada a mais ao `merchant`, e
sem cache — é o preço de não congelar valor velho.

**A porta do `estabelecimento.md` fica com um campo sem código**, o `maxEntregasSimultaneas`, e o
documento diz isso ao lado da declaração.

**A lista dos bairros atendidos vai em toda resposta de entrega não atendida**, com o tamanho da
lista de áreas da loja.

## Alternativas consideradas

**Seguir o `pedido.md`, com o `order` consumindo o `ConfiguracaoOperacionalAlteradaV1` no marco 3.**
**Recusada:** congelar valor de uma projeção exige garantir que ela nunca fica velha, e evento
perdido a deixa velha em silêncio (ADR-034).

**Uma rota só, com tudo.** **Recusada:** mistura o que pode ter cache no consumidor com o que não
pode, e o consumidor teria de lembrar quais campos da mesma resposta valem por sessenta segundos.

**Não atendida como 404 ou 409.** **Recusada:** a loja existe e respondeu; *"não entrego aqui"* é
a resposta, e a ADR-020 manda vir com a lista dos bairros.

## Gatilho escrito

**O primeiro consumidor do rodízio de entregadores.** Aí o `maxEntregasSimultaneas` entra no
domínio do `merchant` e na resposta da operação, como a porta do `estabelecimento.md` o declara.

**O primeiro consumidor que precise de cópia local da configuração.** Aí o
`ConfiguracaoOperacionalAlteradaV1` ganha produtor no `merchant`, pelo outbox, e o consumidor que
o pediu documenta o que faz com ele (ADR-031).
