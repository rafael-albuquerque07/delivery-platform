# Procedimento — recuperação de acesso ao estabelecimento

**Status:** esqueleto · **Base:** ADR-029
**Vale enquanto não houver fluxo automatizado.** Ver ADR-029, consequências.

> Os números deste documento — janela de espera, provas aceitas — são
> **propostas de partida**, marcadas como tais. Calibram-se com o primeiro
> cliente real, não antes.

---

## Antes de tudo: qual dos dois problemas é este

| Se o pedido é | Então é | E o caminho é |
|---|---|---|
| "esqueci minha senha" | recuperação de **conta** | canal verificado, autoatendimento. Não é este procedimento |
| "perdi o acesso à minha loja e não recebo mais o código" | recuperação de **estabelecimento** | este procedimento |

A distinção não é formalidade. Recuperar conta é provar que você é aquela
pessoa; recuperar estabelecimento é provar que o negócio é seu. **As provas são
diferentes e o risco é diferente.**

---

## Regra que não se negocia

> **Este procedimento nunca altera a credencial de uma conta.**

Ele cria ou promove um `Membro` naquele estabelecimento, e só. O mesmo usuário
pode administrar outras lojas (ADR-011), e resetar a senha dele daria acesso a
todas elas com base numa prova que valia para uma.

Se a pessoa também perdeu a própria conta, isso é outro atendimento, com a outra
prova.

---

## 1. Registrar o pedido

| Campo | Por quê |
|---|---|
| Quem pediu, e por qual canal | O prazo corre daqui |
| Qual estabelecimento | Um pedido, um estabelecimento — nunca "todas as lojas dele" |
| Qual dos quatro casos | (a) senha (b) canal perdido (c) administrador saiu (d) sem administrador alcançável |
| O que já foi tentado | Se o canal verificado não foi tentado, tente antes |

**Nada de conversa por canal não registrado.** Se o pedido chegou por telefone,
peça que ele venha pelo canal do titular e registre — o procedimento inteiro
depende de haver rastro.

---

## 2. A prova

O documento do estabelecimento **é público no Brasil**. Apresentá-lo não prova
nada, e aceitar só ele é uma porta aberta com aparência de fechadura.

Exige-se a combinação:

| | Prova | O que ela demonstra |
|---|---|---|
| **1** | Documento com foto do titular, batendo com o cadastro do estabelecimento | que a pessoa é quem diz ser |
| **2** | **Mais um** dos seguintes | que ela opera a loja |
| | controle do número do canal da loja — responde a um código enviado ao WhatsApp do estabelecimento | o número do negócio é dela |
| | origem do pagamento da assinatura — os últimos dígitos do cartão, ou o pagador do boleto | quem paga é quem pede |

Uma prova só do primeiro grupo **não basta**. Um ex-funcionário sabe o CNPJ; um
estranho também.

**Caso sem resposta boa:** o aparelho perdido levou junto o chip do negócio. Aí
sobra o documento e o pagamento, o julgamento humano pesa mais, e o prazo de
espera vale ainda mais. Registre explicitamente que foi este o caso.

---

## 3. Notificar, e esperar

**Imediatamente** ao aceitar a prova:

1. **Notifique todos os membros ativos** do estabelecimento, em todos os canais
   verificados. A mensagem **não carrega dado nenhum** da loja — diz que há um
   pedido de recuperação associado àquele contato, quando ele será executado, e
   como contestar.
2. **Espere.** Proposta de partida: **72 horas**. Durante a janela, qualquer
   membro ativo pode barrar, e barrar encerra o pedido.

A janela existe para dar tempo de o titular real reagir. Sem ela, a tomada de
conta é instantânea e irreversível — quem entra remove os outros.

**Contestação encerra o pedido.** Não se decide quem tem razão por mensagem:
havendo duas partes reivindicando a mesma loja, o procedimento para e vira
assunto entre elas, fora do sistema.

---

## 4. Executar

Nesta ordem de preferência:

| Situação | O que fazer |
|---|---|
| Há membro `ATIVO` | **Promover** um deles a `ADMINISTRADOR` |
| Não há membro ativo nenhum | **Criar** vínculo novo de administrador para o titular provado |

Promover é preferível porque a pessoa **já tinha acesso àquela loja** — a
diferença de risco é entre colaborador e administrador, não entre estranho e
administrador.

Confira depois: `M6` continua satisfeita, e o administrador anterior continua
registrado (não se apaga ninguém para "limpar").

---

## 5. Registrar a conclusão

Quem autorizou · qual prova foi aceita · o que foi feito · quando · se houve
contestação.

O registro é **somente-inserção**, como os ajustes de valor. E o nome de quem
autorizou **sobrevive ao desligamento** — anonimizado só depois dos cinco anos
(ADR-013 §5). Recuperação de acesso é registro de responsabilidade.

---

## A escrever antes do primeiro cliente

- **O prazo definitivo da janela** e o conjunto final de provas. Setenta e duas
  horas é proposta, não decisão calibrada
- Quem tem autoridade para executar, e o que acontece quando essa pessoa é a
  mesma que pediu
- Texto da notificação, que precisa avisar sem vazar
- O que fazer quando o pedido chega **junto** com uma exclusão de titular do
  mesmo contato — as duas operações se contradizem, e nenhuma das duas prevê a
  outra
- Fluxo no painel, quando houver volume. Hoje é console e julgamento
