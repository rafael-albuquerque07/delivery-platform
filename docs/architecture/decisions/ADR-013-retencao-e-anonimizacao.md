# ADR-013 — Retenção, anonimização e exclusão de dados pessoais

**Status:** Aceita — 23/08/2026
**Relacionada:** ADR-009 (valores do pedido), ADR-011 (autorização), ADR-020 (taxa por área), ADR-023 (fronteira com o PSP)
**Detalha:** `docs/dominio/conversa.md` §12, `docs/dominio/estabelecimento.md` §2
**Invariantes do `CLAUDE.md`:** 5 (total imutável), 8 (banco por serviço)
**Precisa existir antes do marco 7** — o canal acumula dado pessoal no primeiro dia em que liga

> **Isto é uma decisão de engenharia, não um parecer jurídico.** Os prazos
> abaixo são padrões de engenharia com justificativa, e precisam de **revisão
> por advogado antes do primeiro cliente real**. Onde a lei é citada, é por
> artigo, para que a revisão saiba onde olhar — não como afirmação de autoridade.

## Contexto

Este sistema guarda telefone, nome, endereço completo, áudio de voz e o conteúdo
de conversas de WhatsApp. É bastante dado pessoal para um produto que ainda não
tem um cliente.

Três perguntas precisam de resposta antes do código, porque respondê-las depois
custa migração de dados e reescrita de consulta:

1. **Com que base legal** cada categoria é tratada. A resposta determina se um
   titular pode simplesmente revogar e obrigar a apagar tudo.
2. **Por quanto tempo** cada coisa fica. Guardar para sempre é o padrão
   acidental de todo sistema, e é infração.
3. **O que acontece quando alguém pede exclusão** — e o pedido colide com a
   obrigação de guardar registro fiscal do que foi vendido.

A terceira é a difícil, e é onde a maioria dos sistemas erra para um dos dois
lados: ou apaga o que a Receita exige guardar, ou se recusa a apagar qualquer
coisa alegando obrigação fiscal que não cobre metade do que guardam.

## Decisão

### 1. Inventário: onde mora dado pessoal

Não é burocracia — sem isso, exclusão vira caça ao tesouro.

| Serviço | Dado pessoal | Titular |
|---|---|---|
| `identity` | nome, telefone, e-mail, hash de senha | usuário do sistema |
| `merchant` | telefone de colaborador, dados do entregador, documento do estabelecimento quando MEI | colaborador, entregador, comerciante |
| `order` | nome, telefone, endereço textual do cliente | consumidor |
| `conversation` | telefone, nome, endereços conhecidos, conteúdo de mensagem, áudio | consumidor |
| `delivery` | endereço em trânsito (derivado do pedido), identificação do entregador | consumidor, entregador |
| `settlement` | identificação do entregador, valores de acerto | entregador |
| `payment` | `txid`, dados do PSP — **nunca** número de cartão | consumidor |
| `catalog` · `gateway` | **nenhum** | — |

O `catalog` não guardar dado pessoal é decisão, não acaso: cardápio é dado de
negócio, e mantê-lo assim deixa um serviço inteiro fora do escopo de exclusão.

### 2. Base legal: execução de contrato, não consentimento

| Categoria | Base legal (LGPD art. 7º) | Por quê |
|---|---|---|
| Pedido, endereço, telefone, entrega | **Execução de contrato** (V) | Não se pede consentimento para entregar a pizza no endereço que a pessoa deu |
| Registro fiscal e liquidação | **Obrigação legal** (II) | Guardar não é escolha |
| Conta de usuário do sistema | Execução de contrato (V) | Colaborador, comerciante, entregador |
| Prevenção a fraude e abuso | **Legítimo interesse** (IX) | Exige registro de teste de balanceamento |
| Histórico do cliente no canal ("o de sempre?") | **Legítimo interesse** (IX) | Ver ressalva abaixo |
| Áudio guardado além da transcrição | **Consentimento** (I) | Por isso não guardamos — §4 |

> **Por que consentimento é a base errada como padrão.** Consentimento é
> revogável, tem que ser granular, e sua revogação obriga a apagar. Usá-lo para
> o pedido significaria que um cliente poderia revogar no meio da entrega e o
> sistema teria que se apagar. Execução de contrato é a base correta e a mais
> honesta: o dado é usado para fazer o que a pessoa pediu.

**Ressalva sobre o histórico do canal.** Legítimo interesse é a base mais frágil
das três e exige teste de balanceamento documentado. O "o de sempre?" é
conveniência, não necessidade — se a revisão jurídica não aceitar, o recurso cai
sem afetar o resto do produto, e é assim que ele deve ser construído: **isolado**,
para poder ser desligado.

### 3. Prazos de retenção

| O quê | Prazo | Depois | Justificativa |
|---|---|---|---|
| Pedido, itens, liquidação, ajustes | **5 anos** | Anonimização do titular; valores permanecem | Prescrição tributária (CTN art. 173/174) e reparação de danos no CDC art. 27 |
| Fechamento de expediente | **5 anos** | Anonimização do entregador; valores permanecem | Mesma razão, mais relação de trabalho |
| Documento fiscal emitido | Prazo da legislação fiscal | Não se anonimiza | Art. 16, I — conservação por obrigação legal |
| Conversa: conteúdo de mensagem | **180 dias** após o encerramento | Exclusão | Cobre disputa e suporte; além disso não serve a nada |
| Conversa: áudio | **Descartado após a transcrição** | — | §4 |
| Contato: telefone, nome, endereços conhecidos | Enquanto ativo **+ 24 meses** de inatividade | Exclusão | Cliente que não pede há dois anos não é cliente |
| Conta de usuário do sistema | Enquanto o vínculo existir **+ 5 anos** | Anonimização | O nome de quem autorizou um ajuste precisa sobreviver ao desligamento |
| Log de aplicação | **30 dias** | Descarte | Diagnóstico, não histórico |
| Backup | **30 dias** rolando | Sobrescrito | §7 |

**Os cinco anos não se aplicam ao pedido inteiro.** Aplicam-se ao que precisa
ser reconstruído: valores, itens, método liquidado, quem autorizou. O nome e o
telefone do cliente **não** são necessários para provar quanto foi vendido — e é
por isso que a anonimização de §5 funciona.

### 4. Dado que simplesmente não é coletado

A forma mais barata de cumprir a lei é não ter o dado.

| Não guardamos | Em vez disso |
|---|---|
| Coordenada geográfica exata | Endereço textual e nome de bairro (P5, ADR-020) |
| Áudio da mensagem | Transcrição; o áudio é descartado após transcrever |
| Número de cartão | `txid` e NSU, no `payment` (ADR-023) |
| Imagem de comprovante como dado financeiro | Nada — imagem não confirma Pix (invariante 6) |
| Telefone e nome no provedor de interpretação | Só o texto necessário (§8) |

### 5. Exclusão anonimiza o operacional; o registro fiscal permanece

A colisão entre o art. 18 (direitos do titular) e o art. 16, I (conservação por
obrigação legal) se resolve **separando os campos, não os registros**.

Ao receber pedido de exclusão do consumidor:

```
Contato (conversation)         → EXCLUÍDO
Conversa e mensagens           → EXCLUÍDAS
Endereços conhecidos           → EXCLUÍDOS

Pedido (order)                 → ANONIMIZADO, não excluído
  ├── clienteNome              → "titular removido"
  ├── clienteTelefone          → nulo
  ├── enderecoTextual          → "endereço removido"
  ├── nomeAreaSnapshot         → PERMANECE   (bairro não identifica ninguém)
  ├── todos os valores         → PERMANECEM
  ├── itens e opções           → PERMANECEM
  └── liquidações e ajustes    → PERMANECEM

Documento fiscal emitido       → INTOCADO — art. 16, I
```

**O titular precisa ser informado disso.** A resposta ao pedido diz o que foi
apagado e o que foi conservado, com o motivo. Prometer exclusão total e manter
nota fiscal é pior que explicar.

**A anonimização é irreversível e destrutiva no lugar.** Não se guarda um mapa de
`pedidoId → titular` em outra tabela; isso seria pseudonimização disfarçada de
anonimização, e o dado continuaria sendo pessoal.

Para colaborador e entregador o desenho é o mesmo, com uma diferença: o nome de
quem **autorizou** um ajuste ou fechou uma jornada é registro de responsabilidade
e sobrevive ao desligamento — anonimizado só depois dos 5 anos.

### 6. Todo dado pessoal é alcançável por identificador estável

Esta é a decisão que mais afeta o código, e a mais fácil de violar sem perceber.

> **Nenhum dado pessoal existe apenas dentro de texto livre.** Todo registro que
> contenha dado pessoal referencia um identificador estável do titular
> (`usuarioId`, `contatoId`), indexado.

Sem isso, exclusão vira varredura de texto em todas as tabelas de todos os
serviços — que é lento, incompleto e impossível de auditar. Com isso, cada
serviço responde "o que eu tenho deste titular" com uma consulta.

Consequência prática: telefone dentro do corpo de uma mensagem de conversa é
inevitável, e por isso **o conteúdo da conversa é excluído, não anonimizado**.
Não se tenta limpar texto livre.

### 7. Execução: procedimento documentado no MVP, evento depois

Cada serviço é dono do seu banco (invariante 8), então exclusão atravessa oito
serviços. O desenho final é um evento `TitularSolicitouExclusaoV1` com
confirmação de cada serviço e acompanhamento de conclusão.

**No MVP isso não é construído.** Enquanto não houver cliente real, exclusão é um
**procedimento operacional escrito** em `docs/operacao/exclusao-de-titular.md`,
executado à mão contra cada banco. Automatizar antes de existir demanda é
construir uma saga para zero pedidos por ano.

O que **não** pode esperar é o §6 — o alcance por identificador. Sem ele, nem o
procedimento manual funciona.

**Backup é o ponto que todo mundo esquece.** A exclusão vale para o dado vivo. Um
backup restaurado ressuscita o que foi apagado. Por isso os backups rolam em 30
dias e o procedimento de restauração inclui **reaplicar as exclusões pendentes**
— é passo obrigatório do runbook, não observação.

### 8. Log e provedor de interpretação

**Log** nunca contém dado pessoal. Nem telefone, nem endereço, nem conteúdo de
mensagem, nem coordenada. `correlationId` e identificadores internos bastam para
diagnosticar. É regra do `CLAUDE.md` e aqui ganha prazo: 30 dias.

**O provedor do modelo é operador** (LGPD art. 39), não terceiro qualquer. Exige
contrato de tratamento, e o que sai daqui é o mínimo: o texto da mensagem, sem
nome e sem telefone (`conversa.md` §12). O `InterpretacaoPort` é onde essa
minimização é imposta por assinatura, não por disciplina.

## Consequências

**Positivas**

- Exclusão passa a ser executável em vez de aspiracional, porque §6 garante que o
  dado é encontrável.
- A colisão entre direito do titular e obrigação fiscal tem resposta escrita
  antes de alguém precisar dela sob pressão.
- Metade do problema desaparece por não coletar: sem coordenada, sem áudio, sem
  cartão.
- O `catalog` fica inteiramente fora do escopo, e o `gateway` também.

**Negativas**

- **§6 restringe o modelo de dados para sempre.** Toda tabela com dado pessoal
  carrega e indexa um identificador de titular, inclusive onde parecia
  desnecessário. É custo permanente pago para uma operação rara.
- **Anonimização destrutiva é irreversível.** Anonimizar por engano não tem
  desfazer. Exige confirmação explícita no procedimento e teste que prove que só
  os campos certos são atingidos.
- **Procedimento manual não escala e pode ser esquecido.** Aceito enquanto não há
  cliente; vira dívida com prazo no momento em que houver.
- **Legítimo interesse no histórico do canal é a base mais frágil** e pode não
  sobreviver à revisão jurídica. Por isso o recurso é isolado.
- **Prazos aqui são padrão de engenharia**, não conclusão jurídica. Se a revisão
  disser outro número, o número muda — mas a estrutura das decisões, não.

## Alternativas consideradas

- **Consentimento como base para tudo.** Rejeitada. Parece a opção mais
  respeitosa e é a pior: revogável a qualquer momento, o que tornaria o pedido em
  andamento juridicamente instável, e granular, o que produziria uma tela de
  consentimento antes de pedir uma pizza.
- **Exclusão total, apagando o pedido.** Rejeitada: destrói registro que a
  legislação fiscal exige conservar, e destrói a apuração de faturamento de um
  período fechado.
- **Recusar exclusão alegando obrigação fiscal.** Rejeitada, e é o erro mais
  comum na direção oposta. A obrigação cobre valor, item e data — não cobre nome,
  telefone e endereço.
- **Pseudonimizar em vez de anonimizar, guardando o mapa.** Rejeitada: com o mapa
  guardado, o dado continua sendo pessoal, e a "exclusão" seria encenação.
- **Construir a saga de exclusão já no MVP.** Rejeitada por ordem, não por
  mérito: é o desenho certo, e entra quando houver titular para exercer o
  direito. O que entra agora é o §6, sem o qual a saga não teria como funcionar.
- **Retenção de 10 anos para tudo**, alinhada à prescrição civil geral.
  Rejeitada: guardar mais do que se precisa é infração ao princípio da
  necessidade, não prudência.

## O que esta ADR não decide

- **Nomeação de encarregado (DPO)** e canal de atendimento ao titular — decisão
  de negócio, exigida antes do primeiro cliente.
- **Texto da política de privacidade** e do aviso no primeiro contato do canal.
- **Teste de balanceamento** do legítimo interesse: precisa ser escrito e
  guardado, não só citado.
- **Transferência internacional**, caso o provedor de modelo ou o PSP processem
  fora do Brasil. Vira relevante ao escolher fornecedor no marco 7.
- **Os prazos, definitivamente.** Ver o aviso no topo.

## Emendas que esta decisão provoca

- `docs/dominio/conversa.md` §12 ganha os prazos concretos e §15 deixa de listar
  a ADR-013 como pendente.
- `CLAUDE.md` ganha a regra do §6 — dado pessoal alcançável por identificador
  estável — que é restrição de modelagem, não de segurança.
- `docs/operacao/exclusao-de-titular.md` passa a existir, como esqueleto, antes
  do marco 7.
