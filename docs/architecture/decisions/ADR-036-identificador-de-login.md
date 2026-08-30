# ADR-036 — O telefone é o identificador de login, e o canal de cadastro

**Status:** Aceita — 30/08/2026
**Relacionada:** ADR-013 (retenção e anonimização), ADR-015 (JWT), ADR-029
(recuperação do administrador único), ADR-035 (idioma)
**Precisa existir antes do marco 1** — é o identificador único da primeira
classe do projeto e a primeira migration

## Contexto

O `identity-service` guarda **nome, telefone, e-mail e hash de senha**
(ADR-013 §1). Nenhum documento decide **com o que o comerciante entra no
painel**: a ADR-011 cita o `Usuario` como metade do par
`(usuarioId, estabelecimentoId)`, a ADR-015 como dono do claim `sub`, e a ADR-029
duas vezes pela negativa. O agregado nunca foi desenhado, e a única lista de
campos aparece de passagem numa ADR sobre retenção.

### O que a ADR-029 §1 decidiu, e o que ela deixou aberto

> *"Cada canal da conta — telefone e e-mail — carrega estado de verificação. Só
> canal verificado recupera acesso. No cadastro, o canal usado para criar a conta
> nasce verificado. O segundo é pedido, não exigido."*

A regra é **simétrica**: ela não prefere telefone nem e-mail. Diz que *um* canal
nasce verificado e que o *segundo* é opcional — qualquer um dos dois em qualquer
dos dois papéis.

E é justamente essa simetria que não sobrevive à existência de um login:

```
conta criada só com e-mail  +  login por telefone   →  conta que não consegue entrar
conta criada só com telefone +  login por e-mail    →  idem, espelhado
login por qualquer canal existente                  →  duas portas com garantias diferentes
```

Não há como escolher um identificador de login **sem** quebrar a simetria da
ADR-029 §1. A decisão abaixo quebra-a de propósito e diz em qual direção.

## Decisão

> **O telefone é o identificador de login, é único no sistema, e é o canal de
> cadastro** — logo nasce verificado (ADR-029 §1) e nunca falta.

O e-mail continua sendo o segundo canal: opcional, com estado de verificação
próprio, e servindo de recuperação secundária quando verificado. Nada nele muda.

**As duas metades são inseparáveis.** Nomear o identificador sem fixar o canal de
cadastro deixaria de pé o caso "conta criada só com e-mail, sem telefone, sem
como entrar". É por isso que esta ADR decide as duas coisas e não uma.

### Por que o telefone

**O canal de login é o mesmo canal de recuperação.** Um fluxo de verificação, um
lugar onde errar. Com login por e-mail e recuperação por telefone, a conta teria
dois caminhos de entrada com garantias diferentes — e a mais fraca seria a que
vale.

**É o que a Marli sabe de cor.** A P2 põe o produto no comércio de bairro
brasileiro, e o vocabulário do produto é o dela. Ela decora o próprio número; o
e-mail, quando existe, está anotado num papel.

**Um índice, uma coluna, uma migration.** No marco 1 isso não é detalhe.

## Consequências

**Positivas**

- Uma conta sempre tem meio de entrar, **por construção** — o campo de login é o
  campo de cadastro, e ele nasce verificado.
- A recuperação comum (ADR-029 §1) e o login provam a mesma posse.
- Um índice único e um fluxo de verificação no marco 1.

**Negativas**

- **Verificação por SMS custa dinheiro**, por mensagem, e o custo cresce com
  cadastro — inclusive com cadastro de má-fé. Limite de tentativas por número
  deixa de ser refinamento e passa a ser contenção de custo.
- **Telefone muda de dono.** Operadora recicla número, e um número reciclado pode
  dar a outra pessoa a chave de uma conta abandonada — com prova de posse
  legítima. O e-mail não tem esse comportamento. É a consequência mais séria
  desta decisão, está aberta em `usuario.md` §7, e precisa de resposta antes do
  marco 5.
- **A simetria da ADR-029 §1 acabou.** O canal de cadastro deixa de ser "aquele
  que a pessoa escolheu" e passa a ser sempre o telefone. A §1 não fica errada —
  fica mais estreita, e a emenda abaixo registra isso.
- **Formato exige normalização.** `11 98765-4321`, `(11) 98765-4321` e
  `+5511987654321` são o mesmo número e três chaves. Sem regra canônica, a
  unicidade de U1 é ficção.
- **Login por telefone é incomum em painel web.** Quem chega de outro produto
  procura o campo de e-mail e não encontra.

## Alternativas consideradas

- **E-mail como identificador.** É a convenção de painel web, a verificação é
  barata e o endereço é estável. Rejeitada porque quebraria a simetria da ADR-029
  §1 na direção oposta e com pior resultado: o e-mail teria de virar obrigatório,
  a recuperação por telefone continuaria existindo, e a conta teria duas portas
  de entrada com garantias diferentes.
- **Qualquer canal verificado — telefone ou e-mail.** Preserva a simetria e é o
  que produtos maduros fazem. Rejeitada pelo custo no marco 1: dois índices
  únicos, dois fluxos de verificação e um caminho para a mesma pessoa existir
  duas vezes. O ganho aparece no marco 5; o custo aparece na primeira migration.
- **Login por nome de usuário escolhido.** Não considerada seriamente:
  acrescenta um dado que ninguém decora e que não serve para recuperar nada.

## Emenda que esta decisão provoca

**ADR-029 §1** — acrescentar, ao fim da seção *"1. Recuperação comum exige canal
verificado"*:

```markdown
> **Estreitado pela ADR-036 (30/08/2026).** O canal de cadastro é sempre o
> **telefone**, que por esta seção nasce verificado. O e-mail permanece como o
> segundo canal — pedido, não exigido — e continua recuperando quando verificado.
> A regra desta seção não muda; o que deixa de existir é a escolha de *qual*
> canal cria a conta.
```

## O que esta decisão **não** decide

Formato de armazenamento, política para número reciclado, limite de tentativas e
troca de telefone de uma conta existente. Todos em `docs/dominio/usuario.md` §7 —
e o reciclado é o que eu menos sei responder.
