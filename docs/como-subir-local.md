# Como subir o ambiente local

Este documento existe porque, em 01/10/2026, a receita de subir a pilha vivia
numa conversa e, por isso, não existia. Oito das nove imagens de serviço nunca
tinham sido construídas, e o comando que a gente achava que subia o ambiente
derrubou a máquina.

**Todo tempo abaixo foi medido nesta máquina**, em 06/10/2026 (build e imagens)
ou em 10/10/2026 (a subida em grupos), com o comando ao lado. Se você medir
outro, troque — número lembrado não serve. **O marco 2 subiu de pé pela primeira
vez em 10/10/2026**, em quatro grupos e com limite de memória por contêiner
(seção 2.4). Os quinze contêineres juntos continuam sem ter subido nenhuma vez.

---

## 0. Onde rodar cada coisa

São dois lados, e cada comando tem o seu:

| Lado | O que roda ali | Por quê |
| --- | --- | --- |
| **Windows** (PowerShell ou Git Bash) | o Gradle — `bootRun`, `bootJar`, `check` | É o ambiente verificado do `CLAUDE.md`. O Ubuntu **não tem JDK**, e o build não provisiona um sozinho (não há resolvedor de toolchain) |
| **WSL** (Ubuntu) | o `docker` e o `docker compose` | O motor mora no WSL2, sem Docker Desktop, e **não há `docker.exe` no Windows** — o `CLAUDE.md` conta por quê |

Para entrar no WSL:

```powershell
wsl
```

```bash
cd /mnt/c/dev/delivery-platform
```

**Nada de `wsl -d Ubuntu -- bash -c "…$var…"` a partir do PowerShell.** O
PowerShell mexe no `$` antes de o bash vê-lo, e o comando roda medindo outra
coisa. É armadilha do `CLAUDE.md`; entre no WSL e rode lá dentro, ou ponha o
comando num arquivo `.sh`.

**Todo `docker compose` leva os perfis.** Todos os serviços do compose têm
`profiles:`: sem perfil, ele não enxerga nenhum, e `docker compose build` não
constrói nada sem dizer nada. `--profile services` sozinho é projeto inválido,
porque os serviços dependem do `postgres`. É sempre:

```bash
docker compose --profile marco2 ...                   # o marco 2: postgres, mongodb, rabbitmq e os quatro serviços
docker compose --profile core ...                     # só a infraestrutura, inclusive redis e minio
docker compose --profile core --profile services ...  # infraestrutura e os nove serviços
```

O `marco2` existe desde a I-B: é o que o marco 2 usa de verdade. Os cinco
esqueletos (`settlement`, `order`, `payment`, `delivery`, `conversation`) só
ligam uma JVM, e o `redis` e o `minio` não têm usuário no repositório — o cache
de autorização é em processo (ADR-011).

Pré-requisitos, uma vez:

- o motor Docker no WSL2, como o `CLAUDE.md` descreve, e a tarefa agendada
  `WSL Ubuntu keepalive` em `Running`;
- JDK 21 **no Windows**;
- Node 22 para o front;
- `.env` na raiz, copiado do `.env.example` e preenchido. **O `.env` é
  gitignorado e nunca entra em relatório, em commit nem em conversa.**

---

## 1. O caminho curto: um serviço, sem contêiner de serviço

Para ver o Swagger e bater num endpoint, você **não precisa de imagem**. A
infraestrutura em contêiner basta, e o serviço roda pelo Gradle no Windows.

No WSL:

```bash
docker compose --profile core up -d          # postgres, mongodb, redis, rabbitmq, minio
```

No Git Bash, na raiz do repositório:

```bash
set -a; . ./.env; set +a                     # ⚠ exporta segredo no shell; não ecoe nada depois
export DB_USERNAME="$POSTGRES_USER" DB_PASSWORD="$POSTGRES_PASSWORD" RABBITMQ_USERNAME="$RABBITMQ_USER"
cd backend
./gradlew :services:merchant-service:bootRun
```

**A segunda linha não é opcional.** O `.env` usa os nomes do contêiner
(`POSTGRES_USER`, `RABBITMQ_USER`); o `application.yml` dos serviços lê
`DB_USERNAME`, `DB_PASSWORD` e `RABBITMQ_USERNAME`, sem valor padrão. No compose,
a tradução está no `environment:` de cada serviço; no host, é esta linha.

Os **endereços** não precisam de nada: cada `application.yml` tem valor padrão
apontando para `localhost` (`DB_URL`, `MONGO_URI`, `RABBITMQ_ADDRESSES`,
`JWT_JWKS_URI`, `MERCHANT_URI`), e a infraestrutura está publicada em
`127.0.0.1`. O compose sobrescreve com os nomes da rede (`postgres:5432`) só
dentro dele.

| | |
| --- | --- |
| Do `bootRun` ao primeiro `200` no `/actuator/health` | **111 s** (o Spring sozinho: 37 s; o resto é o Gradle) |
| Swagger | `http://127.0.0.1:8082/swagger-ui.html` → `302` → `/swagger-ui/index.html` → `200` |
| Contrato | `http://127.0.0.1:8082/v3/api-docs` |

**A documentação só existe em dois serviços**, `merchant` (8082) e `catalog`
(8083), e só com `DELIVERY_DOCS_ABERTAS=true` no `.env` — ADR-050. Fechada, a
resposta é `401`, não `404`. Nos outros serviços a cadeia de segurança não abre
a rota.

O `identity` pelo `bootRun` exige também `JWT_PRIVATE_KEY_PATH` apontando para um
PEM (ADR-037) — sem ele o contexto não sobe, de propósito.

É este o caminho do dia a dia. O da seção 2 é para quando você quer o sistema
inteiro de pé — e ele ainda não chegou lá.

---

## 2. A pilha inteira

### 2.1 A imagem-base, uma vez só

No WSL:

```bash
docker pull eclipse-temurin:21-jre-alpine
```

Separado, e antes. O Docker Hub já falhou aqui por IPv6 —
`dial tcp [2600:...]:443: connect: network is unreachable`. Falhando num `pull`
isolado, você repete o `pull`; falhando no meio de um `build`, perde o build.

### 2.2 Os jars — no Windows

```powershell
cd C:\dev\delivery-platform\backend
.\gradlew.bat bootJar
```

Um daemon do Gradle, o cache do repositório reaproveitado. É a ADR-051: a imagem
carrega o jar, não o compilador.

| Medido com | Tempo |
| --- | --- |
| depois de `./gradlew clean` | **122 s** |
| depois de mudar um arquivo de um serviço | **45 s** |
| sem mudança nenhuma | **22 s** |

Confira que cada um dos nove módulos executáveis tem **um** jar, e que ele se
chama `app.jar`:

```bash
ls backend/services/*/build/libs/ backend/infra/gateway/build/libs/
```

Se aparecer um `*-plain.jar`, é sobra de antes da ADR-051 — o plugin de
convenção não o gera mais. `.\gradlew.bat clean bootJar` resolve.

### 2.3 As imagens — no WSL

```bash
docker compose --profile marco2 build                      # as quatro do marco 2
docker compose --profile core --profile services build     # as nove
```

| Medido com | Tempo |
| --- | --- |
| as quatro do marco 2, duas delas vindas do cache (10/10) | **47 s** |
| as nove, primeira vez depois da ADR-051 | **234 s** |
| uma (`catalog-service`), de novo | **58 s**, dos quais 12 s mandando o contexto |
| **antes da ADR-051**, uma (`identity-service`), com o Gradle dentro | **959 s**: 366 s mandando o contexto e 538 s de Gradle |

Cada imagem manda ao BuildKit só o jar dela — o contexto do `catalog` tem
53,86 MB, que é o tamanho do `app.jar` dele. O `backend/.dockerignore` exclui
tudo e reinclui `**/build/libs/app.jar`. O pico do `vmmemWSL` durante as nove foi
de **4,2 GB**; antes da ADR-051, o mesmo comando passou de 11,8 GB e travou a VM.

### 2.4 De pé — o marco 2, em quatro grupos

No WSL, um grupo de cada vez. `--wait` volta só quando os contêineres do grupo
estão saudáveis pelo healthcheck — e falha se algum ficar `unhealthy`:

```bash
docker compose --profile marco2 up -d --wait --wait-timeout 300 postgres mongodb mongo-init rabbitmq   # 1
docker compose --profile marco2 up -d --wait --wait-timeout 300 identity-service                      # 2
docker compose --profile marco2 up -d --wait --wait-timeout 300 merchant-service catalog-service      # 3
docker compose --profile marco2 up -d --wait --wait-timeout 300 gateway                               # 4
```

O `identity` sobe sozinho porque todos os outros validam token pelo JWKS dele:
juntos, "não subiu" e "subiu e não achou o JWKS" chegam misturados.
`merchant` e `catalog` são os dois lados do circuito do expediente.

**Medido em 10/10/2026**, a partir da infraestrutura já de pé e com o `redis` e o
`minio` parados. O total da VM foi lido do lado Windows, amostrado a cada 500 ms
durante cada `up`:

```powershell
(Get-Process vmmemWSL).WorkingSet64 / 1MB                                        # a VM
$o = Get-CimInstance Win32_OperatingSystem                                        # o commit
($o.TotalVirtualMemorySize - $o.FreeVirtualMemory) / 1MB                          # GB comprometidos
```

| Grupo | Até saudável | Memória por contêiner | Pico do `vmmemWSL` | Commit do Windows |
| --- | --- | --- | --- | --- |
| antes de tudo | — | — | 2.253 MB | — |
| 1 · infraestrutura (já de pé) | 12 s | rabbitmq 136 MiB, postgres 44 MiB, mongodb 484 MiB | 2.310 MB | — |
| 2 · identity | **34 s** | identity 285 MiB de 512 | 2.629 MB | 44,4 de 50,1 GB |
| 3 · merchant e catalog | **45 s** | merchant 279, catalog 233 MiB de 512 | 3.194 MB | 45,0 GB |
| 4 · gateway | **21 s** | gateway 212 MiB de 512 | **3.403 MB** | 45,0 GB |

**Os sete de pé ocupam 3,4 GB da VM.** Recriados de uma vez só — depois de uma
troca de senha no `.env`, o compose recria o que mudou —, os sete voltaram
saudáveis em **80 s**.

#### Por que agora sobe: o limite de memória

Os quatro serviços têm `mem_limit: 512m` e
`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75`. Sem limite declarado, a JVM mede a
**VM inteira** e escolhe o heap máximo como fração dela. Medido com
`java -XX:+PrintFlagsFinal -version`, na imagem do `identity` e no `catalog` pelo
compose:

| Configuração | `MaxHeapSize` |
| --- | --- |
| sem limite, sem percentagem (como era) | **3.137.339.392** bytes — ¼ da VM de 12 GB |
| `512m` e `MaxRAMPercentage=75` (como é) | **402.653.184** bytes — 384 MiB |
| `512m`, sem percentagem | 134.217.728 bytes — 128 MiB, ¼ do limite: pouco |
| `MaxRAMPercentage=75`, **sem** limite | **9.412.018.176** bytes — ¾ da VM |

A última linha é a razão de os dois irem juntos: a percentagem sem o limite é
pior que nada. Nove JVMs a 3 GB cada eram 27 GB de heap possível numa VM de 12.

`restart: "on-failure:3"` nos quatro: o `unless-stopped` foi o que transformou a
queda de 01/10 num laço (abaixo). Os cinco esqueletos ainda têm `unless-stopped`
e nenhum limite — não sobem no `marco2`.

#### O que aconteceu em 06/10/2026, com os quinze de uma vez

```bash
docker compose --profile core --profile services up -d
```

O `up -d` voltou com sucesso em 88 s, os quinze contêineres subiram ao mesmo
tempo, e a VM do WSL caiu cerca de um minuto depois, com os nove serviços ainda
no meio da partida — nenhum chegou a registrar `Started`. A queda levou junto a
tarefa `WSL Ubuntu keepalive`, e a distribuição passou a reiniciar a cada
comando `wsl`; a cada reinício, o `restart: unless-stopped` subia os quinze de
novo, e a VM voltava a cair. **É um laço**, e ele não para sozinho.

Para sair dele, se os esqueletos ainda o armarem:

```bash
docker compose --profile core --profile services stop \
  gateway identity-service merchant-service catalog-service settlement-service \
  order-service payment-service delivery-service conversation-service
```

```powershell
Start-ScheduledTask -TaskName "WSL Ubuntu keepalive"
```

`stop` é respeitado pelo `unless-stopped`: os nove ficam parados nos reinícios
seguintes, e a infraestrutura continua de pé. **O `profile services` inteiro não
foi medido de novo** depois dos limites — os cinco esqueletos não os têm.

### 2.5 A prova

**Saúde não prova a pilha.** O healthcheck do contêiner fica `healthy` com o JWKS
inalcançável — medido na I-B, com o `merchant` apontado para `localhost`. A prova
que exercita a validação de token entre contêineres é uma rota protegida pelo
gateway, com token emitido pelo `identity`:

1. `POST /api/v1/auth/verification-code` com um telefone E.164 → `202`;
2. o código, lido da tabela (seção 4);
3. `POST /api/v1/auth/signup` → `201`; `POST /api/v1/auth/login` → `200`;
4. `GET /api/v1/me/estabelecimentos` com o `accessToken` → `200`, sem token → `401`.

Tudo por `http://127.0.0.1:8080`. Em 10/10/2026: `202`, `201`, `200`, `200` em
0,7–1,2 s com zero lojas, e `401` sem token. **Não cole token, senha nem corpo de
login em lugar nenhum.**

O Swagger em contêiner, medido na mesma data: `merchant` e `catalog` respondem
`200` em `/swagger-ui.html` (com `-L`) e em `/v3/api-docs`; o `identity` responde
`401`, porque não tem a flag da ADR-050.

| Serviço | Porta | Documentação (ADR-050) |
| --- | --- | --- |
| gateway | 8080 | — |
| identity | 8081 | — |
| merchant | 8082 | `/swagger-ui.html` |
| catalog | 8083 | `/swagger-ui.html` |
| settlement | 8084 | — |
| order | 8085 | — |
| payment | 8086 | — |
| delivery | 8087 | — |
| conversation | 8088 | — |

Todas publicadas em `127.0.0.1`, nunca em `0.0.0.0` — regra do `CLAUDE.md`,
conferida no `docker compose config` resolvido em 10/10/2026.

Infraestrutura: postgres 5432, mongodb 27017, redis 6379, rabbitmq 5672 e 15672,
minio 9000 e 9090 (o console; dentro do contêiner, 9001).

### 2.6 O script

```bash
./scripts/subir-local.sh
```

Confere os jars dos quatro módulos (não os constrói — eles vêm da 2.2, no
Windows) e a chave em `secrets/jwt-private.pem`, constrói as quatro imagens, sobe
os quatro grupos da 2.4 com `--wait`, e mostra `ps`, a memória por contêiner, a
saúde do gateway e o Swagger do `merchant`, parando no primeiro erro.
`--sem-build` pula jars e imagens; `--so-infra` sobe só o grupo 1.

**Rodou do começo ao fim em 10/10/2026**, com a pilha já de pé. A prova da 2.5
não está nele: ela cria um usuário a cada execução.

### 2.7 O circuito, visto de fora

Com `DELIVERY_SEMEADURA=true` e `DELIVERY_SEMEADURA_SENHA` no `.env`, a subida grava a
fixture da ADR-059: no `identity`, o usuário que entra nela (W-D); no `merchant`, uma loja cuja faixa de horário **começa cinco minutos à
frente** e dura três horas; no `catalog`, um produto publicado e `ESGOTADO_HOJE`, com
uma opção também esgotada, ambos com carimbo de trinta dias atrás. As duas linhas
`semeadura:` do log dizem o id do produto e a hora em que a faixa abre.

Depois disso, ninguém publica nada à mão. A faixa abre, a varredura do `merchant`
acha a loja dentro do horário sem marca d'água, grava a marca e o evento **na mesma
transação**, o relay entrega ao broker, e o consumidor do `catalog` reativa o que está
atrás do expediente.

**Medido em 10/10/2026**, com a pilha de pé e o grupo 3 recriado com a bandeira
(saudáveis em 40 s):

| Instante (UTC) | O que aconteceu | De onde se lê |
| --- | --- | --- |
| 05:49:15–17 | as duas semeaduras | `docker compose logs`, linha `semeadura:` |
| 05:54:00 | a faixa abre | a linha `semeadura:` do `merchant` |
| 05:54:27,37 | marca d'água e evento gravados | `abertura_de_expediente` e `outbox.ocorrido_em`, no `merchant_db` |
| 05:54:28,27 | o relay publica | `outbox.publicado_em` |
| 05:54:28 | o produto e a opção voltam a `DISPONIVEL` | o documento em `catalog_db.produtos`, e o log `Resultado[produtosAlterados=1, …]` |

**Da faixa abrir ao produto virar: 28 s.** Quase tudo é a fase da varredura, que roda
de minuto em minuto a partir da subida (`delivery.expediente.intervalo-ms`, 60 s, com
10 s de atraso inicial); o relay roda a cada segundo. **O pior caso é cerca de 61 s.**
A fila de trabalho e a fila morta ficaram em zero mensagens, antes e depois.

O que olhar, no WSL:

```bash
docker compose --profile marco2 exec -T mongodb mongosh --quiet --eval   'JSON.stringify(db.getSiblingDB("catalog_db").produtos.findOne({}, {nome:1, disponibilidade:1, "gruposDeOpcoes.opcoes.nome":1, "gruposDeOpcoes.opcoes.disponibilidade":1}))'
docker compose --profile marco2 exec -T postgres sh -c   'psql -U "$POSTGRES_USER" -d merchant_db -c "select * from abertura_de_expediente"'
docker compose --profile marco2 exec -T rabbitmq rabbitmqctl list_queues -q name messages
```

**O `vendavel` não aparece no documento**: é derivado, não é campo. O que o documento
mostra é o estado do produto e o de cada opção.

**A ordem da semeadura é decisão, não acidente** (ADR-059): com a faixa começando à
frente, o produto existe antes de a abertura ser publicada. **Uma abertura gera um
evento** — medido: com o produto semeado depois da abertura, nenhum segundo evento veio
em duas passadas e meia, e ele ficou esgotado. Para ver o circuito de novo, é preciso
outra abertura: a da semana seguinte, ou a receita abaixo.

#### Entrar com a fixture

Telefone **`+5511999990001`**, senha a do `DELIVERY_SEMEADURA_SENHA`. O vínculo é de
fundador — `ADMINISTRADOR`, todas as permissões —, então o painel abre as seções
"Cardápio" e "Disponibilidade", e o nome do produto abre a tela da opção (W-D).

Sem a senha, com a bandeira ligada, o `identity` **não sobe**, e diz por quê:
`delivery.semeadura.ligada=true exige a senha do usuário da fixture em
DELIVERY_SEMEADURA_SENHA` (medido em 10/10/2026).

Medido em 10/10/2026, pela API, com as chamadas que o painel faz: entrar `200`, minhas
lojas `200` com uma loja, a lista `200`, o produto aberto `200`. E o circuito visto
**pela API**, não pelo documento: a faixa abriu às 11:19:00Z e o produto apareceu
`DISPONIVEL` e vendável na leitura de 11:19:51Z.

#### Ressemear

Três comandos, no WSL. **Nada no repositório sabe apagar dado** — é receita, e não
bandeira (ADR-059, emenda da W-D).

```bash
docker compose --profile marco2 exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d merchant_db -v ON_ERROR_STOP=1 -c "begin; delete from abertura_de_expediente where estabelecimento_id = '"'"'5eed0000-0000-4000-8000-000000000001'"'"'; delete from membro where estabelecimento_id = '"'"'5eed0000-0000-4000-8000-000000000001'"'"'; delete from estabelecimento where id = '"'"'5eed0000-0000-4000-8000-000000000001'"'"'; commit;"'
docker compose --profile marco2 exec -T mongodb mongosh --quiet --eval 'db.getSiblingDB("catalog_db").produtos.deleteMany({estabelecimentoId: UUID("5eed0000-0000-4000-8000-000000000001")})'
docker compose --profile marco2 up -d --force-recreate --no-deps --wait merchant-service catalog-service
```

A ordem do primeiro comando é a das chaves estrangeiras: a marca d'água e o vínculo
apontam para a loja sem `cascade`; o resto da loja cai junto com ela. O usuário do
`identity` fica — ele não depende da loja.

Os três comandos, como estão acima, levaram **91 s** em 10/10/2026, e os dois semeadores gravaram.

**O `--force-recreate` não é enfeite.** Os semeadores rodam na **subida**, e o `up -d`
não recria contêiner que não mudou. Medido em 10/10/2026: sem ele, o `merchant` foi
recriado (a imagem era nova) e o `catalog` não (a mudança tinha sido só em
comentário, e a imagem saiu idêntica) — a loja abriu sem produto nenhum. **E a ordem
importa:** os dois juntos, no mesmo comando, para o produto existir antes de a faixa
abrir, cinco minutos depois.

---

## 3. O front

```bash
cd frontend
npm ci
npm run dev
```

Em `http://localhost:5173`.

**`CORS_ALLOWED_ORIGINS` no `.env` tem de incluir `http://localhost:5173`.** Um
`.env` antigo nasceu com `http://localhost:3000`, que é a porta do Create React
App e não a do Vite; com o valor errado, o painel carrega e nenhuma chamada passa.

---

## 4. Olhar dentro

No WSL. As credenciais já estão no ambiente do contêiner do `postgres`, por isso
o `sh -c` com aspas simples — quem expande a variável é o contêiner, não você:

```bash
# o último código de verificação emitido
docker compose --profile core exec postgres sh -c 'psql -U "$POSTGRES_USER" -d identity_db \
  -c "select telefone, codigo, expira_em, tentativas from codigo_de_verificacao order by criado_em desc limit 1;"'

# as bases
# (-d postgres: sem ele, o psql procura uma base com o nome do usuário, que não existe)
docker compose --profile core exec postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres -c "\l"'

# os produtos
docker compose --profile core exec mongodb mongosh --quiet \
  --eval 'db.getSiblingDB("catalog_db").produtos.countDocuments()'
```

O código de verificação vale dez minutos e aceita cinco tentativas
(`CodigoDeVerificacao.VALIDADE` e `TENTATIVAS_MAXIMAS`).

Rabbit em `http://127.0.0.1:15672`, MinIO em `http://127.0.0.1:9090` — as
credenciais estão no `.env`.

---

## 5. Quando der errado

| Sintoma | Causa | O que fazer |
| --- | --- | --- |
| `docker: The term 'docker' is not recognized` | você está no PowerShell | `wsl`, e trabalhe de dentro |
| `java: command not found` no WSL | o Gradle roda no Windows | seção 0 |
| `docker compose build` termina em segundos sem construir nada | faltaram os perfis | `--profile marco2`, ou `--profile core --profile services` |
| `service "…" depends on undefined service "postgres"` | `--profile services` sem o `core` | os dois perfis juntos |
| `COPY failed: no source files were specified` | faltou o `bootJar` | seção 2.2. É o preço aceito na ADR-051 |
| a VM cai e volta, e os contêineres sobem de novo a cada `wsl` | o laço da seção 2.4 — hoje só os cinco esqueletos o armam; os quatro do marco 2 desistem depois de três tentativas (`on-failure:3`) | `stop` dos serviços e `Start-ScheduledTask` |
| `vmmemWSL` acima de 10 GB, Docker ou `wsl` sem responder — a VM travou | build pesado em paralelo, ou serviços sem `mem_limit` subindo juntos | `wsl --shutdown` no PowerShell, e **religue a tarefa**: `Start-ScheduledTask -TaskName "WSL Ubuntu keepalive"` — em 01/10 ela morreu junto, e foi isso que fechou o laço. O `systemctl enable docker` e o `restart: unless-stopped` da infraestrutura a trazem de volta no primeiro `wsl`; os serviços sobem pela 2.4 |
| `Wsl/Service/0x8007274c` | o WSL não consegue mais iniciar a VM | `wsl --shutdown`, espere oito segundos, `wsl` |
| depois do `--shutdown` ou de uma queda, o Ubuntu volta a parar sozinho | a tarefa `WSL Ubuntu keepalive` morreu junto | `Start-ScheduledTask -TaskName "WSL Ubuntu keepalive"`, e confirme `Running` com `Get-ScheduledTask` |
| `127.0.0.1:2375` recusa a conexão logo depois de religar | a ponte do daemon para o lado Windows ainda está subindo | espere e repita. É o caminho que o Testcontainers usa |
| `dial tcp [2600:...]:443: network is unreachable` | Docker Hub por IPv6 | repita o `docker pull` da seção 2.1 |
| o `bootRun` não acha usuário do banco | o `.env` usa `POSTGRES_USER`, o serviço lê `DB_USERNAME` | a linha `export` da seção 1 |
| o painel carrega e nada responde | CORS em 3000 | seção 3 |
| o `catalog` não sobe, com `PRECONDITION_FAILED - inequivalent arg` no log | a fila `catalog.expediente-alterado` já existe no broker com outros argumentos — e **argumento de fila é imutável** | apague a fila e suba de novo: `docker compose --profile marco2 exec rabbitmq rabbitmqctl delete_queue catalog.expediente-alterado`. Mensagem que estava nela se perde — confira antes se há alguma (`rabbitmqctl list_queues name messages`). Não mude o nome da fila para contornar |
| rota protegida dá `500` em contêiner, e o log diz `I/O error on GET request for "http://localhost:8081/.well-known/jwks.json"` | o serviço procura o JWKS em `localhost`, que dentro do contêiner é ele mesmo | `JWT_JWKS_URI` com `http://identity-service:8081/...` no `environment:` do serviço. Não é `401`: falha de busca do JWKS sai como `500` (medido na I-B) |
| o `identity` não sobe, e `secrets/jwt-private.pem` virou diretório | o arquivo não existia quando o compose montou o volume, e o Docker criou um diretório com o nome, de `root` (medido em 10/10/2026) | apague o diretório, gere o par como diz o `.env.example`, e suba de novo |

---

## 6. O que este documento não cobre

**Não há dado de partida.** Depois de subir, o banco está vazio: para ver um
produto na tela é preciso usuário, estabelecimento, categoria e produto — e hoje
nem o `merchant` tem rota de escrita (só leituras e o expediente corrente) nem o
`catalog` tem rota de criar produto (só as duas de marcar disponibilidade).

Um `seed` que escreva direto nas tabelas foi considerado e recusado: ele burlaria
as invariantes que só existem no agregado, e passaria a ser um segundo lugar onde
as regras do domínio estão escritas. **Gatilho escrito:** a rodada que der ao
`merchant` as rotas de escrita. Aí o `seed` é uma sequência de chamadas HTTP, e
ela passa pelas mesmas regras que um usuário passa.

**O que mudou em 10/10/2026 (I-C), e o que não mudou.** A recusa acima é contra escrever
**direto nas tabelas**, e ela continua de pé. A I-C semeia por outro caminho: um
`ApplicationRunner` dentro de cada serviço, que constrói o agregado com os construtores
de verdade e grava pelo repositório de verdade (ADR-059, seção 2.7). As duas razões da
recusa ficam atendidas — as invariantes são o caminho, e não há segundo lugar onde as
regras estejam escritas.

**A rota de escrita continua devida.** Uma fixture não é uma API: ninguém monta um
cardápio por `ApplicationRunner`, e a bandeira que a liga é falsa em todo lugar que não
seja a máquina de quem desenvolve. **O gatilho desta seção segue armado** — a rodada que
der ao `merchant` as rotas de escrita troca a fixture por chamadas HTTP, e **apaga os
semeadores do repositório**. Eles são andaime.
