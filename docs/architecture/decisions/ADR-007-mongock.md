# ADR-007 — Mongock para versionamento de esquema no MongoDB

**Status:** Aceita — 16/08/2026 · **formalizada em 23/08/2026**
**Relacionada:** ADR-008 (replica set), ADR-014 (sem H2), ADR-017 (MongoDB como aprendizado)
**Já implementada** no esqueleto — esta ADR registra o porquê, que faltava

## Contexto

O MongoDB não tem esquema, e é justamente por isso que precisa de migração
versionada. Sem esquema declarado, o que existe no banco é o que alguém criou —
e "alguém criou" é a definição de ambiente que diverge.

Três coisas precisam nascer versionadas nos serviços documentais:

- **Índices.** Um índice criado à mão em desenvolvimento não existe em produção,
  e a diferença só aparece quando a consulta fica lenta com volume.
- **Validadores de esquema** (`$jsonSchema`). O Mongo aceita gravar qualquer
  coisa; o validador é a única defesa contra documento malformado.
- **Transformação de dado existente.** Renomear campo, extrair subdocumento,
  preencher default retroativo.

Os serviços relacionais já resolvem isso com Flyway e `ddl-auto: validate`
(ADR-014). O problema não é diferente por ser documental; só a ferramenta é.

## Decisão

**Mongock**, com `changeUnit` versionado em código, escaneado por pacote.

```yaml
mongock:
  migration-scan-package:
    - com.deliveryplatform.<servico>.infrastructure.persistence.migration
```

Cada unidade é uma classe anotada, com identificador, ordem e autor, contendo
execução e reversão. Roda no **startup**, antes de o serviço aceitar tráfego, e
o Mongock adquire **lock distribuído** para que duas instâncias subindo juntas
não migrem ao mesmo tempo.

**Nenhum comando manual no shell do Mongo.** Índice criado com `mongosh` numa
sessão de depuração é dívida invisível: funciona na máquina de quem criou e não
existe em nenhum outro lugar. Se precisou de índice para investigar, o índice
vira `changeUnit` antes do commit.

Vale para índice, validador e transformação — os três. Não existe categoria
"pequeno demais para virar migration".

## Consequências

**Positivas**

- Ambiente documental para de divergir: o que existe no banco é o que está no
  código, em qualquer máquina.
- Simetria com o lado relacional. Quem entende Flyway entende isto, e a regra do
  `CLAUDE.md` é a mesma frase para os dois.
- Reversão declarada obriga a pensar no caminho de volta na hora de escrever a
  ida, que é quando ainda é barato.
- O `changeUnit` tem acesso ao contexto da aplicação — dá para usar repositório e
  serviço de domínio numa transformação complexa, em vez de reescrever a regra em
  script.

**Negativas**

- **Dependência de terceiro com ciclo próprio.** O Mongock já quebrou API entre
  versões maiores; uma atualização do Spring Boot pode forçar atualização dele.
- **Migração em Java é mais poderosa e mais fácil de errar** que SQL declarativo.
  Um `changeUnit` pode fazer qualquer coisa, inclusive o que não devia.
- **Reversão declarada raramente é testada.** Mesma armadilha do `undo` do
  Flyway: existe, dá conforto, e ninguém sabe se funciona. Não conte com ela para
  desfazer produção.
- **Startup mais lento**, e o lock pode ficar preso se uma instância morrer no
  meio da migração. Tem tempo limite, mas quem opera precisa saber que existe.

## Alternativas consideradas

- **`auto-index-creation: true` do Spring Data**, criando índice a partir de
  `@Indexed`. Rejeitada, e é a mais tentadora por ser grátis: cria índice no
  startup sem versão, sem ordem e sem registro do que já rodou — e **não faz mais
  nada além de índice**, deixando validador e transformação de fora. O Spring
  Data desliga isso por padrão desde a versão 3.0 exatamente por esses motivos.
- **Scripts `.js` versionados, executados pelo CI.** Rejeitada: ficam fora do
  ciclo de vida da aplicação, e nada garante que rodaram antes de o serviço
  subir. O acoplamento certo é "o serviço não atende antes de o esquema estar
  correto", e isso só o startup garante.
- **Liquibase com a extensão de MongoDB.** Alternativa legítima, e mais familiar
  para quem vem do mundo relacional. Rejeitada por preferir uma ferramenta
  Java-nativa, integrada ao ciclo do Spring Boot, cujo `changeUnit` enxerga o
  contexto da aplicação.
- **Nenhuma migração, esquema por convenção.** Rejeitada: é o estado de que esta
  ADR existe para sair.
