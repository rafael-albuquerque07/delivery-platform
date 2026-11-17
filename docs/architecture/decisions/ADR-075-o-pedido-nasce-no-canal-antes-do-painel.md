# ADR-075 — O pedido nasce no canal antes do painel

- **Status:** aceita · **emendada em 17/11/2026**: a primeira porta passa a ser o painel (ADR-083)
- **Data:** 18/10/2026
- **Rodada:** P-1
- **Relacionada:** PRD §3.1, §5, §10, §10.1 e §11; H4.1, H4.2, H8.1, H8.2 e H8.3; ADR-006 (o
  rascunho mora na conversa), ADR-013 (LGPD), ADR-018 (a porta do cardápio de quem compra),
  ADR-024 e ADR-034 (a cotação), ADR-037 (os webhooks), ADR-045 e ADR-054 (a identidade de
  serviço), e a seção *"A pergunta de produto que precede o marco 3"* do `docs/dividas.md`

## Contexto

O marco 3 é *"Receber e acompanhar pedidos numa tela"* (PRD §10). **Nenhuma história anterior
ao marco 7 cria pedido.** A única que cria é a H4.2, *"Como cliente, quero montar meu pedido e
saber o total antes de confirmar"*, e ela nasce na conversa, que é o canal do marco 7. O razão
registra isso desde a R-1 como pergunta de produto que precede o código do marco 3.

**E a resposta já estava numa premissa.** O §5 do `PRD.md` diz, na P4, que *"O pedido nasce
predominantemente em **conversa**"*, e a coluna ao lado diz o que acontece se ela deixar de
valer: *"O canal deixa de ser fundação e vira integração acessória"*. O canal, portanto, **nunca
foi ampliação**: ele é onde o pedido nasce, e a ordem dos marcos é que discordava da premissa.

*Isso muda o peso do que vem abaixo: trazer o 7a para antes do marco 3 não é mudar o rumo do
produto — é a ordem obedecer ao §5.* E duas frases do PRD **já contradiziam a P4 antes desta
decisão**: a do §10.1, *"Os marcos 7 a 9 ampliam o alcance"*, e a mitigação do §11, *"canal é
entrada, não fundação da operação"*. Consertá-las não é consequência desta ADR: é o PRD voltando
a concordar com o §5.

**E a premissa diz *predominantemente*.** O §3.1 registra a origem do pedido como *"WhatsApp
majoritariamente; telefone e balcão em menor volume"*. O canal é a fundação, não a única porta
— e é por isso que a história de lançamento manual existe.

Havia três caminhos: uma história nova em que o atendente lança o pedido; trazer parte do
marco 7 para antes do marco 3; ou reordenar os marcos. **A escolha, feita pelo Rafael em
18/10/2026, é a segunda.** Esta ADR escreve o que ela significa e o que ela custa.

## Decisão

> **Antes do marco 3 entra o mínimo do canal que faz um pedido nascer: o menu numerado
> determinístico, de ponta a ponta, até o pedido confirmado.**

### 1. Que parte do marco 7

O recorte proposto é o da H8.2 (*"menu numerado determinístico atende o pedido recorrente de
ponta a ponta"*), com o que a H8.1 e a H4.2 exigem dele:

- número próprio por estabelecimento;
- cardápio por lista interativa, com botões;
- endereço e forma de pagamento coletados na conversa;
- total discriminado antes de confirmar — itens, taxa de entrega, desconto (H4.2);
- confirmação por botão explícito, nunca por reação (H8.1);
- preço alterado entre montar e confirmar exige nova confirmação (H4.2).

**Fica no marco 7 como está:** a H8.3 inteira (custo do canal), salvo o que a pergunta 3 abaixo
decidir.

### 2. Os números não mudam; muda a ordem

O marco 7 se parte em **7a** — o recorte da §1 —, executado **antes** do marco 3, e **7b**, o
resto, no lugar de sempre. **Renumerar custaria 198 referências a "marco N" em 51 arquivos**
(`grep -rn -i -o "marco [3-9]\b\|marco 1[01]\b"` sobre `docs`, `CLAUDE.md`, `README.md`,
`contracts`, `backend` e `frontend/src`, em 18/10/2026), e cada uma é uma frase que
envelheceria.

## Consequências — e a maior parte é custo

**O §10.1 do PRD deixa de valer como está escrito.** Ele diz que *"ao fim do marco 6 existe
produto completo"* e que os marcos 7 a 9 *"ampliam o alcance"*. Com esta decisão, o canal deixa
de ser ampliação e passa a ser a única entrada de pedido do produto. O §10.1 precisa de emenda
na mesma alteração que aceitar esta ADR.

**O risco do §11 perde a mitigação.** *"Dependência de um único canal externo — interrupção
total do recebimento"* tem como mitigação *"painel e balcão continuam operando; canal é
entrada, não fundação da operação"*. Sem história de lançamento manual, o balcão e o telefone,
que o §3.1 diz existirem em menor volume, **não têm por onde entrar**: o canal vira fundação. Se
o canal cair, a loja não recebe pedido nenhum pelo sistema.

**O que vem junto, porque o canal não existe sozinho:**

- **a autenticidade entre serviços.** O `criarPedido` do `conversation` no `order` é, pela
  ADR-054, o primeiro caso real da identidade de serviço, e a decisão que a ADR-045 deixou em
  aberto passa a preceder o marco 3;
- **o webhook com assinatura** — a entrada `/api/v1/webhooks/**` que a ADR-037 registra como
  ausente;
- **a cotação ganha chamador**, que hoje não tem (ADR-034, ADR-024);
- **a porta do cardápio de quem compra** (ADR-018), que não existe em código;
- **a conversa como dado pessoal** (ADR-013), com retenção e exclusão desde o primeiro pedido;
- **um provedor e um número de WhatsApp de verdade** para a passagem humana do marco 3. A
  coluna *"receber e acompanhar pedidos numa tela"* passa a depender de alguém escrever numa
  conversa real.

**E o marco 3 encolhe em incerteza:** o pedido que o painel mostra passa a ter uma origem
escrita, e as histórias do marco 3 em diante param de esperar uma fonte que não existia.

## Alternativas consideradas

**O atendente lança o pedido no painel.** Uma história nova, com tela e regra de quem pode
lançar, e que manteria o §10.1 e a mitigação do §11 de pé — o balcão e o telefone teriam
entrada. **Não escolhida em 18/10/2026.** *Esta ADR registra que ela continua sendo a única
das três que cobre o pedido que chega por telefone, e que, sem ela, esse pedido não tem
história em marco nenhum.*

**Reordenar os marcos inteiros**, com o canal no lugar do 3. **Recusada pela §2:** o mesmo
efeito, com 198 frases envelhecendo.

## Perguntas que esta decisão não responde

1. **O recorte da §1 está certo?** Em especial: o 7a inclui endereço e pagamento na conversa,
   ou o primeiro pedido pode ser só retirada?
2. **Qual provedor e qual número** para desenvolvimento e para a passagem humana?
3. **O custo do canal (H8.3) pode esperar o 7b**, ou precisa de um teto desde o 7a?

**A pergunta 4 da proposta foi respondida** em 18/10/2026: a história de lançamento manual
**existe** — é a **H4.5** do `PRD.md`, e entra no marco 6. É ela que mantém de pé a mitigação do
§11.

## Gatilho escrito

**Nenhum.** As três perguntas acima **não são gatilhos**: a condição das três é decisão nossa,
e o teste do `README.md` das decisões é *"alguém que não seja nós pode fazer isso
acontecer?"*. Não pode. **O lugar delas é este corpo**, e elas se respondem na rodada que
escrever o 7a.

## Emenda de 17/11/2026 — a primeira porta passa a ser o painel

**A H4.5 entra com o marco 3, e o 7a vem logo depois dele** (ADR-083). A alternativa que esta
ADR não escolheu em 18/10 — o atendente lança o pedido no painel — passa a ser a primeira porta
por onde um pedido nasce. O corpo fica como está: ele registra a decisão que valeu de 18/10 a
17/11.

**A §1 continua valendo como o recorte do 7a.** O que muda é quando ele é construído, não o que
ele contém. **As três perguntas acima continuam abertas** — o recorte, o provedor e o número, e o
custo do canal no 7a.
