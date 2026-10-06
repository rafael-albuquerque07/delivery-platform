# ADR-051 — A imagem carrega o jar, não o compilador

- **Status:** aceita
- **Data:** 06/10/2026
- **Rodada:** I-A
- **Relacionada:** ADR-001 (monorepo), ADR-040 (um, e só um, módulo compartilhado),
  ADR-041 (peça escrita para um marco distante)

## Contexto

O `docker-compose.yml` está no repositório desde o primeiro dia. Em 01/10/2026,
na primeira vez que alguém tentou subir a pilha inteira, descobriu-se que **oito
das nove imagens de serviço nunca tinham sido construídas**. A única que existia
era a do `settlement-service`, construída avulsa.

A tentativa falhou, e a forma da falha é a razão desta ADR. O `build:` de cada
serviço tem `context: ./backend`, e cada Dockerfile copia esse diretório inteiro
e roda um build do Gradle dentro da imagem:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS build
COPY . .
RUN ./gradlew :services:<nome>:bootJar --no-daemon
```

O `docker compose --profile core --profile services up -d` constrói as nove em
paralelo. São nove JVMs de Gradle simultâneas, cada uma resolvendo o grafo do
monorepo inteiro desde o zero, sem cache compartilhado entre elas — porque cada
build acontece num contexto isolado. Numa VM do WSL de 12 GB, o `vmmemWSL`
chegou a 11.894 MB, o daemon do Docker parou de responder em
`127.0.0.1:2375` e o WSL travou com `Wsl/Service/0x8007274c`.

Duas coisas, portanto, estão erradas ao mesmo tempo:

1. **o custo**: um build completo da pilha custa nove builds completos do
   monorepo, e nenhum deles reaproveita o trabalho do outro nem o trabalho que o
   `./gradlew check` já fez no disco do desenvolvedor;
2. **o paralelismo**: o comando documentado para subir o ambiente é o comando
   que derruba a máquina.

E há um terceiro fato, que é o que torna isto uma ADR e não um ajuste: **o
caminho do contêiner nunca foi percorrido.** Um `docker-compose.yml` que ninguém
rodou é intenção, não peça — e ele estava no repositório dando a impressão
contrária.

## Decisão

**A imagem de serviço recebe um jar já construído. Ela não contém o compilador,
não contém o Gradle e não contém o código-fonte.**

O build passa a ter duas etapas, nesta ordem, e a ordem é obrigatória:

1. **no host**, uma vez: `./gradlew bootJar`, de dentro de `backend/`, para os
   nove módulos executáveis — os oito serviços e o `gateway`. Um daemon do
   Gradle, um grafo resolvido, o cache de build do repositório reaproveitado;
2. **no Docker**: nove imagens de um estágio só, cada uma copiando o seu
   `build/libs/app.jar` para cima de `eclipse-temurin:21-jre-alpine`. O estágio
   final é o que já existia — usuário `app`, `HEALTHCHECK` na prontidão,
   `ENTRYPOINT` — e só a origem do `COPY` muda.

O jar já se chamava `app.jar` desde o primeiro commit, pelo plugin de convenção
`delivery.spring-service-conventions`, e o `COPY` sempre o nomeou por inteiro.
O que esta ADR acrescenta é **desabilitar o jar simples** (`-plain.jar`) no
mesmo plugin: nenhum módulo executável é biblioteca de ninguém — a ADR-040
permite como dependência compartilhada só o `:value-types`, que aplica outro
plugin —, então o jar simples não tem consumidor, e `build/libs` passa a ter
exatamente um arquivo.

O contexto de build continua sendo `backend/`, compartilhado pelas nove imagens,
e um `backend/.dockerignore` invertido — exclui tudo, reinclui
`**/build/libs/app.jar` — faz o Docker receber só os jars.

O roteiro de subida vive em `docs/como-subir-local.md`, com os tempos medidos, e
em `scripts/subir-local.sh`, que executa as etapas na ordem. **A ordem deixa de
viver numa conversa.**

## Consequências

**O `docker compose build` sozinho deixa de produzir uma imagem.** Sem o
`bootJar` antes, o `COPY` falha. Isso é aceito e é o preço desta ADR: a promessa
de "um comando só" já era falsa — ela derrubava a máquina — e agora ela é falsa
de um jeito que diz o motivo na primeira linha do erro.

**O que está na imagem é o que estava no disco.** O jar não é construído a
partir de um commit; é construído a partir da árvore de trabalho. Construir com
mudanças não commitadas produz uma imagem que não corresponde a nenhum commit.
Para o ambiente local isso é o comportamento desejado — é justamente para isso
que o loop existe. Para qualquer coisa que não seja local, o build tem de partir
de uma árvore limpa.

**O CI, se construir imagem, passa a ter uma ordem.** O `bootJar` vem antes do
`docker build`. Hoje nenhum workflow constrói imagem — nenhum dos quatorze cita
`docker`, `buildx` ou `bootJar` —, e o dia em que um construir, esta ADR é a
instrução.

**O rebuild de uma imagem passa a ser um `COPY`.** Os tempos, antes e depois,
estão medidos no `como-subir-local.md`.

**A imagem final fica menor e com menos superfície.** Sai o JDK, sai o Gradle,
sai o código-fonte que o `COPY . .` levava para dentro do estágio de build.

## Alternativas consideradas

**Uma imagem construtora comum.** Um `Dockerfile.builder` que roda o build dos
nove `bootJar` uma vez; nove Dockerfiles finos que fazem
`COPY --from=delivery-builder`. Mantém o build dentro do Docker e seria a
escolha se a máquina de build não tivesse JDK. **Recusada por não entregar o que
prometia:** ela também exige um comando anterior — construir a imagem
construtora —, então o `up -d` sozinho continua não funcionando, e em troca
aparece uma imagem a mais, cujo único consumidor é o próprio build.

**Montagem de cache do BuildKit e `COPY` em camadas.** Separar o `COPY` dos
arquivos de build do `COPY` do código-fonte e usar
`--mount=type=cache,target=/root/.gradle`. Resolveria o cache, mas **não resolve
o paralelismo**: as nove JVMs continuam subindo juntas no `up -d`, e o problema
que derrubou a máquina está no número de processos, não no cache deles.

**Limitar o paralelismo e deixar o resto como está.** Construir uma imagem por
vez, mantendo o build dentro de cada uma. Foi o que funcionou na emergência de
01/10. **Recusada pelo custo do loop**, medido no `como-subir-local.md`, e
porque o número não cai na segunda vez: o `COPY . .` invalida o cache a cada
mudança de arquivo. Para um projeto em que cada rodada mexe no código, é um
caminho que ninguém percorre — e a prova é que ninguém percorreu.

**Registro local, imagem publicada.** Fora de escopo: este é um ambiente de uma
máquina, sem implantação.

## Gatilho escrito

O dia em que as imagens tiverem de ser construídas num lugar que **não tem o
build do Gradle disponível** — um executor de CI apenas com Docker, uma máquina
de implantação sem JDK. Aí a imagem construtora volta à mesa, e é nela que o
build entra, não de volta nos nove Dockerfiles.
