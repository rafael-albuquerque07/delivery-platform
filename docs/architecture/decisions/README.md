# Registros de decisão (ADR)

| ADR | Decisão | Status |
|---|---|---|
| 001 | Monorepo para os oito serviços, o gateway e os contratos | ✅ aceita · **emendada** (26/09/2026: a regra existe no build; 29/09/2026: `frontend/` na árvore) |
| 002 | Um banco por serviço, sem exceção | ✅ aceita |
| 003 | ~~Mensageria híbrida RabbitMQ + MQTT~~ | ⛔ sem objeto — MQTT saiu do MVP (020, 021) |
| 004 | Um pedido pertence a exatamente um estabelecimento | ✅ aceita |
| 005 | ~~PostGIS e Redis GEO~~ | ⛔ sem objeto — geoprocessamento saiu do MVP (020) |
| 006 | O rascunho pertence à conversa; não existe carrinho | ✅ aceita |
| 007 | Mongock para versionamento de esquema no MongoDB | ✅ aceita · **emendada** (27/09/2026: `@EnableMongock`, DDL em `@BeforeExecution`, convenção de id) |
| 008 | MongoDB como replica set de nó único | ✅ aceita · **emendada** (27/09/2026: `withReplicaSet()` na Testcontainers 2; `spring.mongodb.uri` no Boot 4) |
| 009 | Modelo de valores do pedido | ✅ aceita · **emendada pela v1.1** · **emendada** (27/09/2026: `Money` em documento é texto) |
| 010 | Saga do pedido: pivô em `PRONTO`, pagamento fora da transação | ✅ aceita · **reescrita pela v1.1** |
| 011 | Autorização comercial: cache em processo, invalidação por evento, fail-closed | ✅ aceita · **emendada** (26/09/2026: a porta não recebe `usuarioId` — ADR-045; 28/09/2026: primeiro consumidor, sem cache até a G-B4; 29/09/2026: `topic` com fila exclusiva, e o cache existe; 17/10/2026: `CONFIGURAR_LOJA`, e por que ela é ampla; 19/10/2026: a permissão não alcança quem já era membro) |
| 012 | Roteamento do gateway por recurso, não por serviço | ✅ aceita · **emendada** (24/09/2026: limite de taxa e `correlationId` fora do marco 1; 30/09/2026: `/api/v1/me` compartilhado, a ordem decide; 16/10/2026: a tabela não acompanhou o gateway; 16/10/2026 (2): a tabela deixa de ser a lista, e o teste deriva do gateway) |
| 013 | Retenção, anonimização e exclusão de dados pessoais | ✅ aceita |
| 014 | Não adotar H2; Testcontainers como fonte de verdade | ✅ aceita · **emendada pela v1.1** · **emendada** (27/09/2026: todo contêiner prova que é o usado — `ConteinerDeVerdadeIT`) |
| 015 | Emitir JWT com `NimbusJwtEncoder` | ✅ aceita |
| 016 | Front-end mínimo antes da PWA completa | ✅ aceita · **emendada** (29/09/2026: o 15.A começa no marco 2) · **emendada** (30/09/2026: o TanStack Query sai do 15.A) |
| 017 | MongoDB como decisão de aprendizado | ✅ aceita |
| 018 | Snapshot de opções no item e cotação pelo catálogo | ✅ aceita |
| 019 | `DeliveryQuotePort`: cotação por distância geodésica | ⛔ **revogada** — ver 020 |
| 020 | Taxa de entrega por área nomeada (bairro / faixa de CEP) | ✅ aceita |
| 021 | Catálogo de serviços do MVP — oito serviços | ✅ aceita · **emendada** (24/09/2026: `merchant` sem Redis; 26/09/2026: `order` e `delivery` sem Redis; 27/09/2026: `catalog` sem o starter, motivo mantido) |
| 022 | A remuneração do entregador pertence ao vínculo | ✅ aceita |
| 023 | Fronteira `order` × `payment`: pedido é dono do registro | ✅ aceita |
| 024 | Desconto de retirada, não preço por modalidade | ✅ aceita · **emendada** (07/10/2026: o `cotar` é marco 3, e o prazo desta decisão vai com ele) |
| 025 | Fuso horário do estabelecimento e o dia operacional | ✅ aceita · **emendada** (26/09/2026: um evento de abertura por dia operacional — ADR-046) |
| 026 | Fila morta, retentativa e reprocessamento | ✅ aceita · **emendada** (29/09/2026: consumidor de invalidação não tem retentativa nem fila morta) · **emendada** (10/10/2026: recusa definitiva vai à fila morta na primeira entrega) |
| 027 | O que conta como mudança compatível num evento | ✅ aceita · **emendada** (17/10/2026: a exceção do §2, medida no primeiro valor novo num enum publicado) |
| 028 | Pedido mínimo por modalidade, sobre o subtotal dos itens | ✅ aceita |
| 029 | Recuperação do administrador único | ✅ aceita |
| 030 | Devolução não é estorno | ✅ aceita |
| 031 | Nome de evento é único no repositório | ✅ aceita |
| 032 | A guarda da conferência pergunta, não escuta | ✅ aceita |
| 033 | O estado da jornada é perguntado | ✅ aceita |
| 034 | Resposta que vira snapshot nunca é cacheada | ✅ aceita |
| 035 | Domínio em português, o resto em inglês | ✅ aceita |
| 036 | O telefone é o identificador de login | ✅ aceita |
| 037 | A emissão do access token: a chave, o tempo, e o que fica público | ✅ aceita · **emendada** (24/09/2026: cinco rotas públicas, lista viva na 044) |
| 038 | O `sub` vira `UUID` na borda, e o caso de uso não conhece o token | ✅ aceita |
| 039 | O contrato OpenAPI é gerado do código e congelado | ✅ aceita · **emendada** (06/10/2026: a lista de erros de cada rota é afirmada, e o `ContratoDeErrosIT` a prova) |
| 040 | Um módulo de tipos de valor, e a regra que impede ele de crescer | ✅ aceita · **emendada** (28/09/2026: a `Permissao` não entra — cada serviço nomeia o seu recorte) |
| 041 | Observabilidade sai do repositório, e a volta é por condição | ✅ aceita |
| 042 | O código de verificação do cadastro, e quem o entrega | ✅ aceita |
| 043 | O outbox e o relay | ✅ aceita · **emenda a 011** · **emendada** (29/09/2026: o relay espera a confirmação, e `mandatory` revela mensagem sem destino) |
| 044 | A cadeia de filtros do gateway, e o que passa sem token | ✅ aceita · **emenda a 012 e 037** · **emendada** (30/09/2026: `/actuator/gateway` sai da exposição) |
| 045 | A credencial entre serviços é o token de quem pediu | ✅ aceita · **emenda a 011** · **emendada** (28/09/2026: o primeiro caminho `/internal/`, e 403 contra 5xx no consumidor) · **emendada** (07/10/2026: o gatilho disparou falso, e a ADR-054 o substitui por dois) |
| 046 | Quem observa a abertura do expediente | ✅ aceita · **emenda a 025** · **emendada** (26/09/2026: o expediente é o do início da faixa; o evento significa "um expediente começou") · **emendada** (30/09/2026: a `OperacaoDoEstabelecimentoPort` não existia, e o §6 ganhou rota) |
| 047 | Onde o token do painel mora no navegador | ✅ aceita |
| 048 | O primeiro consumidor de evento: topologia, idempotência e falha | ✅ aceita · **emenda a 011, 026 e 043** · **emendada** (10/10/2026: o critério não exclui todos os outros consumidores; gatilhos rearmados) |
| 049 | O carimbo com a loja fechada | ✅ aceita · **emenda a 046** · **emendada** (06/10/2026: o que a marcação faz com o 409) |
| 050 | A documentação viva, e só no seu computador | ✅ aceita |
| 051 | A imagem carrega o jar, não o compilador | ✅ aceita |
| 052 | A versão no documento, e o 409 que ela cria | ✅ aceita |
| 053 | O contrato declara o que a rota recusa, e um teste prova | ✅ aceita · **emenda a 039** · **emendada** (10/11/2026: um motivo desta classe responde 409) |
| 054 | O gatilho da identidade de serviço não disparou | ✅ aceita · **emenda a 045** |
| 055 | O que o cliente mostra quando o servidor recusa | ✅ aceita |
| 056 | Recurso de outra loja é 404, e o 403 fica com um significado só | ✅ aceita |
| 057 | A idempotência natural por chave de domínio, e a segunda exceção à invariante 7 | ✅ aceita · **emenda a 048** |
| 058 | O limite do contêiner dimensiona o JVM, e a pilha sobe em grupos | ✅ aceita |
| 059 | A semeadura passa pelo agregado, e não pelas tabelas | ✅ aceita · **emendada** (10/10/2026: o terceiro semeador, e o vínculo; 16/10/2026: a condição era uma aproximação; 17/10/2026: a lista é estado, e estado não mora em ADR) |
| 060 | A primeira rota de escrita, e o que o agregado exige para a loja nascer | ✅ aceita |
| 061 | A loja escolhida mora na URL | ✅ aceita |
| 063 | O erro do catálogo diz de quem é a culpa | ✅ aceita |
| 064 | A categoria ganha persistência, e só criar e listar | ✅ aceita |
| 065 | O produto nasce por rota, e o cardápio é publicado por quem o monta | ✅ aceita · **emendada** (08/11/2026: a rota de tirar do ar nasce sem tela — ADR-078) |
| 066 | A referência do corpo que não resolve é pedido inválido | ✅ aceita |
| 067 | O cardápio ganha escrita, e a seção passa a significar alguma coisa | ✅ aceita · **emendada** (10/11/2026: o publicado ganha botão de volta — ADR-079) |
| 068 | A listagem mostra o rascunho, porque quem a chama é o painel | ✅ aceita |
| 069 | O convite chega pela mão de quem convida, e o token se lê uma vez | ✅ aceita |
| 070 | A tela do convite, e o segredo que já está no navegador de quem o recebeu | ✅ aceita · **emendada** (19/10/2026: uma ação, e ela é aceitar — ADR-076) |
| 071 | A lista tem um dono, e a cópia não é lista | ✅ aceita · **emendada** (16/10/2026: o terceiro item do gatilho não era gatilho; 16/10/2026 (2): os dois itens restantes são regra, e saem do razão) |
| 072 | A seção que o comando colhe, e o item que não era gatilho | ✅ aceita · **emendada** (16/10/2026: "condição nossa" tem duas espécies) |
| 073 | O horário ganha rota, sob uma permissão que não existia | ✅ aceita · **emendada** (17/10/2026: a irmã de leitura) |
| 074 | A tela do horário, e a tela que não valida o que o domínio decide | ✅ aceita · **emendada** (19/10/2026: marcar o que acabou não espera a varredura) |
| 075 | O pedido nasce no canal, e o 7a vem antes do marco 3 | ✅ aceita |
| 076 | O convite aceita antes de entrar | ✅ aceita |
| 077 | O grupo de opções e a opção ganham rota, dentro do produto | ✅ aceita · **emendada** (08/11/2026: o caminho de volta tem rota — ADR-078) |
| 078 | O produto sai do cardápio, sob a permissão que já existia | ✅ aceita · **emendada** (10/11/2026: o rascunho responde 409, e a atribuição estava errada) |
| 079 | A tela dos grupos, e o produto que sai do ar para mudar de forma | ✅ aceita |

**Setenta e seis escritas, duas sem objeto — estas com registro próprio desde
25/08/2026 —, nenhuma a escrever.**

## Onde mora o quê

**ADR** responde *por que* uma decisão foi tomada, com alternativas e
consequências negativas. É histórico: uma ADR aceita não é reescrita por gosto,
é emendada ou revogada com data.

**`docs/dominio/`** responde *qual é a regra hoje* — agregados, invariantes,
tabelas de transição, fórmulas de apuração. É estado corrente: sempre reflete o
que o código deve fazer agora.

**`docs/operacao/`** responde *como se executa* — runbook, procedimento sob
pressão.

Quando dois divergirem, a divergência é o defeito. Corrija na mesma alteração.

## Por onde começar a ler

Quatro decisões sustentam quase todas as outras. Quem chega no projeto lê estas
primeiro, nesta ordem:

| | Por quê |
|---|---|
| **004** — um estabelecimento por pedido | É a premissa de que cinco outras dependem |
| **002** — banco por serviço | Explica de onde vêm Saga, outbox e duplicação |
| **021** — catálogo de serviços | Diz quais serviços existem e por que dois saíram |
| **009** — modelo de valores | O dinheiro, que é o que o produto existe para acertar |

## Formato

Título · Status · Contexto · Decisão · Consequências (positivas e negativas) ·
Alternativas consideradas.

Uma ADR sem "alternativas consideradas" e sem consequências negativas é
decoração, não registro de decisão.

## A seção "Gatilho escrito" é colhida por comando

**O `docs/dividas.md` é derivado, e um dos dois comandos dele é
`grep -rn "Gatilho escrito"`.** Isso faz deste título **uma entrada de instrumento**: **cada
item escrito debaixo dele vira uma linha do razão.**

**Então cada item é uma condição que o mundo cumpre** — uma rota que nasce, um serviço que
ganha código, um volume que chega, uma pessoa que pergunta. **Duas coisas não entram ali:**

- **condição que depende de nós decidirmos.** São **duas espécies**, e só se separaram depois
  do censo da R-4, que pôs as duas na mesma categoria porque apareceram juntas na mesma ADR:
  - **pergunta não feita** — *"o dia em que existir comando que enumere X"*, *"quando
    decidirmos onde mora Y"*. A entrega é **a pergunta**, no mesmo pacote (50º defeito), e
    não a linha;
  - **instrução de permanência** — *"quando achar uma duplicação, escreva o ponteiro"*. Ela
    **nunca se paga**, porque vale para sempre: não é dívida, é regra. **O lugar dela é este
    arquivo**, e não o razão.

  **O teste que separa as duas:** *existe um dia em que isto deixa de valer?* Se não existe,
  é instrução, e o razão não é o lugar;
- **prosa** — ressalva, argumento, frase de efeito. **Vai no corpo.**

**O teste de uma linha:** *alguém que não seja nós pode fazer isso acontecer?* Se não, não é
gatilho.

**E item colhido não se apaga**, porque o corpo da ADR não se reescreve: a retirada é por
**emenda datada**, e o razão **estorna** a linha — ela fica, dizendo que não era gatilho e
apontando a emenda. **O cabeçalho do razão tem de continuar fechando** depois do estorno.

*A decisão é a ADR-072, e nasceu da ADR-071, que se contradisse em duas seções suas no dia em
que foi escrita.*

### Instruções de permanência que já valem

**Regra ou lista achada escrita num segundo lugar:** não se sincroniza. Escreve-se o
**ponteiro**, ou o **comando que a deriva** do dono (ADR-071). Quem achar, faz na hora — não
há rodada para isso, e não há linha de razão esperando.

**Lista transcrita num teste:** o teste passa a **comparar-se com o dono**, por igualdade de
conjunto nos dois sentidos, como o `RoteamentoIT` faz desde `0e545c0`. *Verificar não é
derivar: a comparação tem de ser total.*

## Emendas

**A ADR não se reescreve.** Quando uma decisão muda, ou quando o texto dela envelhece, a ADR
antiga ganha uma **emenda datada ao fim** — `## Emenda de dd/mm/aaaa — <o que mudou>` —, e o
corpo continua registrando a decisão **como ela foi tomada, inclusive quando estava errada**.

**Por que o corpo fica:** a ADR é registro, não manual. Apagar o erro troca um registro
verdadeiro por um conveniente, e some com a única coisa que ensina — o que se sabia na hora.

**Esta é a única redação desta regra no repositório** (ADR-071). O `README.md` da raiz aponta
para cá e não a repete.

**O que havia antes, e por que não é mais regra.** Este parágrafo mandava, até 16/10/2026, pôr
um **bloco de emenda no topo** e dizia que *"o texto abaixo do bloco é sempre o vigente"* — ou
seja, **mandava reescrever o corpo**, o contrário do que o `README.md` da raiz mandava. Foi
assim na **revisão v1.1, de agosto de 2026**, nas ADRs 009, 010 e 014, e **só nelas**. A v1.1
foi um evento datado; regra escrita para um evento que já passou não é regra. **O bloco no topo
daquelas três fica onde está** — o corpo não se reescreve, e isso vale também para um corpo que
carrega uma emenda na forma antiga.

**O status acompanha a emenda.** A 019 ficou marcada como "aceita" por três dias enquanto já
estava revogada em outro documento. Uma ADR emendada ou revogada diz isso na linha de status e
neste índice, na mesma alteração.

Quando uma ADR nova **corrige um documento publicado** (um PDF em
`docs/referencia/`, que não se reescreve), ela registra isso numa seção "Emenda
que esta decisão provoca" no fim. Ver 011, 013, 023 e 024.

**A base publicada passou a ser a v2** (24/08/2026). O documento de
arquitetura v2 já incorpora as emendas registradas nas ADRs 011, 013, 023 e
024 — as seções delas continuam valendo como histórico da correção, e não como
pendência. Emenda nova a partir daqui é contra o v2.

**Rótulo que muda sem que a coisa mude** — número de marco, nome de arquivo, nome de serviço,
número de seção — **é troca direta**, com o motivo na mensagem do commit. A ADR-016 foi o
primeiro caso.

**Emenda não termina no `grep`.** Varredura por padrão encontra texto que contradiz a decisão
nova. Não encontra o que está **ausente** — uma ressalva que falta, um campo com nome que virou
enganoso, um documento que deveria citar a ADR e não cita. Depois do `grep`, leia as seções que
tocam o mesmo dado que a decisão governa. Foi assim que a ADR-013 revelou `Contato.consentimento`
com nome da base legal que ela rejeitou, e o congelamento do endereço em `pedido.md` sem a
ressalva que torna a anonimização possível.

**Corpo de ADR é datado por construção; comentário de código afirma o presente.** Um número ou
um estado escrito no corpo de uma ADR registra **o que se sabia naquela data**, e não envelhece
por isso. O que envelhece é a frase que **sustenta algo que deixou de valer** — foi o caso da
ADR-038, que adiava uma decisão até o segundo consumidor existir, e continuou dizendo que ele não
existia depois que o mundo o trouxe.

**A pergunta, antes de emendar um número:** *o argumento que ele sustenta muda com o número?* Se
não muda, **o conserto é não fazer nada** — e isso se escreve, para o censo seguinte não refazer
o trabalho.

**Medido na E-5:** sete lugares escreviam a contagem das permissões do `merchant`. **Quatro são
corpo ou emenda de ADR** — a ADR-011, a ADR-040 em duas linhas e a ADR-073 — e **ficam como
estão**. **Três eram código** — o javadoc do `PermissaoDoCatalogo`, o do `MenuDoPainel` e a
mensagem de uma asserção do `ConsultarContextoDeAcessoServiceTest` —, e foram trocados no lugar,
sem número.

**Cópia dentro do dono não é duplicação.** Uma ADR pode dizer a mesma coisa no corpo e em duas
emendas de datas diferentes: isso é **histórico**, e **a emenda mais nova vence**. A regra da
ADR-071 — uma redação só — é entre **arquivos**. *Dentro de um, o que não se reescreve é
justamente o que envelheceu.*
