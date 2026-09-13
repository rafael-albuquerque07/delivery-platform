# ADR-040 — Um módulo de tipos de valor, e a regra que impede ele de crescer

**Status:** Aceita — 08/09/2026
**Relacionada:** ADR-001 (monorepo), ADR-002 (banco por serviço), ADR-009
(modelo de valores), ADR-024 (desconto de retirada), ADR-028 (pedido mínimo),
ADR-035 (idioma)
**Emenda:** ADR-001
**Precisa existir antes do `Estabelecimento`** — ele é o primeiro agregado com
dinheiro dentro

## Contexto

O `merchant-service` está prestes a escrever o **primeiro `Money` do
repositório**. Ele aparece em quatro lugares só no `Estabelecimento`:
`descontoDeRetirada`, `pedidoMinimoPorModalidade`, `fundoMaximoDeTroco` e
`AreaDeEntrega.taxa`.

E não para aí. `order`, `payment`, `settlement` e `catalog` todos precisam do
mesmo tipo, porque a ADR-009 o especifica como base do modelo de valores do
pedido.

### O que a ADR-001 decidiu, e por quê

> **Nenhum módulo de serviço declara outro módulo de serviço como dependência.**
> Serviço só depende de `build-logic` e de bibliotecas externas.

A segunda frase é a que morde: como está escrita, ela proíbe **qualquer** módulo
compartilhado, não só a dependência entre serviços. E o motivo está na
consequência negativa que a própria ADR-001 registra:

> *"O monorepo torna o acoplamento barato, e acoplamento barato é como
> microsserviços morrem."*

A regra é boa e a preocupação é real. Um `commons` que começa com um tipo de
dinheiro termina com o agregado de pedido, e aí os oito serviços sobem juntos ou
não sobem.

### Por que copiar não resolve neste caso

A duplicação deliberada é prática corrente aqui — modelo de domínio e entidade
JPA são duplicados de propósito, e a ADR-002 duplica dado entre serviços em vez
de compartilhar banco. O reflexo natural seria copiar `Money` cinco vezes.

O que separa este caso: **a ADR-009 especifica o comportamento e nada verifica que
as cópias o cumprem.** Escala 2, `HALF_UP`, arredondar ao formar cada componente,
somar componentes já arredondados. Uma cópia com `HALF_DOWN` erra centavos —
valores que fecham na tela e não fecham na porta, e que ninguém vê até a
conferência de caixa não bater.

A duplicação entre domínio e entidade JPA é diferente: ela é *visível* e as duas
metades são exercitadas pelo mesmo teste de integração, que falha se divergirem.
Cinco `Money` não têm um teste que os compare — cada um tem o seu, e os testes
também são cópias que derivam.

### A assimetria com o validador que a ADR-037 adiou

A ADR-037 §"não decide" deixou para depois onde mora o validador de `iss` e
`aud`, com o argumento de decidir quando o segundo consumidor existir. Aqui a
mesma pergunta chega por outro caminho, e a resposta é outra pelo custo de
adiar:

```
validador de iss/aud   dez linhas de configuração   →  retrofit = nove edições
Money                  tipo em assinatura e coluna  →  retrofit = refatoração
                                                        atravessada em cinco
                                                        serviços com migration
```

## Decisão

> **Existe um módulo compartilhado, `:value-types`, e ele contém tipos de valor —
> nada mais.** A ADR-001 é emendada para permiti-lo, e a regra de entrada abaixo
> é parte da decisão, não recomendação.

### A regra de entrada

Um tipo só entra em `:value-types` se cumprir **todas**:

| # | Regra | Como se verifica |
|---|---|---|
| 1 | Sem dependência de framework — Spring, Jakarta, Hibernate, Jackson | **ArchUnit no próprio módulo**, build vermelho |
| 2 | Imutável e sem estado externo | Revisão |
| 3 | Não é regra de negócio de serviço nenhum | Revisão: se a resposta a *"de quem é essa regra?"* for o nome de um serviço, não entra |
| 4 | Não é agregado, porta nem evento | Revisão: esses três são fronteira, e fronteira compartilhada é o acoplamento que a ADR-001 teme |
| 5 | **Cada entrada nova é emenda a esta ADR** | Revisão |

A regra 5 é a que faz as outras valerem. Sem ela, a primeira exceção não é
registrada em lugar nenhum e a segunda cita a primeira.

### O nome do módulo é parte da defesa

Não se chama `commons`. `commons` é convite: o nome não diz o que cabe, então
tudo cabe. `value-types` diz o que é, e alguém prestes a colocar uma porta ali
lê o nome do diretório antes de escrever o arquivo.

O pacote é `com.deliveryplatform.valuetypes` — inglês, porque módulo de build e
tipo de padrão são engenharia (ADR-035).

### O que entra hoje: `Money`. Só.

E duas recusas imediatas, que existem para a regra 5 nascer verdadeira em vez de
virar formalidade:

**`Telefone` não entra**, apesar de o `identity` já ter um e o `merchant`
precisar de outro. O do `identity` é o identificador de login com unicidade (U1);
o do `merchant` é o contato da loja. Mesma sintaxe, significados diferentes, e
compartilhar o tipo compartilharia a impressão de que são a mesma coisa.

**`Endereco` não entra.** Cada serviço guarda o pedaço de endereço que a regra
dele usa, e o `pedido` congela `enderecoTextual` — que é texto, não estrutura.
Não há tipo comum a extrair ainda; há um palpite de que haveria.

Quando um dos dois tiver argumento, ele vem como emenda, com o argumento
escrito.

### `Money` segue a ADR-009 literalmente

Escala 2, `RoundingMode.HALF_UP`, **e código de moeda** — que o resumo do
`CLAUDE.md` omite e a ADR-009 nomeia. A moeda não está lá para suportar
multimoeda: está para que somar reais com outra coisa **estoure** em vez de
somar.

**Sinal negativo é permitido.** A ADR-009 exige componentes do `Pedido` `≥ 0`,
mas isso é regra do `Pedido`; `Ajuste.delta` é `Money` e a mesma ADR diz que ele
pode ser negativo. Validar sinal no tipo o tornaria incapaz de expressar metade
do modelo que ele serve — e é exatamente o tipo de regra que a regra 3 acima
proíbe de entrar aqui.

**Não há divisão nem percentual.** Nada no modelo precisa deles, e a ADR-024
rejeitou desconto percentual em favor de valor fixo. Método disponível é método
que um dia é usado.

## Consequências

**Positivas**

- O arredondamento de dinheiro passa a ter **um** lugar e **um** conjunto de
  testes, em vez de cinco que precisam concordar sem nada os comparando.
- A regra 1 é verificada por build, não por disciplina — e este repositório já
  tem seis peças que eram só frases.
- `build-logic` continua sendo o único lugar onde versões são declaradas
  (ADR-001): as dependências de teste do módulo moram num convention plugin.

**Negativas**

- **A ADR-001 fica com uma exceção, e exceção é onde regra não verificada
  quebra.** A pendência que ela já registra — verificação no build de que
  `:services:X` não depende de `:services:Y` — passa de importante a urgente,
  porque a regra deixou de ser "serviço não depende de módulo nenhum do
  repositório" e virou "de um, e só de um".
- **A regra de entrada tem quatro linhas que uma máquina não confere.** São
  regra de revisão, e revisão de uma pessoa só é o revisor mais fraco que existe.
  Escrever a regra é o que se pode fazer; ela não se defende sozinha.
- **O módulo nasce sem consumidor.** Seus testes rodam, mas a dependência de um
  serviço só é exercitada na rodada seguinte, quando o `Estabelecimento` usar
  `Money`. Módulo compartilhado que ninguém consome é peça que não rodou, e o
  prazo para isso deixar de ser verdade é uma rodada.
- **Um módulo a mais no build**, e um convention plugin a mais para uma coisa só.

## Alternativas consideradas

- **Copiar `Money` em cada serviço, mantendo a ADR-001 intacta.** É a leitura
  fiel da decisão vigente e não custa emenda nenhuma. Rejeitada pelo argumento do
  Contexto: a especificação existe na ADR-009 e nada verifica que as cópias a
  cumprem, e o erro que isso produz é em centavos — o tipo de erro que aparece
  meses depois, na conferência de caixa, sem rastro de onde veio.
- **Adiar até o segundo consumidor**, como a ADR-037 fez com o validador.
  Rejeitada pela assimetria de custo do Contexto: o segundo consumidor de `Money`
  é certo, e quando chegar o tipo já estará em assinatura de método e coluna de
  banco.
- **Pôr `Money` no `build-logic`.** Rejeitada porque `build-logic` é build
  incluído de plugins Gradle: ele configura a compilação, não entrega classe ao
  classpath de runtime de ninguém.
- **Publicar `:value-types` como artefato versionado** e consumi-lo como
  biblioteca externa — o que caberia na ADR-001 sem emenda nenhuma. Rejeitada:
  cria ciclo de publicação para código que muda junto com quem o usa, que é
  exatamente o argumento com que a ADR-001 recusou o polirepo de contratos.
- **Chamar o módulo de `commons`.** Rejeitada no corpo da decisão.

## Emenda que esta decisão provoca

**ADR-001**, na seção *"A regra que impede o monorepo de virar monólito"* —
acrescentar ao fim:

```markdown
> **Emendado pela ADR-040 (08/09/2026).** Existe **um** módulo compartilhado que
> não é serviço: `:value-types`, com tipos de valor sem framework, sem estado e
> sem regra de negócio de serviço nenhum. Serviço passa a depender de
> `build-logic`, de `:value-types` e de bibliotecas externas — e de nada mais. A
> regra de entrada do módulo está na ADR-040, e a metade dela que uma máquina
> confere é verificada por ArchUnit dentro dele.
>
> A pendência abaixo fica **mais** urgente, não menos: a verificação passa a
> precisar distinguir a dependência permitida da proibida.
```

## O que esta decisão **não** decide

**Onde mora o validador de `iss` e `aud`** (ADR-037). `:value-types` não é o
lugar: um validador conhece Spring Security e cai na regra 1. Se a resposta for
um segundo módulo compartilhado, ela precisa de argumento próprio — e esta ADR
não o dá por analogia.

**Se `Telefone` e `Endereco` entram um dia.** Recusados hoje, com motivo escrito.

**A verificação de dependência entre serviços no build.** Continua sendo a
pendência da ADR-001, agora com uma exceção a acomodar.
