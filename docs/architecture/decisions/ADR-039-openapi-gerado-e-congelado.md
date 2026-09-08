# ADR-039 — O contrato OpenAPI é gerado do código e congelado no repositório

**Status:** Aceita — 08/09/2026
**Relacionada:** ADR-012 (roteamento do gateway), ADR-016 (front mínimo),
ADR-031 e ADR-032 (nome de evento antes do código), ADR-035 (idioma)
**Emenda:** `contracts/README.md`

## Contexto

O `contracts/README.md` promete, com todas as letras:

> `openapi/` — um arquivo por serviço, validado no CI

O diretório `contracts/openapi/` contém **um `.gitkeep` e mais nada**. Zero
arquivos, de zero serviços. Nenhum dos onze workflows em `.github/workflows/`
menciona `openapi`. A validação prometida não existe, e nunca existiu.

**É a sexta peça deste repositório com garantia escrita e execução zero** —
depois do guardião da VM (a tarefa `WSL Ubuntu keepalive`), das políticas de
reinício, do gitleaks dentro de um pipeline que não disparava, dos nove
pipelines quebrados pelo bit de execução do `gradlew`, e do `flyway-core` sem
a integração do Spring.

O achado veio de uma pergunta feita depois do fato: o `POST /api/v1/auth/login`
foi escrito em 07/09 sem passar por contrato nenhum. Não foi exceção — foi o
comportamento de sempre, porque a etapa prometida nunca teve como ser cumprida.

E o custo cresce: hoje são dois endpoints. Depois do `merchant-service`, seis.

### A pergunta que a promessa não respondia

Contrato de **evento** neste projeto precede o código, e há ADR dizendo isso: o
nome é único no repositório (ADR-031) e entra na matriz antes de existir em código
(ADR-032). O `contracts/README.md` deixou implícito que o OpenAPI seguiria a mesma
disciplina — sem nunca dizer quem escreve o arquivo.

Trinta e sete ADRs depois, zero arquivos escritos. A disciplina não falhou por
descuido de uma pessoa; ela falhou todas as vezes.

## Decisão

> **O arquivo OpenAPI é gerado a partir do código, commitado em
> `contracts/openapi/`, e um teste falha quando o gerado difere do commitado.**

Três partes, e a terceira é a que importa:

### 1. Gerado, não escrito à mão

O `springdoc-webmvc` já é dependência dos nove serviços, pelo
`delivery.spring-service-conventions`. A fonte da verdade é o código anotado.

### 2. Commitado em `contracts/openapi/<servico>.json`

O arquivo é artefato versionado, revisável em diff, e consumível por quem não
roda o serviço — o front do marco 3 (ADR-016) e o gateway (ADR-012). Um contrato
que só existe em tempo de execução não serve para revisão nem para geração de
cliente.

### 3. Um teste congela: gerado ≠ commitado é falha de build

O teste levanta o contexto, obtém o documento, e compara com o arquivo do
repositório. Diferente → vermelho, com a mensagem dizendo como regravar.

**É esta parte que separa esta ADR da promessa que ela substitui.** "Validado no
CI" era uma frase; um teste que falha é uma frase que executa. Contrato gerado
sem congelamento envelhece em silêncio — e silêncio é exatamente o modo de falha
das outras cinco peças desta lista.

### 4. O documento não é rota pública

`/v3/api-docs` e a interface do Swagger continuam sob a cadeia de filtros: a
ADR-037 §1 liberou três rotas e esta não é uma delas. O contrato é publicado pelo
**repositório**, que é onde quem precisa dele consegue lê-lo, e não por um
endpoint aberto que descreve a superfície inteira para qualquer um.

## Consequências

**Positivas**

- A promessa passa a executar. Divergência entre contrato e código deixa de ser
  possível — não por disciplina, por build vermelho.
- Nenhuma ferramenta nova: springdoc já está lá, e o teste roda no `test` que já
  existe.
- O diff do contrato aparece na revisão. Mudança acidental de superfície fica
  visível no mesmo lugar em que se revisa código.

**Negativas**

- **O contrato deixa de preceder o código, e portanto não molda o desenho.** É a
  diferença deliberada em relação à ADR-031, onde o nome do evento vem antes
  justamente para ser discutido. Aqui o código decide e o arquivo registra.
  Aceito porque os consumidores são todos *first-party* — a PWA e o gateway — e
  porque uma promessa que nunca executou vale menos que uma garantia mais fraca
  que executa.
- **O teste sobe o contexto**, o que custa segundos por serviço. Nos serviços
  relacionais isso já acontece de qualquer forma, pelo Testcontainers.
- **O formato gerado pode mudar quando o springdoc mudar de versão**, e o teste
  vai ficar vermelho sem que ninguém tenha mexido na API. É ruído previsível, e
  o conserto é regravar o arquivo com a diferença visível no diff — que é
  exatamente a informação que se quer ter ao subir versão.
- **O login ganha o contrato dele retroativamente**, na rodada que implementar
  esta ADR. Não é dívida nova; é a primeira parcela da antiga.

## Alternativas consideradas

- **Escrito à mão antes do código**, como a ADR-031 faz com nome de evento.
  É a leitura mais fiel do que o `contracts/README.md` prometia, e permitiria
  revisar o contrato antes de existir código. Rejeitada por evidência: é a
  disciplina que já falhou em todos os endpoints escritos até hoje, e mantê-la
  exigiria uma validação de conformidade — comparar um arquivo autorado com o
  comportamento real — que é mais difícil de escrever do que comparar dois
  arquivos gerados.
- **Aposentar a promessa** e deixar a documentação viver no springdoc em tempo de
  execução. Honesto e grátis. Rejeitada porque o front do marco 3 e o gateway
  precisam de artefato estável, e adiar isso empurra a decisão para um momento com
  mais endpoints em cima dela.
- **Gerar no CI com o `springdoc-openapi-gradle-plugin`.** Faz a mesma coisa por
  fora do build de testes. Rejeitada porque o plugin precisa **subir a aplicação**
  — que, no `identity-service`, exige Postgres e chave privada. O teste já tem os
  dois.

## Emenda que esta decisão provoca

**`contracts/README.md`** — a linha do `openapi/` passa a dizer o que acontece de
verdade:

```markdown
- `openapi/` — um arquivo por serviço, **gerado do código e congelado**: um teste
  compara o documento gerado com o arquivo commitado e falha se divergirem
  (ADR-039)
```

## O que esta decisão **não** decide

**Versionamento da superfície HTTP.** `/api/v1/` já está no caminho e ninguém
decidiu o que acontece no `v2`.

**Se o front gera cliente a partir do arquivo.** O `.gitignore` já reserva
*"clientes gerados a partir do OpenAPI — regeráveis, não versionados"*, o que
sugere a intenção. Sugere, não decide, e a decisão é do marco 3.

**As rotas públicas de webhook nos outros oito serviços.** Continuam públicas
apenas em comentário de YAML, e nenhuma cadeia de filtros as libera. É a sétima
peça da mesma lista, ainda de pé.
