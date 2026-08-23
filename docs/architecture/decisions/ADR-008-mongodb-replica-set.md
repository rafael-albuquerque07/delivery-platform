# ADR-008 — MongoDB como replica set de nó único em desenvolvimento

**Status:** Aceita — 16/08/2026 · **formalizada em 23/08/2026**
**Relacionada:** ADR-007 (Mongock), ADR-014 (sem H2), ADR-017 (MongoDB como aprendizado)
**Invariante do `CLAUDE.md`:** 7 (quem publica precisa de outbox)
**Já implementada** no esqueleto — esta ADR registra o porquê, que faltava

## Contexto

A invariante 7 é curta e cara: **quem publica precisa de outbox**. Gravar o
documento de negócio e a linha do outbox tem que ser **um** ato — ou os dois
acontecem, ou nenhum. Sem isso existem dois defeitos, e ambos são silenciosos:
evento publicado para um pedido que não foi gravado, ou pedido gravado cujo
evento nunca sai.

E aqui está o detalhe que pega quem vem do mundo relacional: **MongoDB em modo
avulso não faz transação multi-documento.** A transação exige replica set — ou
cluster fragmentado, que é exagero maior ainda.

Isso vale para `catalog-service` e `conversation-service`, os dois documentais
(ADR-021). Não é opcional para eles: os dois publicam evento.

## Decisão

O MongoDB sobe como **replica set de nó único** em desenvolvimento e em teste.

```yaml
mongodb:
  command: ["--replSet", "rs0", "--bind_ip_all"]

mongo-init:
  entrypoint: ["sh", "/init.sh"]     # roda rs.initiate()
  depends_on:
    mongodb: { condition: service_healthy }
```

Os serviços documentais dependem do `mongo-init` concluir, não só do MongoDB
estar de pé — sem `rs.initiate()`, o nó existe e a transação falha.

### A armadilha da string de conexão

```
mongodb://mongodb:27017/catalog_db?replicaSet=rs0
                                   ^^^^^^^^^^^^^^
```

**Sem `?replicaSet=rs0` o driver conecta em modo avulso**, mesmo com o servidor
configurado como replica set. E o erro não aparece no boot: aparece na primeira
transação, que pode ser semanas depois, possivelmente em produção. É a falha mais
cara desta decisão e a mais fácil de cometer.

### Em teste

O `MongoDBContainer` do Testcontainers já sobe em replica set por padrão. Não é
preciso configurar nada — mas é preciso **saber**, para não replicar aqui uma
configuração de compose que o contêiner já resolve.

### Em produção é outra topologia

Três nós, replica set de verdade. Um nó só não é redundância: é a topologia
mínima que habilita transação, e nada além disso.

## Consequências

**Positivas**

- O Outbox funciona nos serviços documentais com a mesma garantia dos
  relacionais. A invariante 7 vale para os oito, sem exceção envergonhada.
- Um contêiner só, sem custo de memória de três nós numa máquina de trabalho.
- O caminho para produção é mudança de topologia, não de código nem de padrão de
  acesso.

**Negativas**

- **O nome engana.** "Replica set" soa a redundância, e com um nó não há
  nenhuma: se o contêiner cair, cai tudo. É desenvolvimento, e está escrito aqui
  para ninguém confundir.
- **A string de conexão vira ponto único de falha silenciosa.** Toda nova
  configuração precisa do parâmetro. Merece linha nas armadilhas e, idealmente,
  um teste de fumaça que abra uma transação no startup de cada serviço
  documental.
- **Mais um passo no Compose**, com dependência de ordem. Quem subir só o
  `mongodb` sem o `mongo-init` tem um banco que parece funcionar até a primeira
  transação.
- **Desenvolvimento e produção divergem na topologia**, o que contraria o
  espírito da ADR-014 — mesma tecnologia em todo lugar. A diferença aqui é de
  **durabilidade**, não de semântica: a transação se comporta igual com um nó ou
  com três. É a divergência aceitável, e a ADR-014 não é violada porque a
  tecnologia é a mesma imagem.

## Alternativas consideradas

- **Modo avulso, com outbox de melhor esforço** — gravar o documento e depois a
  linha do outbox, sem transação. Rejeitada: quebra a invariante 7, e os dois
  modos de falha são silenciosos. É exatamente o problema que o padrão Outbox
  existe para resolver; adotá-lo sem atomicidade é encenação.
- **Outbox dos serviços documentais num banco relacional à parte.** Rejeitada:
  daria dois bancos por serviço e uma transação distribuída entre eles para
  resolver um problema de transação local. Pior em todos os eixos.
- **Três nós também em desenvolvimento.** Rejeitada: três vezes a memória, sem
  nenhum ganho local. A alta disponibilidade não é o que se está exercitando na
  máquina de trabalho.
- **Não usar MongoDB.** A alternativa honesta, e já respondida pela ADR-017: o
  MongoDB está aqui como decisão de aprendizado, deliberada e registrada. Se essa
  premissa cair, esta ADR cai junto — e o `catalog` e o `conversation` viram
  relacionais.
