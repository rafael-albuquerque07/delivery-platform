# Domínio — Canal, conversa e interpretação

**Serviço:** `conversation-service` · **Status:** vigente (v1.1, 21/08/2026)
**Fontes:** PRD §5 (P4), PRD §6 E8 e E9, Resposta v1.1 §5.3 e §6, ADR-007, ADR-008, ADR-013, ADR-017, ADR-018, ADR-020
**Invariantes do `CLAUDE.md` que este documento detalha:** 2, 6, 7, 9
**Entra em dois tempos:** modo determinístico no marco 7, IA no marco 9

A premissa P4 diz que o pedido **nasce numa conversa**. Isso faz deste serviço a
fundação do produto, não um conector. E impõe a regra que organiza tudo o que
vem abaixo:

> **A conversa entende. O sistema decide.**

O `conversation-service` interpreta intenção e propõe identificadores. Preço,
disponibilidade, área, taxa e total vêm sempre do catálogo e do estabelecimento
(H9.1). Em nenhuma circunstância um valor cobrado do cliente passa pela
interpretação automática.

---

## 1. Agregados

```
Conversa  (raiz)
├── estabelecimentoId, contatoId
├── modo               DETERMINISTICO | ASSISTIDO | HUMANO
├── estado             ATIVA | AGUARDANDO_HUMANO | EM_ATENDIMENTO_HUMANO | ENCERRADA
├── janela             ultimaMensagemDoCliente, expiraEm
├── rascunhoDePedido   identificadores apenas — nunca valores
├── custo              mensagensEnviadas, tokensConsumidos, estimativa
├── Mensagem     [n]   somente-inserção
└── Escalonamento[0,n] motivo, momento, resolvidoEm

Contato  (raiz)
├── telefone, nomeInformado
├── estabelecimentosConhecidos
├── enderecosConhecidos  [n]
└── baseLegal            base legal, momento, versão do aviso
```

**`Contato` é raiz própria e vive por cima da conversa.** A memória do histórico
(H9.3) — "o de sempre?" — atravessa dezenas de conversas encerradas. Dentro da
conversa, essa memória morreria com ela.

**`rascunhoDePedido` guarda identificadores, nunca valores.** É a materialização
da regra do topo. Um `precoBase` guardado aqui seria um número fora do controle
do catálogo, e alguém acabaria somando.

---

## 2. Roteamento multiestabelecimento

Cada estabelecimento tem **seu próprio número** (H8.1). A mensagem chega e o
serviço precisa saber de quem ela é.

> **O estabelecimento é determinado pelo número de destino. Nunca por nada que o
> cliente envie.**

É a invariante 9 do `CLAUDE.md` aplicada ao canal: um identificador que veio de
fora não é contexto confiável. Se o roteamento aceitasse um código no corpo da
mensagem, qualquer pessoa conversaria com a loja de outra.

Contato é chaveado por `(telefone, estabelecimentoId)` para efeito de histórico.
O mesmo cliente na pizzaria e no mercadinho tem duas memórias — e não há tela em
que uma apareça na outra.

---

## 3. Modos, e por que o determinístico é a base

| Modo | O que responde | Entra em |
|---|---|---|
| `DETERMINISTICO` | Menu numerado, listas interativas, botões | Marco 7 |
| `ASSISTIDO` | Texto livre, áudio e localização interpretados | Marco 9 |
| `HUMANO` | Operador no painel | Marco 9 |

A ordem não é acidente de cronograma. O documento v1.1 §6 é explícito: a IA é
**camada, não fundação**, e o modo degradado existe desde o marco 7.

Consequência de projeto: `DETERMINISTICO` atende o pedido recorrente **de ponta
a ponta** (H8.2). Não é uma tela de erro — é um caminho completo, que continua
disponível para sempre. Toda vez que a interpretação falhar, o cliente cai nele
e conclui o pedido.

Quem construir a IA primeiro e o menu depois inverte isso, e o menu vira um
fallback pela metade que ninguém testa.

### Rebaixamento automático

`ASSISTIDO → DETERMINISTICO` quando: a interpretação falha duas vezes seguidas,
o provedor de interpretação está indisponível, ou o teto de custo da conversa
foi atingido (§7). O cliente não vê um pedido de desculpas — vê um menu que
funciona.

---

## 4. O que a interpretação pode e não pode

### Pode

Propor **intenção** e **identificadores**: este produto, estas opções, este
bairro, esta forma de pagamento declarada.

### Não pode

| Proibido | Por quê |
|---|---|
| Calcular ou informar preço, taxa ou total | H9.1 — valor vem do catálogo e do estabelecimento |
| Confirmar pedido | §5 — confirmação é botão |
| Aceitar imagem como comprovante | Invariante 6 do `CLAUDE.md` |
| Responder sobre alergia, restrição ou ingrediente | §6 — escalona, sempre |
| Afirmar disponibilidade sem cotar | O catálogo é a verdade, e ele muda |
| Inventar identificador | §4.1 |

### 4.1 Validação da saída — a fronteira de confiança

Tudo que a interpretação devolve é **entrada não confiável**, do mesmo modo que
um corpo de requisição HTTP. Antes de qualquer uso:

```
∀ produtoId proposto  : existe ∧ pertence a este estabelecimento ∧ está ATIVO
∀ opcaoId proposto    : pertence ao produto proposto
∀ areaId proposto     : pertence a este estabelecimento ∧ está ativa
quantidade            : inteiro ≥ 1, dentro do teto configurado
```

Identificador que não passa é descartado em silêncio para o modelo e vira um
pedido de esclarecimento para o cliente — **nunca** um erro técnico na tela dele.

Depois disso, os identificadores vão para a cotação do catálogo (ADR-018), que é
quem produz os números. A interpretação nunca vê um `Money` de saída antes de o
catálogo tê-lo calculado.

---

## 5. Confirmação — a regra que não tem exceção

> **O compromisso do cliente é um toque em botão interativo com identificador
> conhecido. Nada mais.**

Não valem, em nenhuma hipótese:

- reação com emoji (H8.1, explícito);
- texto livre interpretado como "sim" — "pode ser", "acho que sim", "tá bom"
  não são compromisso, e um modelo que os classifica como tal cria pedidos que o
  cliente não fez;
- áudio;
- silêncio depois de um resumo.

O resumo antes da confirmação mostra **itens, opções, endereço, taxa da área e
total**, todos vindos da cotação — ou, na retirada, **itens, opções e total com
o desconto de retirada nomeado** (ADR-024), sem endereço nem taxa (I10). O
botão carrega um identificador do rascunho e
uma marca da cotação; se a cotação envelheceu, reconfirma-se com os números
novos (ADR-018 — `PRICE_CHANGED`), nunca se cobra a diferença em silêncio.

Só depois disso o `order-service` cria o pedido em `RECEBIDO`.

---

## 6. Escalonamento para humano

Quatro gatilhos (H9.2):

| # | Gatilho | Como é detectado |
|---|---|---|
| G1 | Cliente pede atendente | Botão dedicado **e** detecção por texto |
| G2 | Duas tentativas sem resolver | Contador na conversa |
| G3 | Valor acima do teto configurado | Comparação numérica, no sistema |
| G4 | **Alergia, restrição alimentar ou ingrediente** | Ver abaixo |

### G4 é diferente dos outros

Os três primeiros toleram uma falha de detecção: o cliente insiste, o contador
sobe, o teto é aritmética. **G4 não tolera.** Uma resposta automática errada
sobre a presença de amendoim num produto é um risco à saúde de alguém.

Por isso G4 **não pode depender do modelo reconhecer o assunto**:

```
Piso determinístico  — lista de termos (alergia, alérgico, intolerância,
                       glúten, lactose, amendoim, castanha, "contém", "tem
                       algum", "posso comer"…) verificada ANTES da interpretação
                       e independente dela

Camada adicional     — o modelo também pode escalonar, e o faz por instrução
                       explícita. Ele soma; nunca substitui o piso.
```

O piso é grosseiro e vai escalonar conversas que não precisavam. **É o erro que
se prefere.** Um atendente lendo uma pergunta boba custa segundos; a outra
direção custa outra coisa.

Enquanto o `HUMANO` não existe (antes do marco 9), G4 responde com a
recusa honesta e o telefone da loja. Nunca com um palpite.

### Fila e devolução

A fila de atendimento humano aparece no painel com **tempo de espera e motivo**
(H9.2). A devolução ao modo automático é **explícita** — ato do atendente, nunca
timeout —, porque um cliente devolvido ao robô no meio de um assunto sensível
recomeça do zero e desiste.

### Fora do horário

Resposta honesta em vez de silêncio (H9.2). O estado de abertura vem do
`merchant-service` (`estabelecimento.md` §4), e a resposta diz **quando** a loja
abre. "Estamos fechados" sem horário faz o cliente perguntar de novo — e cada
pergunta custa (§7).

---

## 7. Custo — um requisito, não uma métrica

H8.3 pede fluxo com o menor número de turnos, custo por pedido concluído
acompanhado, e alerta quando uma conversa passa do teto.

O custo tem **duas naturezas**, e medir só uma esconde metade:

| Medidor | Unidade | Onde nasce |
|---|---|---|
| Canal | mensagem enviada | Provedor do WhatsApp |
| Interpretação | token | Provedor do modelo |

Uma conversa com poucos turnos e um modelo caro pode custar mais que uma com
muitos turnos e menu numerado. O teto configurável é sobre o **custo estimado
total da conversa**, não sobre a contagem de mensagens.

Ao atingir o teto: rebaixa para `DETERMINISTICO` (§3) e sinaliza no painel.
Nunca encerra a conversa — o cliente no meio de um pedido não pode ser
desligado por causa de orçamento.

> **Contexto de preço, verificado em 21/08/2026 e com prazo de validade curto.**
> A partir de **1º de outubro de 2026** as *service messages* dentro da janela de
> 24 h — hoje gratuitas — passam a ser cobradas, ao mesmo preço das mensagens
> utilitárias do país; fontes de terceiros indicam **US$ 0,0098 por mensagem no
> Brasil**. Templates utilitários dentro da janela também voltam a ser cobrados.
> O agente de IA da Meta é cobrado por **tokens**.
>
> A página oficial de preços da Meta que consegui ler **não confirma** essas
> datas nem esses valores — ela cita apenas uma política para "AI Providers"
> vigente desde 16/02/2026. Trate os números como indicativos e **reverifique na
> fonte oficial antes do marco 7**. Uma correção minha: em conversa
> anterior eu citei ~US$ 0,0068 e cobrança por token desde 01/08/2026; ambos
> parecem incorretos.
>
> O que **não** depende de reverificação: a partir de outubro, cada turno tem
> preço. O desenho de poucos turnos deixa de ser elegância e vira requisito.

---

## 8. A janela de 24 horas

Fora da janela de atendimento, a plataforma **não fala livremente** — só por
template aprovado. Isso não é detalhe de integração; muda o produto.

| Aviso | Dentro da janela | Fora dela |
|---|---|---|
| Pedido confirmado | Mensagem livre | — (sempre dentro) |
| Pedido pronto / saiu para entrega | Mensagem livre | Template aprovado |
| Recibo | Mensagem livre | Template ou e-mail |

Consequência: **todo aviso proativo do fluxo precisa de um template aprovado
desde o primeiro dia**, mesmo que quase sempre caia dentro da janela. Descobrir
isso quando o primeiro cliente demora quatro horas para responder é tarde — a
aprovação de template leva dias.

---

## 9. Notificação absorvida

O `notification-service` não existe (v1.1). O que restou dele:

| Canal | Onde ficou |
|---|---|
| Cliente | Aqui. É a conversa |
| Operador | **Não existe.** O painel resolve com consulta periódica |
| Entregador | Avisado pelo operador. É da casa (P2) |
| Recibo por e-mail | Adaptador de `CanalPort`, não serviço |

**Ninguém deve construir push para o operador.** O alerta sonoro e visual de
pedido novo (H4.1) é do painel, por polling. Volta a ser serviço quando houver
aplicativo nativo — não antes.

---

## 10. As três portas

O serviço tem canal, conversa e interpretação juntos, com três portas de saída
(v1.1 §5.3). A enumeração abaixo é interpretação minha — o documento diz "três
portas" sem listá-las.

```java
interface CanalPort {          // adaptadores: WhatsApp Cloud API, e-mail
    void enviar(Destinatario d, Conteudo c);
}

interface InterpretacaoPort {  // adaptador: provedor de modelo
    Proposta interpretar(Contexto ctx, Entrada e);   // devolve identificadores
}

interface OperacaoPort {       // catálogo, estabelecimento, pedido
    Cardapio cardapio(UUID estabelecimentoId);
    Cotacao cotar(UUID estabelecimentoId, List<ItemProposto> itens);
    UUID criarPedido(RascunhoConfirmado r);
}
```

`InterpretacaoPort` devolve `Proposta`, e `Proposta` **não tem campo de valor**.
A porta é onde a regra do topo deixa de ser recomendação e vira tipo.

---

## 11. Entrada do webhook

O endereço é público e a mensagem se repete (segurança do `CLAUDE.md`).

| Regra | Detalhe |
|---|---|
| Assinatura | Validada antes de qualquer processamento. Falhou, descarta |
| Idempotência | Chave: id da mensagem do provedor, em `processed_messages` |
| Resposta rápida | Confirma recebimento e processa assíncrono. Provedor com timeout curto reenvia, e reenvio custa |
| Ordem | Mensagens podem chegar fora de ordem. Ordenação é por carimbo do provedor, não por chegada |

---

## 12. LGPD

A conversa é o ponto do sistema com mais dado pessoal por metro quadrado:
telefone, nome, endereço completo, áudio de voz, às vezes localização.

| Regra | Detalhe |
|---|---|
| Identificação | Assistente virtual se identifica no primeiro contato e **responde honestamente se perguntado diretamente** (H9.3). Nunca afirma ser humano |
| Áudio | Transcrito e descartado — descarte imediato após a transcrição. Guardar exige consentimento próprio (ADR-013 §4) |
| Localização | Convertida em área e endereço textual. **Coordenada exata não é gravada nem logada** — o `CLAUDE.md` proíbe, e a P5 tornou desnecessária |
| Conteúdo | Conteúdo de mensagem **excluído** 180 dias após o encerramento da conversa; `Contato` excluído após 24 meses de inatividade (ADR-013 §3) |
| Log | Sem telefone, sem endereço, sem conteúdo de mensagem. `correlationId` e ids internos bastam. Retenção de 30 dias |
| Interpretação | O que sai daqui para o provedor de modelo é o mínimo. Nome e telefone não precisam ir |
| Exclusão | Pedido de exclusão apaga `Contato` e conteúdo; o pedido permanece, com o contato dissociado — obrigação fiscal e contábil não se apaga a pedido. O pedido permanece **anonimizado**, não excluído; o titular é informado do que foi conservado e por quê (ADR-013 §5) |

A última linha é a que costuma passar batido: exclusão de dado pessoal **não**
apaga pedido. São bases legais diferentes, e confundi-las cria tanto violação de
LGPD quanto problema fiscal. A ADR-013 é o lugar dessa decisão.

---

## 13. Persistência

Documental por decisão de aprendizado (ADR-017), e a forma ajuda: uma
conversa é uma árvore de mensagens lida inteira, cuja forma varia conforme o
canal.

| Aspecto | Regra |
|---|---|
| Esquema | Todo índice e validador nasce em `changeUnit` do Mongock — ADR-007. Nunca comando no shell |
| Transação | Replica set de nó único; sem ele não há transação multi-documento e portanto não há outbox — ADR-008 |
| String de conexão | Precisa de `?replicaSet=rs0`. Sem o parâmetro o driver conecta em modo avulso e a transação falha em runtime, não no boot |
| Índices mínimos | `(estabelecimentoId, contatoId)` · `(estabelecimentoId, estado)` para a fila de atendimento · `janela.expiraEm` |
| Idempotência | `processed_messages` com o id da mensagem do provedor — §11 |
| Retenção | Índice TTL sobre o conteúdo, com o prazo da ADR-013 §3. A retenção é imposta pelo banco, não por rotina que alguém precisa lembrar de rodar — e o índice nasce em `changeUnit`, como todo o resto |
| Binário | Nunca no documento. Áudio não é guardado (ADR-013 §4); mídia que precise persistir vai para o MinIO por referência |

---

## 14. Eventos

**Consome** — todos idempotentes, todos com `processed_messages`:

| Evento | De | Para quê |
|---|---|---|
| `PedidoRecebidoV1` | `order` | Confirmar ao cliente |
| `PedidoProntoV1` | `order` | Avisar retirada ou saída |
| `PedidoSaiuParaEntregaV1` | `order` | Avisar |
| `PedidoEntregueV1` / `PedidoRetiradoV1` | `order` | Recibo |
| `PedidoCanceladoV1` | `order` | Avisar com motivo |
| `DisponibilidadeAlteradaV1` | `catalog` | Parar de oferecer o que acabou |
| `ExpedienteAlteradoV1` | `merchant` | Responder aberto/fechado corretamente |
| `AreasDeEntregaAlteradasV1` | `merchant` | Lista de bairros |

**Publica:**

| Evento | Quando |
|---|---|
| `ConversaEscalonadaV1` | G1–G4 — alimenta a fila do painel |
| `CustoDeConversaExcedidoV1` | Teto atingido |

---

## 15. Invariantes

| # | Invariante | O que quebra sem ela |
|---|---|---|
| V1 | A interpretação nunca produz valor monetário | Cliente cobrado por número que um modelo inventou |
| V2 | Todo identificador vindo do modelo é validado contra o catálogo | Pedido com produto de outra loja, ou inexistente |
| V3 | Confirmação só por botão com identificador conhecido | Pedido que o cliente não fez |
| V4 | Imagem nunca confirma pagamento | Golpe do comprovante falso |
| V5 | G4 tem piso determinístico independente do modelo | Resposta automática errada sobre alergia |
| V6 | Estabelecimento vem do número de destino | Conversa com a loja errada |
| V7 | `DETERMINISTICO` conclui pedido de ponta a ponta | Falha do modelo vira loja parada |
| V8 | Webhook: assinatura validada e processamento idempotente | Mensagem forjada; pedido duplicado |
| V9 | Coordenada exata nunca é gravada nem logada | Violação de LGPD e do `CLAUDE.md` |
| V10 | Assistente nunca afirma ser humano | Quebra H9.3 e a confiança do cliente |
| V11 | Devolução ao automático é ato humano explícito | Cliente largado no meio de assunto sensível |
| V12 | Teto de custo rebaixa o modo, nunca encerra a conversa | Cliente desligado no meio do pedido |

---

## 16. O que este documento deliberadamente não decide

- **Provedor de interpretação.** A porta existe justamente para adiar isso.
- **Teto de valor de G3.** É configuração por estabelecimento, e o número inicial
  é decisão de produto.
- **Texto da persona** (H9.3) — nome, tom regional, saudação. É conteúdo.
- **Múltiplos atendentes humanos na mesma conversa** e transferência entre eles.
  Fora de escopo até existir demanda.
- **Recuperação de conversa interrompida por dias.** O rascunho tem validade, e
  qual validade não foi decidido. Interage com a janela de 24 h (§8).
