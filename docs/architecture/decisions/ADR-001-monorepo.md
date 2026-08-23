# ADR-001 — Monorepo para os oito serviços, o gateway e os contratos

**Status:** Aceita — 16/08/2026 · **formalizada em 23/08/2026**
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
  verificada por build ela é só uma frase.
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

## Pendência que esta ADR cria

**Verificação de dependência entre serviços no build.** Uma regra que falhe se
`:services:X` declarar `:services:Y`. Requisito do marco 1 — sem ela, a decisão
central desta ADR depende de ninguém errar.
