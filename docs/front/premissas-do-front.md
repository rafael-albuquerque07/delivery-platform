# As premissas do front

**O que este documento é:** a lista de telas que o mercado espera de um "app de
delivery" e que **este produto decidiu não ter**, cada uma com a premissa do PRD
que a mata — e as regras que valem para qualquer tela que se escreva aqui.

**Por que ele existe:** em 28/09/2026 chegou um protótipo de front com sete
páginas. Três descreviam funcionalidade que o produto recusa, e o defeito mais
grave não era tela nenhuma: era um seletor de "Tipo de acesso" no login, que é
autorização declarada pelo cliente. Nada disso estava escrito em lugar nenhum do
repositório — a lista existia numa conversa, e conversa não sobrevive a seis
rodadas.

> **A frase que resume:** "app de delivery" significa iFood para praticamente
> todo mundo, e o PRD deste produto diz, em seis premissas, que ele é
> deliberadamente outra coisa.

---

## 1 · As seis premissas, na forma em que o front as sente

| # | A premissa | O que ela proíbe na tela |
| --- | --- | --- |
| **P1** | A plataforma não custodia dinheiro na maioria das transações | Carteira, saldo na plataforma, checkout com cartão como caminho padrão, qualquer selo de "pedido garantido" |
| **P2** | O entregador pertence ao estabelecimento | Pool de corridas, aceitar entrega em broadcast, mapa de calor, cadastro de entregador autônomo, ganhos e avaliação de entregador |
| **P3** | O comerciante é o cliente; o consumidor é um usuário | Home com vitrine de lojas, busca por tipo de comida, avaliações, favoritos, vitrine de cupons |
| **P4** | O pedido nasce na conversa | **Carrinho e checkout em passos** — a ADR-006 põe o rascunho dentro da conversa. O painel *recebe* um pedido que já nasceu; ele não o monta |
| **P5** | Taxa de entrega por área nomeada | Mapa com raio, arrastar o pino, pedir permissão de localização, acompanhar o entregador em tempo real |
| **P6** | Disponibilidade qualitativa | Quantidade em estoque, "restam 3 unidades", contagem de inventário. Há quatro estados: disponível, acabando, acabou hoje, acabou sem data |

**P5 tem uma consequência que não é de tela, é de regra:** o `CLAUDE.md` proíbe
guardar coordenada. Endereço é **texto e bairro**. Nenhum componente deste front
pede `navigator.geolocation`, e nenhum guarda latitude ou longitude.

**P6 tem uma consequência de tipo:** `QUANTITATIVO` não existe no enum do back,
de propósito, até o marco 10. Não há campo de quantidade para exibir.

---

## 2 · As regras de autorização, que são as mais fáceis de errar

### 2.1 O cliente nunca declara o que pode

> `CLAUDE.md`: *"Permissão é do vínculo usuário × estabelecimento e é resolvida
> por requisição, com cache curto e falha fechada."*

O token tem **seis claims** — `iss`, `sub`, `aud`, `iat`, `exp`, `jti` — e
nenhuma permissão, nenhum papel. O que a pessoa pode fazer **numa loja** vem do
`merchant-service`, por requisição, e o `catalog` já pergunta a ele desde a G-B3.

Portanto, e sem exceção:

- **não existe seletor de perfil no login.** O protótipo tinha um, com quatro
  opções, e o destino depois do login era a página escolhida;
- **não existe rota de front escolhida por papel** guardado no cliente;
- o menu do painel se monta a partir das permissões que o servidor respondeu — e
  enquanto essa resposta não tiver rota pública, o menu não se monta.

### 2.2 O guarda de rota é conveniência, não segurança

Toda rota deste sistema exige token, e a autorização é resolvida no servidor a
cada requisição. O `ExigeSessao` existe para a pessoa não ver um painel piscando
antes do 401. **Apagá-lo não abriria dado nenhum.**

### 2.3 O token é opaco

O front **não decodifica o JWT**, nem para mostrar um nome. A validade vem do
`expiresIn` que o login devolve. Decodificar sem verificar é o primeiro passo
para confiar, e não há nada dentro do token em que se possa confiar para decidir
acesso. Há uma regra de ESLint contra `atob`.

### 2.4 As quatro recusas são uma só, e a tela não desambigua

No `catalog`, o mesmo 403 com o mesmo corpo sai para: sem vínculo, sem permissão,
`merchant` com erro e `merchant` calado. É M7 — pela borda, ninguém distingue "a
loja não é sua" de "o sistema está com problema". Escolher uma das quatro na
mensagem é mentir com mais confiança do que o servidor.

### 2.5 O que a tela mostra quando o servidor recusa (W-C)

O `detail` do `ProblemDetail`, como veio, e **sem interpretar o texto** — ADR-055.
Frase de recusa é escrita uma vez, no servidor, onde a regra mora; uma segunda
redação no front diverge da primeira sem ninguém notar.

Três consequências que o código tem de respeitar:

- **quem lê o corpo de erro é o `cliente.ts`, e só ele** (`lerDetalhe`, desde a
  W-A). O contrato declara os erros sem corpo — os tipos gerados dizem
  `content?: never` —, então nenhuma tela lê corpo de erro por conta própria;
- **`detail` que não é texto não vai à tela**, e texto em branco também não. Cai
  na mensagem por código. Resposta de erro pode vir do gateway, de um proxy ou da
  página de erro do contêiner;
- **no 403 o texto do servidor não é mostrado.** Ele é fixo e genérico de propósito
  (§2.4), e o que a tela faz é oferecer recarregar o painel — o menu é montado das
  permissões que o servidor respondeu, e um 403 numa ação oferecida significa que
  elas mudaram.

---

## 3 · Dinheiro, e regra de domínio

> ADR-009: `BigDecimal`, escala 2, `HALF_UP`.

No contrato HTTP, `precoBase` é `number`. Em JavaScript isso é ponto flutuante.

**O front não calcula dinheiro.** Não soma, não multiplica por quantidade, não
aplica desconto. Quem faz conta é o servidor. A cotação está desenhada na
ADR-018 (`POST /internal/catalog/quote`), **ainda não existe em código**, e é
interna — o front nunca a chamará; o total chegará a ele pronto, dentro do
pedido. O front **exibe** e **formata**,
com `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })`.

O protótipo fazia `Number(item.price) * Number(item.qty)` no navegador e somava
uma taxa fixa de R$ 5 — que, além de ser float, contraria P5.

**E o front não deriva regra de domínio** (R-1, 10/10/2026). O `vendavel` é o caso
que trouxe isto à tona (W-D): a tela do produto aberto tem **todos** os dados para
recalculá-lo — os grupos, o mínimo de cada um, o estado de cada opção — e mostra o
que o servidor mandou. A regra mora no `catalogo.md` §4 ("Vendabilidade derivada") e
é derivada no `catalog`; derivá-la outra vez aqui seria um segundo lugar onde ela
está escrita, e no dia em que ela mudar a tela passaria a mentir **sem nada ficar
vermelho**.

O argumento é o mesmo que a ADR-059 usou contra semear por SQL, aplicado à borda de
cima em vez da de baixo: **não duplicar a regra — chamar quem a tem.**

**O que prova isto é um dublê que mente.** Um produto com uma opção esgotada e
`vendavel: true` não distingue as duas implementações: se o grupo ainda tem opção
suficiente, a regra aplicada certo também dá `true` — medido na W-D, recalcular pela
§4 deixou esse caso verde. O caso que distingue manda `vendavel: false` num produto
que a regra daria como vendável: um estado que ela nunca produziria, e que só uma tela
que lê o servidor mostra.

---

## 4 · O que o front tolera, porque o back mudará

### 4.1 Valor novo em enum: o front tolera, mesmo que o back não conte com isso

> `CLAUDE.md`: *"Enum que chega com valor que este serviço não conhece **não
> estoura, não bloqueia e não vira exceção** — vira registro de que chegou algo
> não entendido."* (ADR-027)

A ADR-027 §2 é mais dura do lado de quem produz: *"Valor novo em enum é
incompatível por padrão"* — exige versão nova. Tolerar do lado do front não
torna o acréscimo compatível; torna o front um consumidor que não quebra quando
alguém errar.

O `disponibilidade` do produto vem como enum fechado de quatro valores no
contrato. O dia em que o back acrescentar um quinto, o tipo gerado não o
conhecerá. Toda leitura de enum no front tem um caminho para o desconhecido, e
esse caminho **nunca concede nada** — exibe um estado neutro, não "disponível".

### 4.2 Campo novo no corpo é ignorado

Nenhuma leitura deste front reprova uma resposta por trazer campo que ele não
conhece.

---

## 5 · Acessibilidade e responsividade, desde o primeiro componente

Está no `frontend/README.md` desde o commit inicial: *"Responsivo desde o
primeiro componente."*

- rótulo ligado ao campo por `htmlFor`/`id`, e o id vem de `useId` — dois campos
  na mesma página com id fixo colidem;
- erro de campo referenciado por `aria-describedby`, senão o leitor de tela
  anuncia o campo e nunca chega ao erro;
- erro de formulário com `role="alert"`;
- botão que dispara requisição fica `disabled` enquanto ocupada — dois cliques no
  cadastro consomem o código e o segundo falha com "código inválido", que parece
  erro do usuário e é do front;
- cor nunca é a única portadora de significado;
- funciona em largura de telefone, com margem lateral, e sem rolagem horizontal.

---

## 6 · O que o front **não** tem porque o back ainda não respondeu

Isto não é lista de desejos: é o que foi **medido** em 29/09/2026 e o que trava
tela, com o gatilho de cada um.

| O que falta | Consequência na tela | Gatilho |
| --- | --- | --- |
| ~~Rota que diga **quais lojas o portador tem**~~ | ~~Não há tela depois do login. Todas as rotas de negócio começam com `{estabelecimentoId}`~~ | **resolvido na G-B5** — `GET /api/v1/me/estabelecimentos` |
| ~~Rota **pública** de contexto de acesso~~ | ~~O menu do painel não se monta. A que existe é `/internal/`, que o gateway não roteia~~ | **resolvido na G-B5**, e não vai existir: seria uma segunda verdade sobre o mesmo vínculo. `GET /api/v1/me/estabelecimentos` já traz papel e permissões de cada loja; o `/internal/…/contexto-de-acesso` continua só para serviço |
| Rota que devolva **o usuário do token** | O painel não sabe o nome de quem entrou | a G-B5 **não** a entregou; sem rodada marcada |
| **Refresh token** | Trinta minutos e login outra vez, no meio do expediente | ADR-037 registra como dívida assumida |
| `exposedHeaders` no CORS | O front não lê `Location` nem `WWW-Authenticate` de outra origem | a primeira rota que responda `201` com `Location` |
| `required` nos esquemas do `catalog` e na `LojaDoUsuario` do `merchant` | Todo campo do tipo gerado é opcional, e a tela precisa tratar ausência que não acontece | a primeira tela que leia produto |
| O **estado da operação** — a loja está aberta agora? | O seletor não pode mostrar aberta/fechada. A `OperacaoDoEstabelecimentoPort` do `estabelecimento.md` §3 não existe em código | sem rodada marcada — a porta não existe (ADR-046, emenda de 30/09/2026), e a rota da G-C1 responde outra pergunta: qual expediente carimbar |
| Paginação na tela do cardápio | A lista mostra os 20 primeiros e diz quantos há. Quem tiver 200 produtos não alcança o resto | a primeira loja de teste com mais de 20 produtos |
| Rota de escrita do catálogo | Não dá para criar nem editar produto pela tela — só ver | G-C2 |

**Enquanto um item desta tabela não tiver rota, a tela que depende dele não é
escrita** — e não é simulada com valor de configuração temporário. Um
`VITE_ESTABELECIMENTO_ID` resolveria a tela de hoje e ficaria no repositório
para sempre.

---

## 7 · O protótipo de 28/09 é referência visual, não base de código

O que se aproveita dele: a casca (barra lateral + topo + conteúdo), o vocabulário
visual do login, os estados vazios, a grade do cardápio com busca e filtro, o
modal do produto, o layout do ticket, o toast, os blocos de indicador.

O que **não** se aproveita: nada do código. São 407 linhas de `innerHTML` com
`onclick` interpolado e 803 linhas de CSS à mão. E o `innerHTML` com
interpolação é, por si, a razão: no dia em que o nome do produto vier da API, ele
é o texto que o comerciante digitou.

---

## 8 · Onde cada regra deste documento é conferida

| Regra | Quem a confere |
| --- | --- |
| A sessão mora em `sessionStorage`, e só um arquivo a toca | regra de ESLint `no-restricted-globals` |
| O token não é decodificado | regra de ESLint contra `atob` |
| Não há seletor de perfil no login | teste `não há seletor de perfil nesta tela` |
| 403 não desloga | teste `403 NÃO derruba a sessão` |
| O corpo do login usa `telefone` e `senha` | teste `manda telefone e senha` |
| O tipo do corpo casa com o contrato | `npm run tipos:conferir`, no CI |
| A tela mostra o `detail`, e só texto não vazio vira `detail` | testes `corpo sem detail textual não vira detalhe` (cliente) e `3 · a recusa mostra o detail do servidor, como veio` |
| No 403 o texto do servidor não vai à tela | testes `no 403 descarta o detail…` e `5 · no 403, não mostra o texto do servidor…` |
| Estado de enum desconhecido não concede nada | testes `estado que o front não conhece vira null…` e `estado que o front não conhece não aparece como "Disponível"` |
| O front não deriva regra de domínio | teste `3b · e quando o servidor diz que não é vendável, a tela diz também` |
| As seis premissas | **ninguém, e é por isso que este documento existe.** Nenhuma máquina confere que uma tela não foi desenhada. |
