# ADR-020 — Taxa de entrega por área nomeada

**Status:** Aceita — 18/08/2026
**Substitui:** ADR-019 (cotação por distância geodésica)
**Relacionada:** ADR-009 (valores do pedido), premissa P5 do PRD

## Contexto

O comércio de bairro brasileiro não cobra entrega por quilômetro. Cobra por
bairro: R$ 6 no Centro, R$ 9 na Torre, R$ 12 em Boa Viagem, e não entrega em
Olinda. O dono define isso de cabeça, olhando o mapa mental que ele já tem da
própria clientela.

A decisão anterior (ADR-019) presumia precificação por distância e construía uma
porta com adaptador geodésico no MVP e cálculo de rota viária na evolução. Sob a
premissa correta, **não há distância a calcular** — e com ela caem
geocodificação, motor de rotas, mapeamento espacial e o serviço de
geolocalização como fundação do MVP.

O cliente continua precisando ver a taxa antes de confirmar. O que muda é como
ela é obtida.

## Decisão

### O estabelecimento é dono das suas áreas

O `merchant-service` mantém, por estabelecimento, uma lista de áreas atendidas:

```
AreaEntrega
├── merchantId
├── nome              "Boa Viagem", "Centro"
├── identificadores   bairro normalizado e/ou faixas de CEP
├── taxa              Money  — zero é valor válido
└── ativa             boolean
```

Dezenas de linhas por loja, mantidas pelo próprio comerciante. Nenhum polígono,
nenhum tipo espacial, nenhum provedor externo.

### A cotação continua atrás de uma porta

O `order-service` declara `DeliveryQuotePort` em `application/port/out`, e a
implementação do MVP consulta a tabela de áreas do `merchant-service`:

```java
public interface DeliveryQuotePort {
    DeliveryQuote quote(UUID merchantId, EnderecoEntrega endereco);
}
```

A porta permanece porque é ela que permitirá reintroduzir cálculo de rota no
marco 11, se surgir operação que justifique, sem alcançar o domínio.

### Resolução do endereço para área

Não depende de geocodificação. A área é **escolhida**, não deduzida:

- no canal de conversa, o cliente seleciona o bairro numa lista de opções — as
  áreas ativas daquela loja;
- na aplicação web, o mesmo, por seletor;
- o CEP, quando informado, pré-seleciona a área e o cliente confirma.

Endereço fora de área é recusado **no momento do pedido**, com mensagem clara e
a lista de bairros atendidos — nunca com erro genérico.

### Congelamento

O pedido congela `nomeAreaSnapshot` e `taxaSnapshot` no fechamento, junto com o
endereço textual. Se o comerciante reajustar a taxa daquele bairro depois, o
pedido antigo não muda.

**Taxa zero é distinta de área não atendida.** Zero significa "entrego de graça
aqui"; ausência significa "não entrego aqui". Modelar as duas como o mesmo valor
nulo é o tipo de acoplamento implícito que quebra meses depois.

## Consequências

**Positivas**

- Elimina do MVP: geocodificação, motor de rotas, mapeamento espacial, tipos
  geográficos e o `geolocation-service` como fundação.
- A taxa passa a ser exatamente o que o comerciante já cobra — nada de explicar
  a ele por que o sistema calculou R$ 7,43.
- Cotação instantânea, sem chamada a provedor externo, sem cota e sem timeout.
- Área e taxa são configuração do comerciante, não regra da plataforma.

**Negativas**

- **Bairro é impreciso.** Ruas longas atravessam bairros, e o cliente pode
  escolher errado — por desconhecimento ou para pagar menos. Mitigação: o
  endereço textual completo fica visível ao comerciante antes da confirmação, e
  ele pode ajustar a área ou recusar.
- **Não cobre o caso do endereço distante dentro do mesmo bairro.** Aceito: é o
  mesmo comportamento que o comerciante já pratica hoje.
- Exige que o comerciante cadastre suas áreas no onboarding. Mitigação: começar
  com uma área única e taxa única é caminho válido e leva trinta segundos.

## Alternativas consideradas

- **Distância geodésica (ADR-019).** Rejeitada: precifica por um critério que o
  comerciante não usa, e exige que ele entenda um número que não controla.
- **Rota viária real.** Rejeitada para o MVP: exige provedor externo com cota,
  latência e custo, para produzir uma precisão que a premissa não pede. Volta no
  marco 11 se houver operação que justifique — e a porta existe justamente para
  isso.
- **Taxa única por estabelecimento.** Mais simples ainda, e permanece disponível
  como caso particular (uma área cobrindo tudo). Rejeitada como padrão porque
  perde a granularidade que a PME de fato pratica, e é justamente essa
  granularidade que faz a taxa parecer "certa" para o cliente.
