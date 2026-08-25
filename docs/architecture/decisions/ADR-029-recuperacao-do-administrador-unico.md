# ADR-029 — Recuperação do administrador único

**Status:** Aceita — 24/08/2026
**Fecha a pendência de:** `docs/dominio/estabelecimento.md` §9
**Relacionada:** ADR-011 (autorização contextual), ADR-013 (dados pessoais), ADR-015 (emissão do JWT)
**Detalhada em:** `docs/operacao/recuperacao-de-acesso.md`
**Precisa existir no marco 1** — toca conta, vínculo e papel, que é o que o marco 1 constrói

## Contexto

A invariante M6 garante que **existe** pelo menos um administrador ativo por
estabelecimento. Ela não garante que alguém consiga **entrar** como ele.

```
M6   ≥ 1 ADMINISTRADOR ATIVO por estabelecimento
     ↑
     garante que o registro existe, não que há quem o acesse
```

Quatro modos de falha, e só o primeiro é o caso fácil:

| # | Situação | Por que é diferente |
|---|---|---|
| a | Esqueceu a senha, tem o telefone | recuperação comum, resolvida pelo canal registrado |
| b | Trocou de número, perdeu o aparelho | **não recebe o código** — o canal é o que falhou |
| c | O administrador saiu, foi demitido ou morreu | não há ninguém para recuperar a conta dele |
| d | Há colaboradores ativos, nenhum administrador alcançável | a loja opera e não se administra |

### A armadilha que fica no meio

> **Recuperar conta e recuperar estabelecimento são problemas diferentes.**
> Recuperar conta é provar que você é aquela pessoa. Recuperar estabelecimento é
> provar que o negócio é seu.

Confundir os dois é o caminho fácil — o segundo parece um caso especial do
primeiro — e é exatamente assim que se faz tomada de conta. As provas são
diferentes, os riscos são diferentes, e num sistema onde permissão pertence ao
vínculo (ADR-011), a consequência de errar é maior do que parece. Ver §3.

### O que já existe

O `identity` guarda **nome, telefone, e-mail e hash de senha** (ADR-013 §1). Há
dois canais possíveis desde o começo — o que falta não é campo, é dizer qual
deles serve para recuperar e sob que condição.

## Decisão

### 1. Recuperação comum exige canal verificado

Cada canal da conta — telefone e e-mail — carrega estado de verificação. **Só
canal verificado recupera acesso.**

No cadastro, o canal usado para criar a conta nasce verificado. O segundo é
**pedido, não exigido**: obrigá-lo produziria um e-mail inventado na hora, que é
pior que campo vazio porque parece proteção.

Isso resolve **(a)** sempre e **(b)** quando há um segundo canal — que é a
maioria dos casos, e é por isso que pedir vale a pena.

### 2. A regra central: a recuperação opera sobre o vínculo, nunca sobre a credencial

Esta é a decisão mais importante desta ADR, e a menos óbvia.

Um mesmo usuário pode administrar **mais de um estabelecimento** — é o desenho da
ADR-011, e o exemplo do próprio documento de domínio: Jorge é administrador na
pizzaria da Marli e entregador no mercadinho do Sérgio.

Se a recuperação do estabelecimento A resetasse a **senha da conta**, quem
recuperou passaria a ter acesso ao estabelecimento B — que não tem nada a ver com
a prova apresentada.

```
recuperação do estabelecimento A
    ├── cria ou promove Membro em A          ✓ escopo da prova
    └── NUNCA altera credencial do Usuario   ✗ vazaria para B, C, D…
```

Por isso a recuperação assistida **cria ou promove um `Membro`** e não toca em
`Usuario`. Quem perdeu a própria conta recupera a conta pelo canal verificado
(§1) — que é o outro problema, com a outra prova.

### 3. Recuperação assistida do estabelecimento, quando nenhum canal funciona

Cobre **(b)** sem segundo canal, **(c)** e **(d)**. É procedimento manual e
auditado, detalhado em `docs/operacao/recuperacao-de-acesso.md`, com três
elementos que não são negociáveis:

**Prova de titularidade do estabelecimento, e não do CNPJ.** O documento do
estabelecimento é **público** no Brasil — apresentá-lo não prova nada. A prova é
a combinação de documento do titular batendo com o cadastro **mais** um elemento
que só quem opera a loja controla: o número do canal da loja, ou a origem do
pagamento da assinatura.

**Prazo de espera antes de executar.** Entre o pedido e a execução corre uma
janela em que qualquer membro ativo pode barrar. Ela existe para dar tempo de o
titular real reagir — sem ela, a tomada de conta é instantânea e irreversível.

**Notificação a todos os membros ativos**, imediata, em todos os canais
verificados. A mensagem não carrega dado nenhum do estabelecimento: diz que há um
pedido de recuperação associado àquele contato e como contestá-lo. Se for
tentativa de tomada, o dono real vê.

### 4. Promover é preferível a criar

Quando há membro ativo, a recuperação **promove um deles a administrador**. Criar
administrador novo só quando não há membro nenhum.

Promover é mais seguro por um motivo simples: aquela pessoa **já tinha acesso
àquela loja**. A superfície de risco é a diferença entre colaborador e
administrador, não entre estranho e administrador.

### 5. Tudo fica registrado, e o registro sobrevive

Quem autorizou, com que prova, quando, e o que foi feito. Mesmo tratamento dos
ajustes de valor: lançamento, não edição.

E vale a regra da ADR-013 §5 — **o nome de quem autorizou sobrevive ao
desligamento**, anonimizado só depois dos cinco anos. Recuperação de acesso é
registro de responsabilidade, não dado operacional.

## Consequências

**Positivas**

- A loja para de poder ficar órfã sem saída. M6 garantia o registro; agora há
  caminho até ele.
- A separação vínculo × credencial impede que recuperar uma loja dê acesso às
  outras do mesmo usuário — um vazamento que passaria despercebido justamente
  porque o desenho multiloja é discreto.
- Promover em vez de criar mantém a recuperação dentro de quem já tinha acesso.
- Notificação e prazo tornam a tomada de conta **cara e visível**, que é o
  máximo que se consegue sem cartório.

**Negativas**

- **O procedimento é manual e não escala.** Mesma dívida assumida na ADR-013 §7
  para a exclusão de titular, e pelo mesmo motivo: automatizar antes de haver
  demanda é construir para zero casos por ano. Vira dívida com prazo no dia em
  que houver volume.
- **O prazo de espera deixa a loja sem administrador durante a janela**, no caso
  legítimo. É o custo direto da salvaguarda, e a alternativa — recuperação
  imediata — é tomada de conta imediata.
- **O canal secundário é opcional**, então quem não preencher cai no
  procedimento assistido, que é lento. Aceito: obrigar produz dado falso.
- **A prova falha no caso do aparelho perdido com o chip da loja.** Se o número
  do canal e o do administrador se perderam juntos, sobra o documento, e o
  procedimento fica mais lento e mais dependente de julgamento humano. É o pior
  caso, e não tem resposta boa.
- **Julgamento humano é superfície de ataque.** Quem executa o procedimento pode
  ser convencido. As salvaguardas reduzem, não eliminam — e o registro existe
  para que um erro seja rastreável depois.

## Alternativas consideradas

- **Exigir dois administradores desde o cadastro.** Elimina o problema por
  construção. Rejeitada: a pizzaria de uma pessoa tem uma pessoa. O comerciante
  cadastraria um administrador falso, ou promoveria o atendente a administrador
  — e aí a escalada de privilégio que A1 impede entraria pela porta da frente.
- **Códigos de recuperação gerados no cadastro.** Tecnicamente limpo e comum em
  produtos técnicos. Rejeitada pela persona: a Marli opera pelo celular, entre um
  forno e outro, e não vai guardar oito códigos num papel. Um mecanismo que
  ninguém usa é pior que nenhum, porque cria a impressão de que o problema está
  resolvido.
- **Resetar a credencial da conta na recuperação do estabelecimento.** É o
  caminho intuitivo e o defeito do §2 — dá acesso a todas as outras lojas do
  mesmo usuário.
- **Delegar a identidade a um provedor externo** e com ela a recuperação.
  Resolveria de graça, e é o que muita gente faz. Rejeitada: contraria a ADR-015,
  que escolheu emissão própria, e transfere para um terceiro o elo mais sensível
  do produto — sem contar que a persona não necessariamente tem conta nesses
  provedores.
- **Recuperação assistida sem prazo de espera**, executada assim que a prova
  convence. Mais rápida no caso legítimo. Rejeitada: torna a tomada de conta
  instantânea, e o dano é irreversível — quem entrou remove os outros membros.
- **Aceitar o documento do estabelecimento como prova única.** Rejeitada porque o
  CNPJ é dado público. Seria uma porta aberta com aparência de fechadura.

## Pendência que esta decisão não fecha

**O prazo exato da janela de espera** e o conjunto final de provas aceitas. São
decisões de operação e de risco, não de arquitetura, e dependem de haver um
primeiro cliente para calibrar. O procedimento em
`docs/operacao/recuperacao-de-acesso.md` carrega os valores propostos marcados
como tais.

## Emenda que esta decisão provoca

O documento de arquitetura v2 (`docs/referencia/`), §18.1, lista **recuperação do
administrador único** como decisão de produto sem dono, com prazo "antes do
primeiro cliente". Passa a estar decidida aqui, e o prazo fica mais curto do que
era: **marco 1**, porque toca conta, vínculo e papel.

O §18.5 do mesmo documento continua valendo — os prazos da ADR-013, o encarregado
e o canal do titular seguem pendentes de revisão jurídica. O que mudou é que
agora há material preparado para ela: `docs/operacao/revisao-juridica.md` e
`docs/operacao/legitimo-interesse.md`.
