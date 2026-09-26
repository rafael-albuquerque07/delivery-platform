# ADR-001 — Monorepo para os oito serviços, o gateway e os contratos

**Status:** Aceita — 16/08/2026 · **formalizada em 23/08/2026** · **emendada em
26/09/2026**: a regra existe no build (ver "Emenda de 26/09/2026")
**Relacionada:** ADR-002 (banco por serviço), ADR-021 (catálogo de serviços)
**Em vigor desde o primeiro commit** — esta ADR registra o porquê, que faltava

## Contexto

Microsserviços podem morar num repositório ou em oito. A escolha parece
organizacional e não é: ela decide o custo de mudar duas coisas ao mesmo tempo, e
decide o que impede um serviço de invadir o outro.

Este projeto tem uma pessoa. Oito repositórios significariam oito pipelines, oito
lugares para atualizar a versão do Spring, oito históricos para cruzar quando
algo quebra — e uma renomeação de evento virando três pull requests com problema
de ordem entre elas.

## Decisão

**Um repositório**, com fronteiras internas explícitas.

```
delivery-platform/
├── backend/            build Gradle multi-projeto
│   ├── build-logic/    convention plugins — configuração compartilhada
│   └── services/       um módulo Gradle por serviço
├── contracts/          OpenAPI, AsyncAPI e JSON Schema dos eventos
├── docs/               PRD, domínio, ADRs, runbook
├── infra/              Postgres, Mongo, observabilidade
└── .github/workflows/  um pipeline por serviço, com filtro de caminho
```

### O que torna isto microsserviços e não um monólito com pastas

Três coisas, e nenhuma delas é o diretório:

1. **Processo próprio.** Cada serviço tem `bootJar`, `Dockerfile` e porta.
2. **Banco próprio.** ADR-002.
3. **Pipeline próprio, com filtro de caminho.** Alterar o `catalog-service` não
   dispara o CI do `order-service`, e cada um pode ser publicado sozinho.

### A regra que impede o monorepo de virar monólito

> **Nenhum módulo de serviço declara outro módulo de serviço como dependência.**

É a fronteira de verdade. Num monorepo, `order-service` importar uma classe de
`merchant-service` é só um `implementation(project(":services:merchant-service"))`
— duas linhas, resolve o problema de hoje, e destrói a independência que
justifica a arquitetura inteira. Integração é por **API ou evento**, sempre.

Serviço só depende de `build-logic` e de bibliotecas externas.

**Isso não está verificado hoje.** O `HexagonalArchitectureTest` checa camadas
*dentro* de um serviço; não existe nada checando dependência *entre* serviços.
Precisa de uma verificação no build que falhe se um `:services:*` depender de
outro — requisito do marco 1, listado como pendência abaixo.

> **Resolvido em 26/09/2026.** A verificação existe e roda no `check` de todo
> módulo — ver "Emenda de 26/09/2026", no fim desta ADR.

> **Emendado pela ADR-040 (08/09/2026).** Existe **um** módulo compartilhado que
> não é serviço: `:value-types`, com tipos de valor sem framework, sem estado e
> sem regra de negócio de serviço nenhum. Serviço passa a depender de
> `build-logic`, de `:value-types` e de bibliotecas externas — e de nada mais. A
> regra de entrada do módulo está na ADR-040, e a metade dela que uma máquina
> confere é verificada por ArchUnit dentro dele.
>
> A pendência abaixo fica **mais** urgente, não menos: a verificação passa a
> precisar distinguir a dependência permitida da proibida.

### Contrato mora junto

`contracts/` fica no mesmo repositório porque o esquema de um evento muda com o
código que o publica. Separá-los criaria uma dependência versionada e um ciclo de
release para algo que só faz sentido em conjunto — e a primeira divergência entre
o esquema publicado e o evento real apareceria em produção.

## Consequências

**Positivas**

- **Mudança atravessada em um commit.** Renomear um campo de evento toca
  produtor, consumidor e contrato de uma vez, e o CI valida tudo junto.
- **Uma configuração compartilhada.** `build-logic` é o único lugar onde a versão
  do Java, o BOM do Spring, o ArchUnit e o Testcontainers são declarados.
- **Um histórico.** `git log` conta a história inteira, incluindo por que dois
  serviços mudaram juntos.
- Para uma pessoa, a diferença de sobrecarga entre um e oito repositórios é a
  diferença entre trabalhar e administrar.

**Negativas**

- **O monorepo torna o acoplamento barato**, e acoplamento barato é como
  microsserviços morrem. A regra acima existe por isso, e enquanto não for
  verificada por build ela é só uma frase. *(Verificada por build desde
  26/09/2026.)*
- **Tenta a publicar tudo junto.** "Está tudo no mesmo repositório, vamos subir
  tudo" desfaz a implantação independente sem ninguém decidir isso. Os filtros de
  caminho por pipeline são a defesa.
- **O repositório cresce** e o `git log` mistura assuntos. Aceito: nesta escala,
  irrelevante.
- **Não escala para times.** Com equipes separadas por serviço, um repositório
  vira disputa por revisão e por `main`. Se isso acontecer, esta ADR é a primeira
  a ser revisitada — e a saída existe, porque os módulos já são independentes.

## Alternativas consideradas

- **Um repositório por serviço.** O desenho canônico de microsserviços, e o certo
  quando há times independentes. Rejeitado: oito vezes a sobrecarga para uma
  pessoa, e mudança atravessada — que num sistema com Saga e eventos é comum —
  vira coordenação de pull requests com ordem obrigatória.
- **Monorepo com build único, sem módulo por serviço.** Rejeitado: é um monólito
  com pastas. Sem `bootJar` e banco próprios, a fronteira é decoração.
- **Polirepo com um repositório compartilhado de contratos.** Rejeitado:
  transforma o contrato numa dependência versionada com ciclo próprio, e a
  pergunta "qual versão do contrato este serviço implementa" passa a ter resposta
  diferente em cada repositório.
- **Submódulos do Git.** Rejeitado sem muita discussão: junta o pior dos dois —
  a sobrecarga do polirepo com a confusão de estado do monorepo.

## Pendência que esta ADR cria — **resolvida em 26/09/2026**

~~**Verificação de dependência entre serviços no build.** Uma regra que falhe se
`:services:X` declarar `:services:Y`. Requisito do marco 1 — sem ela, a decisão
central desta ADR depende de ninguém errar.~~

Resolvida pela emenda abaixo.

## Emenda de 26/09/2026 — a regra existe, e roda no `check`

A ADR-001 criou a própria pendência, com as palavras riscadas acima. Ela ficou
aberta por um mês, e nesse mês nada errou — o repositório tem **exatamente uma**
declaração `project(...)`, a do `merchant-service` para `:value-types`. É
justamente essa a situação em que uma regra é barata de escrever e fácil de
adiar para sempre: não há o que consertar, só o que impedir.

### A regra

`VerificarDependenciaEntreModulos`, em `build-logic`
(`com.deliveryplatform.buildlogic`), registrada por `delivery.java-conventions` —
o plugin que **todo** módulo do backend alcança, de modo que a regra vale também
para o módulo que alguém criar amanhã sem ler esta ADR. Ela lê as dependências de
projeto declaradas em qualquer configuração e falha se alguma não for
`:value-types`. Roda nos dez módulos: os oito serviços, o `gateway` e o próprio
`:value-types`.

Pendurada no `check`. Uma verificação que dependa de alguém lembrar de executá-la
não é diferente de um comentário pedindo cuidado.

O pacote **não** se chama `build`: o `.gitignore` da raiz ignora todo diretório
`build/`, e com esse nome a classe nunca teria entrado no Git — a regra passaria
na máquina de quem a escreveu e o build quebraria em todo clone.

### Por que task de build, e não teste

A ADR-040 virou teste — o `TiposDeValorNaoConhecemFrameworkTest`, com ArchUnit —
e seria natural tentar o mesmo aqui. Não serve, por três motivos:

1. **Aresta não é classe.** A ADR-040 proíbe *imports dentro de um módulo*, e
   import está no bytecode. Esta proíbe *arestas entre módulos*, e aresta está no
   arquivo de build. Um `implementation(project(":services:x"))` declarado e
   ainda não usado por nenhuma classe é invisível para o ArchUnit, e já é a
   violação: a partir dele os dois módulos compilam juntos.
2. **Não há onde o teste morar.** Para enxergar todos os módulos, ele teria de
   estar num módulo que depende de todos — que é exatamente o que a regra
   proíbe.
3. **Não precisa compilar para decidir.** A task lê o grafo; o teste só existiria
   depois de compilar. (Nada a *obriga* a rodar antes do `compileJava` — na prova
   ela foi a primeira a falhar, por ordem de agendamento.)

A informação está no grafo do Gradle. A verificação mora onde a informação está.

### O que é permitido, e por que a lista importa

Uma aresta: `:value-types`, aberta pela ADR-040.

Esta é a parte que tornou a regra necessária. Enquanto a ADR-001 dizia "nenhum
módulo", ela era autoverificável por leitura — qualquer `project(...)` era
violação, e ninguém precisava lembrar de qual. Desde a ADR-040 a regra virou
**"um, e só um"**, e uma lista de exceções com um item é uma lista de exceções:
cresce por argumento razoável, um item de cada vez, e o segundo item sempre
parece tão justificado quanto o primeiro.

A mensagem de falha diz o que fazer no lugar — porta HTTP, evento, ou
`:value-types` quando for tipo de valor —, e termina dizendo que a terceira saída
é emendar esta ADR. É deliberado: se alguém precisar mesmo de uma segunda
aresta, o caminho é abrir a decisão, não contornar o build.

### A prova

Em 26/09/2026, com `implementation(project(":services:identity-service"))`
acrescentado de propósito ao `catalog-service` e desfeito em seguida, o
`./gradlew :services:catalog-service:check` falhou com:

```
> ADR-001 violada em :services:catalog-service

    Dependência de módulo declarada e não permitida:
        :services:identity-service

    Permitido: :value-types — e nada mais.
    ...
    O que fazer, em ordem de preferência:
      1. Chamar o outro serviço pela porta dele (HTTP) ou reagir a um
         evento — é assim que serviço fala com serviço aqui.
      2. Se o que se quer compartilhar é tipo de valor sem estado e
         sem framework, ele pertence a :value-types (ADR-040).
      3. Se nenhuma das duas serve, o caso é emendar a ADR-001 — e
         essa é uma decisão de arquitetura, não um ajuste de build.
```

E, antes disso, a prova de que ela **enxerga**: rodada em todos os módulos, a do
`merchant-service` relatou `[:value-types]` e as outras nove, `[nenhum módulo]`.
Uma regra que lesse uma lista vazia passaria em tudo — inclusive numa prova de
violação feita errado.

### Consequência negativa

**A regra nunca falhou em código de verdade.** Ela foi provada disparando contra
uma violação criada de propósito e desfeita em seguida, e não por uma suíte que a
exercite a cada build.

Isso é *"peça que nunca rodou não é peça, é intenção"* aplicado a esta própria
ADR, e fica registrado como tal. **Gatilho escrito:** o dia em que houver uma
segunda regra de build. Aí o `build-logic` passa a merecer suíte própria,
pendurada no `check` da raiz, e as duas são exercitadas juntas — hoje, uma suíte
para uma regra seria mais cerimônia do que verificação.
