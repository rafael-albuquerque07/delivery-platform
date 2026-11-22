# ADR-072 — A seção que o comando colhe, e o item que não era gatilho

- **Status:** aceita · **emendada em 16/10/2026**: "condição nossa" tem duas espécies, e uma delas não é dívida · **emendada em 22/11/2026**: as contas têm um script (S-6) · **emendada em 22/11/2026**: a pasta das rodadas fica fora das contas (S-7)
- **Data:** 16/10/2026
- **Rodada:** R-4
- **Relacionada:** ADR-071 (a lista tem um dono), `docs/dividas.md` e o recorte estreito dele,
  o `README.md` das decisões, o quinquagésimo defeito — *gatilho cuja condição é uma decisão
  nossa é pergunta não feita* — e o trigésimo quinto, que criou o razão

## Contexto

**O razão tem dois comandos.** O recorte estreito é, literalmente,
`grep -rn "Gatilho escrito"` sobre `docs/`, `contracts/`, `CLAUDE.md`, `backend/` e
`frontend/`. A segunda passada busca cinco formas em prosa.

**Isso faz do título `## Gatilho escrito` uma entrada de instrumento**, e não um título. **Tudo
o que está escrito debaixo dele vira linha do razão** — foi a convenção que a R-2 estabeleceu
por uso e que a R-3 aplicou sem exceção. Quem escreve a seção está preenchendo um formulário,
e vinha escrevendo um parágrafo.

**A ADR-071 provou isso contra si mesma, no mesmo dia em que nasceu.** A §4 dela diz, com todas
as letras:

> *"⚠ E isto não vira linha de razão, pelo próprio critério do razão: não existe comando que
> enumere 'regra escrita em dois lugares', então não existe gatilho."*

E a seção `## Gatilho escrito` da **mesma ADR**, setenta e quatro linhas abaixo, termina em:

> *"E o dia em que existir comando que enumere regra duplicada. Aí se sabe se são cinco ou
> cinquenta."*

**O agente seguiu a convenção e aquilo virou a linha 96.** Exatamente a linha que a §4 diz que
não deve existir. *Uma ADR cujo assunto é regra escrita em dois lugares se contradizendo em
duas seções suas.*

**E a linha 96 é pior do que sobrando: ela é o quinquagésimo defeito outra vez.** A condição
dela é *"o dia em que existir comando"* — ou seja, **o dia em que alguém decidir escrever o
comando**. *Gatilho cuja condição é a própria realização não é gatilho: é desejo.*

## Decisão

> **A seção `## Gatilho escrito` é entrada de instrumento. Cada item dela vira uma linha do
> razão, então cada item é uma condição que o mundo cumpre — e nada mais entra ali.**

### 1. O que é condição, e o que não é

| | Exemplo | Vale? |
|---|---|---|
| **o mundo cumpre** | a próxima rota que o gateway ganhar · o primeiro administrador que perguntar quem está pendente · o `order` ganhar código · o dia em que a rota do usuário do token existir | **sim** |
| **nós decidimos** | o dia em que existir comando que enumere X · quando decidirmos onde mora Y | **não** — é pergunta não feita (50º) |
| **não é condição** | ressalva, argumento, frase de efeito, *"e se alguém sentir falta, a falta é de um teste"* | **não** — é corpo |

**O teste de uma linha:** *alguém que não seja nós pode fazer isso acontecer?* Se a resposta é
não, **a entrega é a pergunta**, não a linha.

### 2. A redação da regra mora no `README.md` das decisões

**Não aqui.** É a ADR-071 §1 aplicada à primeira regra escrita depois dela: **a ADR registra a
decisão; o README das decisões é o dono do texto que quem escreve uma ADR lê.** *Escrever a
regra nos dois lugares seria a duplicação que a decisão anterior proíbe, cometida pela decisão
seguinte.*

### 3. Item colhido não se apaga — **o razão estorna**

O corpo de uma ADR não se reescreve (ADR-071 §1), e isso vale para um item de `Gatilho escrito`
que não devia estar ali. **A retirada é por emenda datada**, e o razão **estorna a linha**: ela
não desaparece do histórico; ela passa a dizer que não era gatilho e aponta a emenda.

⚠ **O cabeçalho do razão tem de continuar fechando.** Ele deriva o número de linhas do número
de ocorrências do recorte estreito, com subtrações nomeadas — e uma retirada é uma subtração a
mais. *Instrumento cuja aritmética deixa de fechar para acomodar uma correção para de ser
instrumento.*

**E na revisão.** Quem revisar uma ADR e achar, na seção de gatilhos dela, item que não seja
condição, move o item para o corpo **antes** de commitar — e, se já estiver em `main`, a saída
é emenda e estorno, não edição. *Era o item de gatilho do rascunho desta ADR, e ele mesmo não
passava no teste: a próxima ADR com item errado só acontece se nós a escrevermos.*

### 4. O que esta ADR **não** faz

**Não conserta as seções antigas.** A R-4 **mede** todas as seções `## Gatilho escrito` das
ADRs e classifica cada item; **só a ADR-071 é emendada**, porque é a única cuja frase já estava
medida. ⚠ *Isso não é adiar frase falsa conhecida — é que a medição das outras é justamente o
que não existia até esta rodada.*

**Não mexe no comando.** O recorte estreito continua sendo `grep "Gatilho escrito"`. ⚠ *A saída
não é melhorar o* grep *para ele ignorar prosa: é parar de escrever prosa ali. Comando que
adivinha intenção volta a ser surdo de outro jeito — é a lição do trigésimo quinto.*

**Não cria estado novo no razão além do estorno.** Sim, Não, rearmado, pago, e agora
**estornada**.

## Consequências

**Escrever uma ADR passa a ter uma regra de formulário**, e isso é desconfortável de propósito:
a seção mais retórica do documento era a que alimentava o instrumento mais literal.

**O razão deve encolher.** Quantas linhas dele nasceram de prosa, ninguém sabe — é o que a R-4
mede. *Se a conta for grande, o razão vinha medindo a minha escrita e não a dívida do
projeto.*

**E o número de "gatilhos escritos" deixa de ser uma virtude.** Uma ADR com cinco itens não é
mais cuidadosa do que uma com um: ela pode só ter escrito mais parágrafos debaixo do título
errado.

## Alternativas consideradas

**Mudar o comando para ignorar o que não parece condição.** **Recusada:** `grep` que adivinha
intenção é o trigésimo quinto defeito com outra roupa — ele volta a não achar o que está escrito
de um terceiro jeito, e agora também erra sobre o que achou.

**Trocar o título por um marcador explícito** — `<!-- gatilho -->` por item, por exemplo.
Resolve de verdade e é o que um dia pode valer. **Recusada agora:** mudaria o corpo de dezenas
de ADRs, que não se reescrevem, e o custo recai sobre a decisão errada. *Fica sem gatilho, de
propósito: a condição seria uma decisão nossa.*

**Deixar a linha 96 onde está, por ser inofensiva.** **Recusada:** ela contradiz a §4 da própria
ADR que a gerou, e uma dívida que ninguém pode pagar ensina a ignorar o razão.

**Apagar o item do corpo da ADR-071.** **Recusada por regra**, vinte e quatro horas depois de
esta mesma rodada ter feito dessa regra a única redação do repositório sobre o assunto.

## Gatilho escrito

**Nenhum.** O item que o rascunho desta ADR tinha aqui não passava no teste da §1 — quem escreve
ADR somos nós —, e pela regra da própria ADR foi para o corpo antes do commit: está no fim da
§3.

## Emenda de 16/10/2026 — "condição nossa" tem duas espécies, e uma delas não é dívida

**O censo da R-4 classificou cinco itens como condição nossa, e eles não são a mesma coisa.**
O terceiro item do gatilho da ADR-071 era **pergunta não feita** — a condição era nós
decidirmos escrever um comando, e a primeira emenda de 16/10 à ADR-071 o retirou. **Os outros
dois da mesma ADR são de outra espécie:** *"quando achar uma regra escrita num segundo lugar,
escreva o ponteiro"* é uma **instrução de permanência** — ela não espera um dia; ela vale todos
os dias.

**O teste que as separa:** *existe um dia em que isto deixa de valer?*

- **existe** → é condição, e o razão é o lugar;
- **não existe** → é regra, e o lugar é o `README.md` das decisões.

**Os dois restantes do censo passam no teste, e ficam:** a linha 47 (ADR-059, *"a primeira vez
que alguém quiser semear algo que o agregado não deixa construir"*) deixa de valer na primeira
recusa, ou quando os semeadores saírem; a 55 (ADR-063, *"o dia em que alguém for ler o
`catalog` e se confundir"*) deixa de valer quando a classe se partir. Medido na R-5.

**A §1 desta ADR continua valendo** — item da seção de gatilhos é condição que o mundo cumpre.
O que esta emenda acrescenta é **para onde vai o que não é**: a pergunta, no pacote; a
instrução, no README das decisões. *Nenhuma das duas no razão.*

**Gatilho escrito: nenhum.**

## Emenda de 22/11/2026, S-6 — as contas têm um script

As três contas do razão — o recorte estreito, a segunda passada e as linhas numeradas — passam a
sair também de `scripts/razao.mjs`, versionado. Antes, a contagem das linhas numeradas rodava num
arquivo fora do repositório, na pasta temporária de quem aplicava. **Os dois comandos do cabeçalho
do `docs/dividas.md` continuam sendo a especificação**; o script os reproduz, e divergência entre
os dois é defeito do script. O script também diz quantas linhas do recorte estão em arquivo que o
git não conhece — as cópias de `frontend/.tsbuild/`, que a conciliação já desconta. O corpo fica
como está.

## Emenda de 22/11/2026, S-7 — a pasta das rodadas fica fora das contas

Os pacotes aplicados passam a ficar em `docs/rodadas/` (ADR-084, emenda desta data), e eles citam
os comandos do razão, as fórmulas dos gatilhos e as formas da segunda passada. Os três comandos do
cabeçalho do `docs/dividas.md` ganham `--exclude-dir=rodadas`, e o `scripts/razao.mjs` o mesmo
corte, pelo motivo que o `--exclude=dividas.md` já tinha: instrumento que conta a própria
descrição não está medindo. O corpo fica como está.
