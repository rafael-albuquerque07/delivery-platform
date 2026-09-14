# ADR-042 — O código de verificação do cadastro, e quem o entrega

**Status:** Aceita — 14/09/2026
**Fecha a pendência de:** ADR-036, consequências — *"um índice único e um fluxo de verificação no marco 1"*
**Relacionada:** ADR-021 (catálogo de serviços), ADR-029 §1 (canal de cadastro nasce verificado), ADR-041 (a volta por condição), `docs/dominio/usuario.md` §2 e §7
**Emenda provável:** nenhuma. Esta ADR nasce com a condição de término escrita.

## Contexto

A ADR-036 fixou o telefone como identificador de login **e** como canal de
cadastro, e listou entre as consequências positivas:

> "Um índice único e **um fluxo de verificação** no marco 1."

O índice existe desde `V1__cria_usuario.sql`. O fluxo não, e a invariante U4 —
*"o telefone nasce verificado no cadastro"* — está hoje garantida por
construção no código e por ninguém na realidade: o único caminho que cria um
`Usuario` é um `INSERT` na mão, e o carimbo `telefone_verificado_em` afirma uma
posse que jamais foi provada.

**E não há como provar.** O único caminho até o telefone de alguém, neste
desenho, é o `CanalPort` do `conversation-service` (`docs/dominio/conversa.md`
§10) — adaptadores WhatsApp Cloud API e e-mail. O `conversation-service` não
tem código, não é marco 1, e a ADR-001 (emendada pela ADR-040) proíbe o
`identity` de depender dele: serviço depende de `build-logic`, `:value-types` e
bibliotecas externas, e nada mais.

```
ADR-036   "fluxo de verificação no marco 1"
   ↓
precisa mandar um código para um telefone
   ↓
único canal: CanalPort  →  conversation-service  →  não existe, não é marco 1
```

Duas decisões escritas se contradizem. Esta ADR escolhe qual cede, e em quê.

## Decisão

### 1. O fluxo de verificação existe no marco 1. O transporte é humano

O cadastro tem dois passos e um código de seis dígitos:

```
POST /api/v1/auth/verification-code   { telefone }        →  202, sempre
POST /api/v1/auth/signup              { telefone, codigo, nome, senha }  →  201
```

Entre um e outro, **quem entrega o código é a pessoa que faz o onboarding**,
lendo a tabela `codigo_de_verificacao` e dizendo o número ao comerciante pelo
canal por onde já está falando com ele.

Isto não é um contorno: é a mesma forma da ADR-029, que decidiu que a
recuperação do administrador único é **procedimento manual até haver volume**,
com o argumento de que *"automatizar antes de haver demanda é construir para
zero casos por ano"*. No marco 1 há zero comerciantes. Um adaptador de SMS sem
provedor, sem conta, sem template aprovado e sem ninguém para receber a
mensagem seria a nona peça que nunca rodou.

**O que muda em relação a hoje é o que importa:** o `telefone_verificado_em`
deixa de ser um carimbo automático e passa a existir só porque alguém confirmou
um código que só quem atende aquele número recebeu. A U4 vira verdade em vez de
afirmação.

### 2. O código é guardado em texto claro, e isso é consequência, não descuido

Enquanto o transporte for humano, **a tabela é o canal de entrega**. Um código
com hash não pode ser lido por quem precisa entregá-lo, e um fluxo que ninguém
consegue operar é o mesmo que fluxo nenhum.

A comparação com a senha (U5, `usuario.md` §3) não se aplica, e vale dizer por
quê em vez de deixar a semelhança confundir:

| | Senha | Código de verificação |
|---|---|---|
| Tempo de vida | anos | **10 minutos** |
| Reuso | toda sessão | **uma vez** |
| O que abre | a conta, e por ela todas as lojas | **um cadastro de um telefone que ainda não tem conta** |
| Precisa ser lida por alguém | nunca | **sim, é assim que ela chega** |

Um código vazado permite criar conta num telefone que o atacante não tem — que
é exatamente o que a verificação impede, e por isso o risco é real e limitado a
dez minutos, a um número, e a quem tem acesso ao banco. Quem tem acesso ao
banco já tem acesso a coisas piores.

### 3. Condição de término, escrita agora

> **No dia em que o `conversation-service` tiver o `CanalPort` de pé, este
> fluxo ganha um adaptador de saída e o código passa a ser guardado com hash,
> na mesma rodada.** Não antes — e não "quando der".

A porta `EnviadorDeCodigo` **não nasce agora**. Uma interface com um único
adaptador que não envia nada é a peça que nunca rodou com outro nome. Ela nasce
no dia em que houver o que ligar do outro lado, que é o dia em que ela passa a
ter dois adaptadores de verdade: o canal e o console.

### 4. Três limites, e só um deles é política

| Limite | Valor | Natureza |
|---|---|---|
| Validade do código | 10 minutos | constante de domínio, igual para todo mundo |
| Tentativas por código | 5 | **invariante do código**, não política de produto |
| Códigos simultâneos por telefone | 1 — pedir de novo substitui | invariante da tabela (`UNIQUE`) |

O limite de tentativas **entra agora**, e é preciso separar do que
`usuario.md` §7 deixou aberto. Lá o que está em aberto é a *política*: quantos
logins por hora, quantos SMS por número, quanto isso custa. Aqui é outra coisa
— um código de seis dígitos sem limite de tentativas percorre o espaço inteiro
em minutos, e um fluxo de verificação que se deixa adivinhar **não verifica
nada**. Sem essa linha, a decisão 1 seria falsa.

### 5. Nenhum dos dois endpoints diz quem já tem conta

`POST /auth/verification-code` responde **202 sempre**, inclusive para telefone
já cadastrado — caso em que nenhum código é criado. `POST /auth/signup` devolve
a **mesma** recusa para código errado, código expirado, código estourado de
tentativas e telefone que passou a existir no meio do caminho.

É a política que a ADR-037 §7 já aplica ao login, pelo mesmo motivo: a
diferença entre duas respostas é informação sobre o cadastro, e uma lista de
telefones que têm conta neste produto é uma lista de comerciantes.

**A exceção deliberada:** telefone que não normaliza devolve **400**. Não é
vazamento — um número malformado não podia estar cadastrado de forma nenhuma,
e responder 202 a quem digitou errado esconde um erro do cliente atrás de uma
política que existe para outra coisa. É a mesma distinção que o `LoginRequest`
já documenta, lida do outro lado.

## Consequências

**Positivas**

- A U4 passa a ser verdade verificável em vez de invariante de construção.
- O `salvar` e o `existeComTelefone` do `UsuarioRepositorio` ganham o primeiro
  consumidor de produção. Até hoje só os testes os chamavam — uma porta de
  escrita que nenhum caso de uso usava.
- A promessa da ADR-036 é cumprida em vez de reinterpretada.
- O dia em que o transporte existir está escrito, com o que muda junto.

**Negativas**

- **O cadastro não é autoatendimento.** Ninguém cria conta sozinho às duas da
  manhã — precisa de alguém do outro lado. Aceito enquanto o produto tem zero
  comerciantes e todo onboarding é assistido de qualquer jeito; deixa de ser
  aceitável no dia em que houver fila.
- **Código em claro no banco.** Mitigado pelo tempo de vida, pelo uso único e
  pelo escopo do que ele abre — e é a única forma de o transporte humano
  funcionar. É a linha desta ADR que eu menos gosto, e está aqui escrita para
  que ninguém precise descobri-la lendo a tabela.
- **Uma tabela que acumula linhas mortas.** Código expirado só some quando o
  mesmo telefone pede outro. Não há rotina de limpeza, e não vai haver
  enquanto o volume for o que é: uma linha por telefone que já tentou se
  cadastrar, no pior caso. Vira problema junto com o volume, e aí vira uma
  linha de `DELETE ... WHERE expira_em < now()` em algum lugar.
- **Seis dígitos é pouco** para um código que vive dez minutos com cinco
  tentativas. É suficiente aqui (5 em 10⁶ por código, e um código por
  telefone), e é o formato que cabe numa mensagem que alguém vai ditar por
  telefone. Oito dígitos seriam mais seguros e piores de ditar.

## Alternativas consideradas

- **Cumprir a ADR-036 com um adaptador de SMS de verdade.** Exige provedor,
  conta, custo por mensagem e — no caso do WhatsApp — template aprovado, que
  `conversa.md` §8 registra levar dias. Tudo isso para zero comerciantes.
- **Emendar a ADR-036 e tirar a verificação do marco 1**, como a ADR-041 fez
  com a observabilidade. Rejeitada: a observabilidade que saiu não mentia sobre
  nada. Aqui, o que ficaria no lugar é um `telefone_verificado_em = agora()`
  gravado sem que ninguém verifique coisa alguma — e a U4, que é invariante
  escrita, passaria a ser falsa no banco enquanto continuava verdadeira no
  código. Custo diferente e pior.
- **Criar a porta `EnviadorDeCodigo` agora, com adaptador que registra em
  log.** Rejeitada duas vezes: a interface teria um adaptador só, e o adaptador
  poria uma credencial de uso único no log — contra a linha do `CLAUDE.md` que
  proíbe token e senha em log.
- **Deixar o `identity` chamar o `conversation` por HTTP quando ele existir.**
  Não é alternativa a esta decisão, é o desenho do adaptador futuro — e fica
  para a rodada que o construir, com a ADR-012 (gateway autentica, serviço
  autoriza) na mesa.

## Pendência que esta decisão não fecha

**A política de limite por telefone e por janela** — quantos códigos um número
pode pedir por hora, e o que acontece com quem pede mil. Continua aberta em
`usuario.md` §7, e ganha urgência no dia em que cada código custar uma
mensagem. Hoje custa um `INSERT` que substitui o anterior.

## Emenda que esta decisão provoca

`docs/dominio/usuario.md` §2 diz *"o telefone nasce verificado"* sem dizer como.
Passa a apontar para cá: nasce verificado **porque um código de seis dígitos,
válido por dez minutos e com cinco tentativas, foi confirmado** — e o carimbo é
o instante da confirmação, não o do cadastro.
