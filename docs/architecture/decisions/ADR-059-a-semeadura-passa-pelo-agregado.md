# ADR-059 — A semeadura passa pelo agregado, e não pelas tabelas

- **Status:** aceita
- **Data:** 10/10/2026
- **Rodada:** I-C
- **Relacionada:** ADR-046 (quem observa a abertura), ADR-049 (o carimbo com a loja
  fechada), ADR-057 (a idempotência natural), `contracts/eventos.md` cláusula 2,
  `docs/como-subir-local.md` §6

## Contexto

O circuito do marco 2 — a loja abre, o evento viaja, o produto volta — **nunca foi visto
de fora**. Existe como asserção de teste no `ReativacaoNoExpedienteIT` e no
`ConsumoDeExpedienteIT`, e desde a I-B existe uma pilha de pé onde ele poderia
acontecer. **Falta estado inicial:** depois de subir, os bancos estão vazios.

**E este projeto já recusou um seed, por escrito.** O `docs/como-subir-local.md` §6 diz:

> *"Um `seed` que escreva direto nas tabelas foi considerado e recusado: ele burlaria as
> invariantes que só existem no agregado, e passaria a ser um segundo lugar onde as
> regras do domínio estão escritas. **Gatilho escrito:** a rodada que der ao `merchant`
> as rotas de escrita. Aí o `seed` é uma sequência de chamadas HTTP, e ela passa pelas
> mesmas regras que um usuário passa."*

**Aquele gatilho não disparou** — o `merchant` continua sem rota de escrita. Esta ADR
existe porque eu quase escrevi o seed sem procurar essa recusa, que é o décimo sétimo
defeito deste projeto; e porque, tendo procurado, **a recusa não cobre o que esta rodada
precisa**.

## Decisão

> **A semeadura roda dentro do próprio serviço, constrói os agregados com os
> construtores de verdade e grava pelo repositório de verdade. Nenhuma linha de SQL,
> nenhum documento montado à mão.**

Cada serviço semeia a sua parte, com um `ApplicationRunner` atrás de uma bandeira
(`delivery.semeadura.ligada`, **falsa por padrão**), e cada um é **idempotente**: se o
identificador da fixture já existe, não faz nada.

**Como ficou no código, e as duas escolhas que o agregado impôs** (I-C, conferido no
código):

- **no `merchant`, a loja nasce por `Estabelecimento.reconstituir`**, e não por
  `novo`. A `novo` sorteia o id, e o `catalog` precisa saber o id da loja sem
  perguntar. As duas passam pelo **mesmo construtor privado** e pelas mesmas
  verificações (M9, M10) — a `novo` só acrescenta o sorteio;
- **no `catalog`, o produto nasce por `rascunho` → `publicar` → `marcar`**, e o id
  dele é sorteado. Aqui a `reconstituir` **não** serve: ela pula as regras de
  publicação (C1, C4, C5), de propósito, porque é leitura e não escrita. Então a
  idempotência do produto é pela **loja**: se ela já tem produto publicado, a semeadura
  não faz nada.

**As duas razões da recusa do §6 ficam atendidas, e é por isso que esta decisão não a
contraria:**

- *"burlaria as invariantes que só existem no agregado"* — não burla: as invariantes são
  justamente o caminho. Produto com preço zero, grupo com `min > max`, loja sem nome: o
  construtor recusa, e a semeadura quebra a subida em vez de gravar algo ilegal;
- *"um segundo lugar onde as regras do domínio estão escritas"* — não há segundo lugar:
  a semeadura **chama** as regras, não as reescreve.

**O que ela não substitui é a rota de escrita.** Uma fixture não é uma API, e ninguém
monta um cardápio por `ApplicationRunner`. **O gatilho do §6 é rearmado abaixo.**

### A semeadura não calcula o dia operacional

O carimbo do produto fica em **trinta dias atrás, em UTC** — e não em "ontem".

Calcular "ontem" exigiria o fuso da loja e a hora de corte das 04:00, e **esse cálculo
tem um dono só: o `merchant`** (ADR-046 §6, e a cláusula 2 do `eventos.md`: *"a
reativação compara, nunca calcula"*). A semeadura obedece à mesma regra: trinta dias
atrás é estritamente menor que qualquer expediente corrente em qualquer fuso, e o
predicado da reativação é `<` (G-C1).

**Custa realismo na fixture e economiza a única duplicação que importaria.**

### A ordem importa, e é a parte fácil de errar

A marca d'água do produtor é `(estabelecimento, expediente)`, e **uma abertura gera um
evento** (`eventos.md`). Se a varredura publicar a abertura **antes** de o produto
existir, o evento é consumido, não acha nada para reativar, e **nenhum segundo evento
vem** — o produto fica `ESGOTADO_HOJE` para sempre, e o circuito não se vê.

> **O produto é semeado antes de a loja passar a estar dentro do horário.**

Na prática: a loja nasce com uma faixa que **ainda não começou**, e a faixa abre sozinha
alguns minutos depois. Assim não há corrida a vencer — há uma espera.

## Consequências

**A prova do circuito deixa de depender de alguém publicar o evento à mão.** A varredura
do `merchant` publica por conta própria, o relay entrega, o consumidor reativa — e o que
se observa é o **documento gravado**, não o log.

**A bandeira é falsa por padrão, e o `.env.example` carrega só o nome.** Semeadura ligada
em qualquer lugar que não seja a máquina de quem desenvolve é dado inventado em produção.

**Os identificadores da fixture são constantes, e isso é um acoplamento declarado.** Dois
serviços — e um terceiro, quando a W-D trouxer o usuário — precisam concordar sobre a
mesma loja sem se falarem, e a forma mais simples é
um punhado de UUIDs fixos, escritos num lugar só e usados apenas quando a bandeira está
ligada. **É uma folga de arquitetura**, e está escrita aqui para não ser descoberta como
surpresa. Em cada serviço eles moram numa classe `Fixture`, no pacote
`infrastructure.semeadura`, ao lado do semeador que os usa.

**Não há usuário semeado, nem vínculo, e é por isso que a loja da fixture não responde
por rota nenhuma.** Usuário é do `identity`, e o circuito não precisa dele: o consumidor
recebe o expediente no próprio evento e não autoriza nada (ADR-054). A leitura do
produto pela API fica para a W-D, que traz o terceiro semeador. **Quando ele vier, a
senha sai de `DELIVERY_SEMEADURA_SENHA`**, nunca de uma constante — e essa variável só
entra no `.env.example` junto com o código que a lê.

**A ordem foi medida, e não só argumentada** (I-C). Com o produto semeado **depois** de a
faixa abrir, a marca d'água ficou em 1 e o outbox em 1 por duas passadas e meia da
varredura, e o produto continuou `ESGOTADO_HOJE` — nenhum segundo evento veio. É o
parágrafo "A ordem importa" acima, visto acontecer.

## Alternativas consideradas

**SQL e `mongosh` direto nas tabelas.** É exatamente o que o §6 recusou, e a recusa está
certa: um `INSERT` de estabelecimento duplica as invariantes do agregado, e no dia em que
uma delas mudar o seed passa a gravar estado que o código não aceitaria mais.

**Esperar as rotas de escrita do `merchant`.** É o gatilho do §6, e é o caminho certo
para o seed *definitivo*. **Recusada para esta rodada** porque daria a uma rodada de
infraestrutura o trabalho de desenhar a API de escrita do `merchant` — que é decisão de
domínio, com permissões, validação e contrato — só para poder ver um produto mudar de
estado. A fixture não compete com aquela API; ela morre quando a API chegar.

**Publicar o evento de abertura à mão no Rabbit.** Era o mais curto, e **prova a metade
errada**: é o que o `ConsumoDeExpedienteIT` já faz, com um broker de verdade. O que falta
provar é a varredura publicando **sozinha**, e isso exige uma loja que abra de verdade.

**Um serviço de semeadura à parte, no compose.** Teria de falar com três bancos e
reimplementar os três agregados, ou chamar três APIs que não existem. É o pior dos dois
mundos.

## Gatilho escrito

**O §6 do `como-subir-local.md` fica com o gatilho dele rearmado:** a rodada que der ao
`merchant` as rotas de escrita troca esta fixture por uma sequência de chamadas HTTP, e
**a semeadura por `ApplicationRunner` sai do repositório nessa rodada**. Ela é andaime,
não fundação.

**E um segundo, que mede o andaime:** a primeira vez que alguém quiser semear algo que o
agregado não deixa construir. Se isso acontecer, a resposta **não** é escrever SQL — é
descobrir por que o domínio recusa aquele estado, porque provavelmente ele está certo.
