# Teste de balanceamento do legítimo interesse

**Status:** rascunho de engenharia · **Base:** ADR-013 §2
**Preparado em:** 24/08/2026

> **Isto não é um parecer jurídico.** É o registro estruturado que a engenharia
> consegue produzir — o que se trata, para quê, por que não haveria forma menos
> invasiva, e quais salvaguardas existem. A conclusão de cada teste está marcada
> como **preliminar** e precisa de revisão por advogado antes do primeiro cliente
> real. Onde a lei é citada, é por artigo, para que a revisão saiba onde olhar.

A ADR-013 escolheu **execução de contrato** como base para quase tudo, e
legítimo interesse para duas coisas apenas. Legítimo interesse é a base mais
frágil das três: exige este teste documentado, e é a que pode não sobreviver à
revisão. Este documento existe para que essa revisão tenha o que revisar.

---

## Índice das duas hipóteses

| # | Tratamento | Finalidade | Se a revisão não aceitar |
|---|---|---|---|
| **A** | Prevenção a fraude e abuso | recusar trote e reincidência | o produto perde a defesa contra prejuízo direto — precisa de outra base ou de outro mecanismo |
| **B** | Histórico do cliente no canal | "o de sempre?" | **o recurso é desligado.** Foi construído isolado exatamente para isso |

A diferença entre as duas colunas da direita é o motivo de a ADR-013 já ter
mandado construir B isolado.

---

# A — Prevenção a fraude e abuso

## A.1 Teste de finalidade — o interesse é legítimo?

**Finalidade.** Impedir que um pedido falso seja produzido e despachado. No
modelo deste produto o pedido é **preparado antes de qualquer pagamento** (P1,
ADR-010): a comida existe antes de o dinheiro entrar. Um trote não é
inconveniência — é prejuízo direto e imediato do comerciante, em insumo e em
tempo de forno.

**Registro no próprio produto.** O PRD §11 lista "trote no pedido presencial"
como risco, com mitigação de confirmação ativa, teto de valor para cliente sem
histórico e bloqueio por reincidência.

**Dados tratados.** Telefone do consumidor, histórico de pedidos recusados ou não
liquidados naquele estabelecimento, e a contagem de reincidência. Nenhum dado
novo é coletado para esta finalidade — todos já existem por execução de contrato.

**Conclusão preliminar.** Interesse legítimo e concreto, com prejuízo
identificável e mensurável. É a hipótese mais forte das duas.

## A.2 Teste de necessidade — existe forma menos invasiva?

| Alternativa | Por que não substitui |
|---|---|
| Exigir pagamento antecipado de todos | contraria P1 e o modelo do produto inteiro; o cliente do bairro paga na porta |
| Confirmação ativa apenas, sem histórico | cobre a primeira vez; não cobre a reincidência, que é o caso caro |
| Bloqueio manual pelo comerciante | é o que ele já faz de cabeça, e é o que o produto promete substituir |

**Conclusão preliminar.** O tratamento é necessário para a finalidade, e o volume
de dado é o mínimo — a contagem e o identificador, não o conteúdo.

## A.3 Balanceamento — o interesse prevalece?

**Expectativa do titular.** Quem faz um pedido a uma loja espera razoavelmente
que ela se lembre de um trote anterior. É o que qualquer comércio de bairro faz
sem sistema nenhum.

**Impacto sobre o titular.** Recusa de venda, ou teto de valor. Não há
consequência fora do estabelecimento: o dado **não atravessa a fronteira da
loja** (P3 e a invariante 9 do `CLAUDE.md`), então um bloqueio numa pizzaria não
alcança o mercadinho.

**Salvaguardas.**

- escopo por estabelecimento — nunca uma lista compartilhada entre lojas;
- sem enriquecimento com dado externo, sem consulta a bureau;
- decisão **revisável pelo comerciante**, que pode liberar um cliente;
- retenção limitada à do contato: inatividade de 24 meses e o dado sai.

**Conclusão preliminar.** O interesse do controlador e do comerciante prevalece,
com impacto contido e reversível. **Ponto que a revisão precisa examinar:** se a
recusa automática configura decisão automatizada com efeito ao titular (art. 20),
e se por isso exige direito de revisão explícito.

---

# B — Histórico do cliente no canal

## B.1 Teste de finalidade

**Finalidade.** Reconhecer o cliente recorrente e oferecer o pedido habitual —
"o de sempre?" —, reduzindo o número de turnos de conversa.

**Duas coisas se somam aqui, e vale separá-las**, porque a segunda é mais forte
que a primeira:

- **Conveniência do cliente.** Ele não repete endereço e pedido a cada semana.
- **Custo do canal.** A partir de outubro de 2026 cada mensagem enviada tem
  preço. Menos turnos é requisito econômico do produto, não refinamento (H8.3, e
  o PRD §9 traz a conta).

**Dados tratados.** Telefone, nome informado, endereços conhecidos e o histórico
de pedidos daquele contato **naquele estabelecimento**.

**Conclusão preliminar.** Interesse legítimo, mas **de conveniência e de custo, não
de necessidade**. É a hipótese mais frágil das duas, e é honesto dizer isso aqui
em vez de deixar a revisão descobrir.

## B.2 Teste de necessidade

| Alternativa | Por que não substitui — ou substitui |
|---|---|
| Pedir tudo de novo a cada conversa | **substitui**, com custo: mais turnos, mais dinheiro, pior experiência |
| Guardar só o endereço, sem histórico de pedidos | **substitui parcialmente** — resolve a maior parte do atrito com metade do dado |
| Consentimento explícito no primeiro contato | **substitui**, e é a alternativa que a revisão pode preferir |

**Conclusão preliminar.** O tratamento **não é estritamente necessário** — existem
alternativas que funcionam. É a razão de a ADR-013 exigir que o recurso seja
isolado e desligável.

## B.3 Balanceamento

**Expectativa do titular.** Alguém que pede da mesma pizzaria toda semana espera
ser reconhecido. Essa expectativa é real e pesa a favor — é a mesma que ele tem
ao ligar e ouvir "oi, é do mesmo endereço?".

**Impacto.** Baixo em conteúdo, mas o dado é sensível em volume: endereço
completo e padrão de consumo ao longo do tempo.

**Salvaguardas já decididas na ADR-013.**

- escopo por `(telefone, estabelecimentoId)` — a memória de uma loja não aparece
  em outra;
- coordenada exata nunca gravada; endereço textual e bairro apenas;
- áudio descartado após a transcrição;
- ao provedor de interpretação vai **só o texto necessário**, sem nome e sem
  telefone, imposto pela assinatura da porta e não por disciplina;
- exclusão a pedido apaga contato, conversas e endereços conhecidos;
- inatividade de 24 meses e o contato sai sozinho.

**Conclusão preliminar.** Defensável, com as salvaguardas acima. **Frágil o
bastante para que a arquitetura já preveja o desligamento** — e é isso que a
ADR-013 mandou construir.

---

## O que a revisão jurídica precisa responder

1. **A hipótese A sobrevive?** E a recusa automática configura decisão
   automatizada (art. 20), exigindo direito de revisão explícito?
2. **A hipótese B sobrevive**, ou o histórico do canal exige consentimento?
3. Se exigir consentimento: ele precisa ser **granular** — separado por endereço
   e por histórico de pedidos — ou um só basta?
4. Este documento, com esta estrutura, **serve como registro** do teste de
   balanceamento, ou a forma esperada é outra?
5. A retenção de **24 meses de inatividade** para o contato é adequada à
   finalidade, ou é longa demais para a hipótese B?

---

## O que muda no sistema conforme a resposta

| Se a revisão disser | O que muda no código |
|---|---|
| A e B sobrevivem | nada |
| B exige consentimento | o recurso ganha coleta de consentimento e passa a poder ser revogado. **A estrutura já suporta**: `Contato` tem `baseLegal` com momento e versão do aviso |
| B não se sustenta | o recurso é **desligado**. Foi construído isolado para isso — o resto do produto não depende dele |
| A exige direito de revisão | o painel ganha a tela de revisão da recusa. Não toca modelo de dado |
| A retenção deve ser menor | é número de configuração; o índice de expiração muda |

**Nenhuma das respostas possíveis obriga migração estrutural.** Isso não é sorte:
é o efeito de a ADR-013 ter escolhido execução de contrato como base padrão e
isolado o que dependia da base frágil.
