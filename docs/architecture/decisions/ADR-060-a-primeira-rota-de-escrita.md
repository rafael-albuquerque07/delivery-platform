# ADR-060 — A primeira rota de escrita, e o que o agregado exige para a loja nascer

- **Status:** aceita
- **Data:** 10/10/2026
- **Rodada:** E-1
- **Relacionada:** M7, M9/M10, **M12**, **M15**, **M17** (`estabelecimento.md`), ADR-011
  (autorização comercial), ADR-012 (roteamento por recurso), ADR-038
  (`SujeitoDoToken`), ADR-043 (outbox), ADR-053 (o contrato declara o que a rota
  recusa), ADR-055 (corpo de erro), ADR-059 (a semeadura)

## Contexto

**Em 10/10/2026 nem o `merchant` nem o `catalog` tinham uma única rota de escrita de
domínio.** A R-1 mediu: `Estabelecimento.novo`, `Membro.fundador`, `Produto.rascunho` e
`publicar()` são chamados **apenas por semeadores e por testes**. As duas marcações de
disponibilidade são as únicas escritas que um cliente alcança nesses dois serviços.

**O `identity` tem uma:** o `POST /api/v1/auth/signup` do `CadastroController`. É a
única rota do repositório que recebe um corpo e cria algo — e por isso **ela é o
precedente desta**, em validação e em forma de erro, não um detalhe de outro serviço.

O PRD §10 define cada marco por uma coluna — *"O comerciante consegue"* —, e a do marco
1 diz *"Cadastrar a loja e o time"*. **O marco 1 foi declarado fechado sem essa
coluna.** A H1.1 não tinha caminho.

Esta é a primeira rota de escrita do `merchant`, e ela existe porque o próprio domínio a
nomeou. O javadoc de `Membro.fundador` diz:

> *"Quem cria a loja e quando isso acontece é assunto da rodada que escrever o cadastro
> do estabelecimento; esta fábrica existe para que a loja nunca possa ser criada sem
> ele."*

Esta é essa rodada.

## Decisão

> **`POST /api/v1/me/estabelecimentos` cria a loja e o vínculo de fundador na mesma
> transação, registra o `VinculoAlteradoV1` no outbox antes de ela terminar, e responde
> **201 com o corpo, sem `Location`**.**

### 1. Ela é a única escrita do sistema sem autorização contra um vínculo

Toda outra rota confronta o `estabelecimentoId` da URL com o vínculo do portador
(invariante 9, ADR-011). **Esta não tem o que confrontar: o vínculo é o que ela cria.**
Basta token válido.

**E isso não é um buraco na M7.** A M7 diz que sem vínculo ativo a resposta é 403
idêntica a "não existe" — ela protege o que **já** existe de ser descoberto. Aqui não há
recurso a descobrir: a loja passa a existir por causa da chamada.

### 2. O corpo carrega o que o agregado exige — e o agregado já disse por escrito

A primeira versão desta ADR perguntava se a `Operacao` aceita mapas vazios. **A pergunta
tinha resposta na tabela de invariantes do `estabelecimento.md`, cinco linhas abaixo da
M7 que esta própria ADR cita.** Não aceita:

| Invariante | O que o construtor da `Operacao` exige |
|---|---|
| **M12** | `metodosPorModalidade` não vazio, e nenhum conjunto de métodos vazio |
| **M17** | `pedidoMinimoPorModalidade` com entrada para **toda** modalidade aceita e para nenhuma outra, toda entrada `≥ 0` |
| **M15** | `descontoDeRetirada` não negativo |

**A leitura que determina o corpo de uma rota de criação é a tabela de invariantes do
agregado, não uma pergunta ao construtor.** A tabela é o índice; o construtor é a
implementação dela.

**O corpo, então:**

- **`identificacao` e `politicaDeTroco`.** Não há fábrica de identificação vazia, e nem
  deveria haver — loja sem nome, sem documento e sem fuso não é loja;
- **`tipoDeOperacao` e `metodosPorModalidade`.** É a M12 falando: *"loja que não opera;
  ou modalidade que aceita pedido e nenhuma forma de pagar"*. Pedir isso no cadastro não
  é formulário inflado — é o mínimo que o agregado aceita.

**E o que não vem no corpo:**

- **`disponibilidade`: `Disponibilidade.semHorario()`.** A razão está escrita no domínio
  há semanas: *"Horário vazio é válido e significa 'nunca abre por horário'. **É o
  estado de uma loja recém-cadastrada, antes de o comerciante preencher a tela.**"*
  Pedir horário no cadastro contraria o que a `Disponibilidade` já decidiu;
- **`areasDeEntrega`: vazia.** A H1.3 — *"definir minhas áreas de entrega e taxas por
  bairro"* — é uma tela própria, e as M9/M10 são sobre duplicata e sobreposição **entre**
  áreas: com zero, não há o que violar;
- **`pedidoMinimoPorModalidade` e `descontoDeRetirada`: derivados em zero**, por uma
  fábrica nova no domínio — `Operacao.nova(tipoDeOperacao, metodosPorModalidade)`. Zero
  não é dado inventado: o javadoc da própria `Operacao` diz *"zero é valor válido e
  significa 'sem mínimo'"*, e loja recém-cadastrada não tem mínimo. **A M17 passa a ser
  satisfeita por construção** — as chaves dos mínimos saem das chaves dos métodos, e é
  impossível um cliente desalinhá-las.

### 3. Por que a operação vem no corpo e o horário não — e a simetria é falsa

As duas parecem o mesmo caso: configuração que o comerciante preenche depois. Não são.

**`semHorario()` fecha a loja.** O pior que um padrão assim faz é não vender, e o
comerciante descobre na primeira tentativa de abrir.

**Uma operação padrão abriria a loja.** Ela prometeria ao consumidor uma modalidade e
uma forma de pagar que o comerciante nunca escolheu — e pela **P1** o dinheiro não passa
pela plataforma: ele chega à porta, em espécie, com alguém esperando troco.

**A regra, e ela vale para toda rota de criação que vier:** estado inicial derivado só é
legítimo quando o erro dele é **recusar**. Zero é exceção, e por escrito.

### 4. O fundador nasce junto, e o evento também

`Membro.fundador` — `ADMINISTRADOR`, ativo, **todas** as permissões. Não é generosidade:
o javadoc da fábrica explica que papel não é lista de permissão, e que uma loja cujo
dono nasce com o conjunto vazio nasce inoperante com a A3 satisfeita no papel.

O `VinculoAlteradoV1` vai ao outbox por
`GerenciarEquipeService.registrarVinculoNascido`, **dentro da mesma transação** — o
método exige transação aberta (`MANDATORY`), e é o mesmo caminho que o aceite de
convite usa. Sem ele, um `catalog` que já tenha a resposta "sem vínculo" em cache
continuaria recusando o dono da loja que acabou de nascer, pela janela de 60 s medida na
W-D.

### 5. A resposta é 201 **sem** `Location` — e quem decidiu foi o gateway

A primeira versão desta ADR dizia *"201 com `Location`"*. **O `Location` apontaria para
fora do serviço.** O `application.yml` do gateway tem, escrito na G-B5:

> *"A ordem importa: este predicado é mais específico que o `/api/v1/me/**` logo abaixo
> […]. O vínculo entre pessoa e loja mora no merchant; **o resto do `/me` continua sendo
> do identity. Sem `/**` no fim: a rota é exata (G-B5)**."*

Logo `/api/v1/me/estabelecimentos/{id}` **não casa** com a rota exata: cai no
`/api/v1/me/**` e vai para o `identity`. O `RoteamentoIT` já prova esse caso, pelo nome
`/api/v1/me/estabelecimentos/qualquer-coisa`. E no `merchant` **não existe GET de uma
loja por id** — não há o que o cabeçalho prometesse, nem no serviço certo.

**O corpo é o `LojaDoUsuario`**, o mesmo record que o `GET /api/v1/me/estabelecimentos`
devolve — a mesma forma que o painel lê para montar o seletor e o menu. Devolver outra
faria a tela ter dois jeitos de ler a mesma coisa.

**Uma criação sem `Location` é incomum**, e aqui é consequência de uma decisão de
roteamento tomada de propósito. O recurso criado é alcançável: pela coleção que esta
mesma rota alimenta.

### 6. Uma pessoa pode ter várias lojas, e não há unicidade de documento

Já estava decidido, e está escrito no javadoc do `EstabelecimentoRepositorio`:
*"Buscar por documento não entra: não há unicidade de documento (uma pessoa pode ter
duas lojas) e ninguém pergunta."* **Esta rota não introduz 409 nenhum.**

## Consequências

**O contrato desta rota declara 201, 400 e 401, e mais nada** (ADR-053). Não há 403 —
não há vínculo contra o qual recusar — nem 404, nem 409.

**E o 400 precisa de quem o lance.** O `TratadorDeErros` do `merchant` tem dois
tratadores — `AcessoNegado` e `SemExpedientePorHorario` — e **nenhum transforma o
`IllegalArgumentException` do agregado em 400**. A ADR-053 exige um teste de integração
provando cada código declarado; um 400 declarado sem produtor reprova no
`ContratoDeErrosIT`, que é o lugar certo de reprovar. **A forma segue o precedente do
`POST /signup` do `identity`** — a única rota com corpo que o repositório já tem — e a
ADR-055 governa o corpo do erro.

**Como o 400 ficou, medido na E-1.** O corpo alcança regras que Bean Validation não
expressa — documento com 11 ou 14 dígitos depois de limpar, formato do telefone, fuso do
conjunto brasileiro (M16), modalidade sem método (M12), fundo de troco negativo. Então o
400 tem duas origens, as duas no padrão do `/signup`:

- **a forma**, por Bean Validation no `CriarEstabelecimentoRequest` — o que falta, o que
  vem em branco, o mapa vazio. O `/signup` faz igual (`@Valid` e `@NotBlank`), e o
  400 sai do mecanismo padrão do Spring;
- **o que só o agregado sabe**, por **um** ponto de tradução: a construção do agregado no
  `CriarEstabelecimentoService` captura as recusas dos objetos de valor e lança
  `CadastroDeLojaRecusado`, que o `TratadorDeErros` responde com 400 e a frase do
  agregado como `detail`. É o mesmo desenho do `/signup`, que trata
  `TelefoneInvalido` por tipo.

**Não há tratador de `IllegalArgumentException`**, e é de propósito: ele transformaria em
400 todo defeito do serviço que lançasse o mesmo tipo.

**O que os testes alcançam, medido por mutação.** Tirar o `registrarVinculoNascido`
derruba o caso 5 do teste de unidade, a linha do outbox no `CriarEstabelecimentoIT` e o
`CriarEstabelecimentoAtomicoIT`. Salvar o vínculo antes da loja derruba o caso 6 — pela
**ordem das chamadas**, que é o que um dublê sabe ver — e os dois casos de integração que
criam de verdade, com 500: a chave estrangeira está no banco, e só a integração a alcança.

**O marco 1 ganha metade do que lhe faltava.** O time — convite, aceite, papel,
permissão — continua sem rota: as sete operações do `GerenciarEquipeService` e o
`AceitarConviteService` ainda não têm controlador. *Está no `dividas.md`.*

**Os três semeadores continuam.** A ADR-059 diz que eles saem *"na rodada que der ao
`merchant` as rotas de escrita"* — e esta dá **uma**. O valor da fixture é uma loja
**com cardápio**, e o `catalog` continua sem rota de criação. **O gatilho não está
pago.**

**E a fixture não muda.** Ela precisa de um `estabelecimentoId` **fixo**, e
`Estabelecimento.novo` sorteia o id — por isso o semeador continua chamando
`reconstituir`. Esta rota não devolve ao domínio o corpo do `ApplicationRunner`.

## Alternativas consideradas

**O corpo carrega a loja inteira — horário, áreas, mínimos, desconto.** É o formulário
completo numa chamada. **Recusada** porque contraria o que a `Disponibilidade` já
decidiu sobre o estado de uma loja recém-cadastrada, e porque H1.2 e H1.3 são telas — e
telas são rotas.

**A rota preenche uma operação padrão (retirada, dinheiro, sem mínimo).** **Recusada
pela §3:** um padrão que abre a loja promete uma forma de pagar que o comerciante não
escolheu, e com dinheiro na porta pela P1. Também contraria o princípio desta ADR: quem
decide o mínimo é o agregado.

**Afrouxar a M12 para a loja nascer sem modalidade.** **Recusada:** a M12 existe desde o
início do domínio e diz o porquê — *"loja que não opera; ou modalidade que aceita pedido
e nenhuma forma de pagar"*. Nenhuma medição desta rodada a contestou; só a minha
suposição a contestava.

**`POST /api/v1/estabelecimentos`, fora do `/me/`.** Mais convencional. **Recusada
porque o `/me/` diz a verdade sobre esta rota:** ela não cria um recurso global, cria
*uma loja do portador* — e o resultado entra exatamente na coleção que o
`GET /api/v1/me/estabelecimentos` devolve.

**`Location` com um GET de loja por id.** Exigiria trocar o predicado exato do gateway
por prefixado. **Recusada:** a G-B5 tomou aquela decisão de propósito — *"o resto do
`/me` continua sendo do identity"* — e o `RoteamentoIT` prova o caso que a troca
quebraria. Convenção de 201 não paga o preço de reabrir uma decisão de roteamento.

**Criar a loja e deixar o vínculo para uma segunda chamada.** Seria duas transações e um
instante em que existe loja sem dono — e a fábrica `Membro.fundador` existe, por escrito,
*"para que a loja nunca possa ser criada sem ele"*.

**Exigir uma permissão para criar.** Não há onde: permissão é por loja, e a loja não
existe antes da chamada. Qualquer verificação aqui seria sobre outra loja, que é
exatamente o que a invariante 9 proíbe.

## Gatilho escrito

**A primeira rota que crie um recurso cujo dono ainda não exista** — hoje só esta. Se
aparecer outra, a §1 deixa de ser caso único e a regra *"escrita sem vínculo só quando o
vínculo é o produto"* precisa estar no `CLAUDE.md` como linha, não como ADR.

**O dia em que o `merchant` precisar de um GET de uma loja por id.** Aí o predicado
exato da G-B5 volta à mesa, e com ele o `Location` desta rota — com ADR própria, porque
a decisão é de roteamento e não de recurso.

**O dia em que uma pessoa física precisar ser impedida de criar lojas sem limite.** Hoje
não há limite, não há unicidade de documento e não há quem reclame — quando houver, é
decisão de produto, com o número vindo de alguém que opera.
