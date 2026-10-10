# ADR-058 — O limite do contêiner dimensiona o JVM, e a pilha sobe em grupos

- **Status:** aceita
- **Data:** 10/10/2026
- **Rodada:** I-B (medida), escrita na I-C
- **Relacionada:** ADR-051 (a imagem carrega o jar), ADR-021 (oito serviços e um
  gateway), ADR-048 §3 (o bloco de retentativa do YAML), ADR-050 (a documentação viva)

## Contexto

Em 01/10/2026 o comando que subia a pilha derrubou a VM do WSL. A ADR-051 tirou o
compilador da subida — nove builds do Gradle em paralelo tinham levado o `vmmemWSL` a
11,8 GB — e **a subida continuou matando a VM**: o `up -d` voltava com sucesso em 88 s,
os quinze contêineres partiam juntos, e a VM caía cerca de um minuto depois, com os nove
serviços ainda no meio da partida. Nenhum chegou a registrar `Started`.

A queda levava junto a tarefa agendada `WSL Ubuntu keepalive`; a distribuição passava a
reiniciar a cada comando `wsl`; e a cada reinício o `restart: unless-stopped` subia os
quinze de novo. **Era um laço, e não parava sozinho.**

A causa **não estava medida** — o pico do `vmmemWSL` durante a subida não havia sido
registrado. A I-B mediu, e esta ADR é escrita com os números dela.

### O que a medição mostrou

`java -XX:+PrintFlagsFinal -version`, na imagem do `identity` e no `catalog` pelo
compose, numa VM de **11.960 MB**:

| Configuração | `MaxHeapSize` | |
| --- | --- | --- |
| **sem limite, sem percentagem** — como era | **3.137.339.392** | um quarto da VM, **por JVM** |
| `mem_limit: 512m` **e** `MaxRAMPercentage=75` | **402.653.184** | 384 MiB |
| `mem_limit: 512m`, sem a percentagem | 134.217.728 | 128 MiB — um quarto do limite: apertado |
| `MaxRAMPercentage=75`, **sem** limite | **9.412.018.176** | três quartos da VM |

**Um JVM sem limite declarado mede a memória que vê** — a da VM — e escolhe o heap
máximo como fração dela. Nove deles eram **27 GB de heap possível numa VM de 12**. Eles
não reservam tudo de uma vez, e é por isso que a pilha parecia subir e morria depois: o
limite só aparece quando alguém aloca.

**A quarta linha é a que não se adivinha.** A percentagem sem o limite é *pior* que não
ter nenhum dos dois — ela troca um quarto da VM por três quartos.

## Decisão

> **Todo serviço Java no compose declara `mem_limit` e
> `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`. Os dois, sempre — nunca um só.**

**Sem `-Xmx`.** Com o limite declarado, a percentagem se reajusta sozinha quando o
limite mudar; um `-Xmx` fixo passa a mentir no dia em que alguém mexer no limite, e
mente em silêncio.

### A pilha sobe em grupos, e o perfil `marco2`

Quatro grupos, cada um esperando o anterior ficar saudável com `--wait`:

1. a infraestrutura que o marco 2 usa: `postgres`, `mongodb`, `mongo-init`, `rabbitmq`;
2. o **`identity` sozinho**;
3. `merchant` e `catalog`, os dois lados do circuito do expediente;
4. o `gateway`.

**O `identity` sobe sozinho por diagnóstico, não por memória.** Todos os outros validam
token pelo JWKS dele; subindo junto, *"não subiu"* e *"subiu e não achou o JWKS"* chegam
misturados, e são consertos diferentes.

**O perfil `marco2` existe porque subir o que não participa da prova gasta a memória que
está no caminho dela.** Os cinco esqueletos ligam uma JVM e não fazem mais nada; o
`redis` e o `minio` não têm usuário no repositório — a ADR-011 decidiu Caffeine, em
memória. São sete contêineres em vez de quinze.

### `restart: "on-failure:3"`, e não `unless-stopped`

**Foi a política de reinício que transformou o incidente em laço.** Três tentativas e
para.

## Consequências

**A pilha sobe, e está medido.** Com a infraestrutura já de pé: `identity` saudável em
**34 s**, `merchant` e `catalog` em **45 s**, `gateway` em **21 s**. Pico do `vmmemWSL`
de **3.403 MB** com os sete de pé, contra os 11,8 GB que a derrubavam. Recriados de uma
vez — depois de uma troca de senha, o compose recria o que mudou —, os sete voltaram
saudáveis em **80 s**. O commit do Windows ficou em 45,0 de 50,1 GB.

**Os 512 MiB não são um número medido, são um teto escolhido.** O que foi medido é a
ocupação com ele: `merchant` 279–293 MiB, `identity` 268–285, `catalog` 220–233,
`gateway` 212–221 — todos em torno de 55% do limite. **Há folga, e ela é de propósito**:
o número que importa é o heap máximo que o JVM escolhe, e é esse que o limite governa.

**Os cinco esqueletos ficam como estão**, com `unless-stopped` e sem limite. Eles não
sobem no `marco2`, e quem rodar `--profile services` rearma o laço inteiro. **Está
anotado, e é dívida.** *Gatilho escrito: a primeira rodada que precise de um dos cinco
de pé.*

**E saúde não prova a pilha.** Medido na mesma rodada: com o `JWT_JWKS_URI` apontado para
`localhost`, o contêiner continua `healthy` e a rota protegida responde **500** — o
Spring Security trata falha ao *buscar* o JWKS como `AuthenticationServiceException`, não
como token inválido. **A prova de que a pilha está de pé é uma rota protegida pelo
gateway com token emitido pelo `identity`**, nunca `/actuator/health`.

## Alternativas consideradas

**Aumentar a memória da VM do WSL.** Trocaria 12 GB por mais, e nove JVMs continuariam
pedindo um quarto do que vissem — o problema é proporcional, então aumentar a VM aumenta
o apetite na mesma medida. **Recusada:** não é escassez, é a conta.

**`-Xmx` em cada serviço.** Funciona hoje e mente amanhã, pela razão da Decisão.

**Deixar os quinze subindo juntos e aceitar a lentidão.** Não era lentidão: a VM caía.

**Um `docker-compose.override.yml` local com os limites.** Deixaria o compose versionado
sem a decisão, e o próximo a clonar o repositório repetiria o incidente de 01/10. **A
decisão é do compose**, e o override fica para o que é de cada máquina.

## Gatilho escrito

**A primeira rodada que precise de um dos cinco esqueletos de pé.** Aí eles ganham
`mem_limit`, a percentagem e `on-failure:3`, e o perfil `services` deixa de ser o
caminho que rearma o laço.

**E um segundo:** o primeiro serviço cuja ocupação passar de 80% dos 512 MiB. O teto é
escolhido, não medido, e é esse número que diz quando reescolhê-lo.
