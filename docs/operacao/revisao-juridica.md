# Dossiê para a revisão jurídica — proteção de dados

**Status:** preparado pela engenharia · **Base:** ADR-013
**Preparado em:** 24/08/2026 · **Necessário antes do primeiro cliente real**

> **O que este documento é.** A pauta de uma consulta. Reúne o que o sistema
> trata, o que a engenharia decidiu, com que raciocínio, e **o que muda no
> sistema conforme a resposta** — para que a revisão seja uma reunião com agenda
> em vez de um item que nunca começa.
>
> **O que não é.** Parecer, conclusão ou interpretação da lei. As citações de
> artigo servem para indicar onde olhar, não para afirmar o que dizem. Toda
> decisão marcada como "assumido" é padrão de engenharia esperando confirmação.

---

## 1. O sistema, para quem não o conhece

Plataforma de pedidos para comércio de bairro — pizzaria, hamburgueria,
minimercado. **O cliente do produto é o comerciante**, não o consumidor. O
consumidor faz o pedido pelo WhatsApp da loja, e o pagamento acontece
majoritariamente **na porta**, ao entregador, em dinheiro, cartão ou Pix.

Três consequências que importam para a análise:

- a plataforma **não custodia o dinheiro** na maioria das transações — ela
  registra quem recebeu, quanto e por qual método;
- o entregador é **funcionário da loja**, não autônomo de um pool;
- não há aplicativo de consumidor, não há cadastro, não há login do consumidor.
  Ele conversa num número de WhatsApp.

Estado atual: **nenhuma linha de regra de negócio implementada.** Não há cliente,
não há dado de ninguém. Este é o momento barato de responder.

---

## 2. Inventário — onde mora dado pessoal

| Serviço | Dado pessoal | Titular |
|---|---|---|
| identidade | nome, telefone, e-mail, hash de senha | usuário do sistema |
| estabelecimento | telefone de colaborador, dados do entregador, documento do estabelecimento quando MEI | colaborador, entregador, comerciante |
| pedido | nome, telefone, endereço textual do consumidor | consumidor |
| conversa | telefone, nome, endereços conhecidos, conteúdo de mensagem | consumidor |
| entrega | endereço em trânsito (derivado do pedido), identificação do entregador | consumidor, entregador |
| fechamento | identificação do entregador, valores de acerto | entregador |
| pagamento | identificador da transação — **nunca** número de cartão | consumidor |
| catálogo · roteador | **nenhum** | — |

**Dados que deliberadamente não são coletados:** coordenada geográfica exata
(guarda-se endereço textual e bairro), áudio de mensagem (transcreve-se e
descarta-se), número de cartão (guarda-se o identificador da transação), imagem
de comprovante.

---

## 3. Perguntas — bases legais

### 3.1 A base padrão é execução de contrato

**Assumido.** Pedido, endereço, telefone e entrega tratados por **execução de
contrato**, não por consentimento.

**Raciocínio.** Consentimento é revogável, precisa ser granular, e sua revogação
obriga a apagar. Usá-lo para o pedido significaria que um cliente poderia revogar
no meio da entrega e o sistema teria de se apagar. E produziria uma tela de
consentimento antes de pedir uma pizza.

**Perguntar.** A base está correta? Há alguma categoria da tabela do §2 que
exigiria outra?

**O que muda se a resposta for outra.** Muito. Seria o único item deste dossiê
com consequência estrutural — mudaria o modelo de dados e o fluxo do canal.

### 3.2 Duas hipóteses de legítimo interesse

**Assumido.** Prevenção a fraude e histórico do cliente no canal ("o de
sempre?"). O teste de balanceamento está escrito em
`docs/operacao/legitimo-interesse.md` e deve ser lido junto deste dossiê.

**Perguntar.** As duas se sustentam? A recusa automática por reincidência
configura decisão automatizada com efeito ao titular, exigindo direito de revisão
explícito?

**O que muda.** O histórico do canal foi construído **isolado, para poder ser
desligado**. Se não sobreviver, desliga-se o recurso e o resto do produto não é
afetado.

---

## 4. Perguntas — prazos de retenção

**Assumidos**, com o raciocínio de engenharia ao lado:

| O quê | Prazo | Por que este número |
|---|---|---|
| Pedido, itens, liquidação, ajustes | 5 anos | prescrição tributária e reparação de danos no CDC |
| Fechamento de expediente | 5 anos | idem, mais relação de trabalho |
| Conteúdo de mensagem de conversa | 180 dias após o encerramento | cobre disputa e suporte; além disso não serve a nada |
| Áudio | descartado após a transcrição | não se guarda o que não se precisa |
| Contato do consumidor | enquanto ativo + 24 meses de inatividade | quem não pede há dois anos não é cliente |
| Conta de usuário do sistema | vínculo + 5 anos | o nome de quem autorizou um ajuste precisa sobreviver ao desligamento |
| Log de aplicação | 30 dias | diagnóstico, não histórico |
| Backup | 30 dias rolando | §6 |

**Perguntar.** Os prazos são adequados? Algum é longo demais para a finalidade —
em especial os 24 meses do contato, que sustentam a hipótese mais frágil?

**O que muda.** Nada de estrutural. São valores de configuração e prazos de
índice de expiração no banco. **Trocar um número é uma linha.**

---

## 5. Perguntas — exclusão diante da obrigação fiscal

**Assumido.** Ao receber pedido de exclusão do consumidor, separam-se **os
campos, não os registros**:

```
Contato, conversas e endereços conhecidos  →  EXCLUÍDOS
Pedido                                     →  ANONIMIZADO
  nome, telefone, endereço textual         →  removidos
  bairro, valores, itens, liquidações      →  PERMANECEM
Documento fiscal emitido                   →  INTOCADO
```

**Raciocínio.** Os cinco anos não se aplicam ao pedido inteiro — aplicam-se ao
que precisa ser reconstruído: valores, itens, método liquidado, quem autorizou. O
nome e o telefone do cliente não são necessários para provar quanto foi vendido.

A anonimização é **irreversível e destrutiva no lugar**: não se guarda mapa de
pedido para titular, porque isso seria pseudonimização disfarçada.

**Perguntar.**

1. A separação por campo é aceitável, ou a obrigação fiscal alcança mais campos
   do que supomos?
2. **O titular precisa ser informado do que foi conservado e por quê** — a
   engenharia assumiu que sim, e o procedimento já manda fazer isso. Confirmar.
3. O bairro pode permanecer? Assumimos que não identifica ninguém.

**O que muda.** Se a obrigação fiscal alcançar mais campos, é uma lista diferente
no procedimento. Se alcançar menos, também. Não muda o modelo.

---

## 6. Perguntas — encarregado e canal do titular

**É o bloco que menos depende de engenharia e mais depende de decisão de
negócio.**

**Assumido.**

- O **encarregado** é o titular do negócio — é uma operação de uma pessoa.
- O **canal** é um endereço de e-mail dedicado, publicado na política de
  privacidade e no aviso do primeiro contato do WhatsApp.
- Todo pedido é **registrado** com data, canal e quem atendeu; o prazo corre
  desse registro. O procedimento existe em
  `docs/operacao/exclusao-de-titular.md`.
- **Prazo de resposta assumido: 15 dias corridos**, aplicado a todos os direitos.

**Perguntar.**

1. O encarregado precisa ser **pessoa física nomeada publicamente**, ou basta um
   canal institucional? Há alternativa para operação individual?
2. O contato publicado pode ser genérico, ou tem de ser nominal? *(Isto tem
   consequência pessoal para quem opera sozinho: publicar contato próprio numa
   página pública.)*
3. **Quinze dias vale para todos os direitos**, ou há prazos diferentes para
   confirmação de tratamento, acesso, correção, portabilidade e exclusão?
4. Existe formato exigido de resposta, ou basta responder pelo mesmo canal?
5. O aviso no primeiro contato do WhatsApp precisa de conteúdo mínimo definido?

**O que muda.** Um endereço, um texto e um número no procedimento. Nada de
código.

---

## 7. Perguntas — operadores e transferência internacional

**Assumido.** O provedor do modelo de linguagem é **operador**, não terceiro
qualquer, e exige contrato de tratamento. O que sai para ele é o mínimo: o texto
da mensagem, **sem nome e sem telefone** — imposto pela assinatura da porta, não
por disciplina de quem escreve.

O mesmo vale para o provedor de pagamento e para o provedor do canal de
mensagens.

**Perguntar.**

1. Que cláusulas o contrato de tratamento precisa ter, no mínimo?
2. Se o provedor do modelo ou o do canal processarem **fora do Brasil**, o que
   isso exige? Vira relevante ao escolher fornecedor, no marco 7.
3. O consumidor precisa ser informado de que a conversa passa por um provedor de
   interpretação?

**O que muda.** A pergunta 3 muda o texto do aviso. As outras duas mudam
contrato, não sistema.

---

## 8. Duas coisas que a engenharia já resolveu, e valem ser conferidas

**8.1 Todo dado pessoal é alcançável por identificador estável.** Nenhum dado
pessoal existe apenas dentro de texto livre; todo registro que contenha dado
pessoal referencia um identificador indexado do titular. Sem isso, exclusão vira
varredura de texto em oito bancos — lenta, incompleta e impossível de auditar.

É a restrição que mais custa no modelo de dados, e foi assumida antes da primeira
linha de código.

**8.2 Backup restaurado ressuscita o que foi apagado.** Os backups rolam em 30
dias, e o procedimento de restauração **obriga a reaplicar as exclusões
registradas** com data posterior à do backup, antes de o serviço voltar a
atender. É passo obrigatório do runbook, não observação.

**Perguntar.** As duas são suficientes, ou há obrigação adicional sobre backup
que não estamos vendo?

---

## 9. O que existe e o que não existe hoje

| | Estado |
|---|---|
| Decisão sobre base legal, prazos e exclusão | **escrita** — ADR-013 |
| Procedimento de exclusão de titular | **escrito**, manual e auditado |
| Teste de balanceamento | **rascunho**, `docs/operacao/legitimo-interesse.md` |
| Encarregado nomeado | **não existe** |
| Canal do titular publicado | **não existe** |
| Política de privacidade | **não existe** |
| Aviso no primeiro contato do canal | **não existe** |
| Contrato com operadores | **não existe** — não há fornecedor escolhido |
| Automação da exclusão | **não existe**, por decisão: procedimento manual enquanto não houver demanda |

---

## Como usar este documento na reunião

Os blocos 3 a 7 são as perguntas, e cada um traz o que muda no sistema conforme a
resposta. **Só o bloco 3.1 tem consequência estrutural** — todo o resto é
configuração, texto ou contrato.

Isso não é acaso: a ADR-013 escolheu execução de contrato como base padrão e
isolou o que dependia de base frágil, justamente para que uma revisão jurídica
tardia não obrigasse a reescrever o sistema.
