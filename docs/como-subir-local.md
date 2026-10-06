# Como subir o ambiente local

Este documento existe porque, em 01/10/2026, a receita de subir a pilha vivia
numa conversa e, por isso, não existia. Oito das nove imagens de serviço nunca
tinham sido construídas, e o comando que a gente achava que subia o ambiente
derrubou a máquina.

**Todo tempo abaixo foi medido nesta máquina em 06/10/2026**, com o comando ao
lado. Se você medir outro, troque — número lembrado não serve. E **a pilha
inteira ainda não subiu nenhuma vez**: a seção 2.4 diz o que aconteceu quando se
tentou, e é o estado de verdade deste caminho.

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
docker compose --profile core ...                     # só a infraestrutura
docker compose --profile core --profile services ...  # infraestrutura e serviços
```

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
docker compose --profile core --profile services build
```

| Medido com | Tempo |
| --- | --- |
| as nove, primeira vez depois da ADR-051 | **234 s** |
| uma (`catalog-service`), de novo | **58 s**, dos quais 12 s mandando o contexto |
| **antes da ADR-051**, uma (`identity-service`), com o Gradle dentro | **959 s**: 366 s mandando o contexto e 538 s de Gradle |

Cada imagem manda ao BuildKit só o jar dela — o contexto do `catalog` tem
53,86 MB, que é o tamanho do `app.jar` dele. O `backend/.dockerignore` exclui
tudo e reinclui `**/build/libs/app.jar`. O pico do `vmmemWSL` durante as nove foi
de **4,2 GB**; antes da ADR-051, o mesmo comando passou de 11,8 GB e travou a VM.

### 2.4 De pé — **não funcionou em 06/10/2026**

O comando seria:

```bash
docker compose --profile core --profile services up -d
docker compose --profile core --profile services ps
```

**O que aconteceu na única vez que ele rodou depois da ADR-051:** o `up -d`
voltou com sucesso em 88 s, os quinze contêineres subiram ao mesmo tempo, e a
VM do WSL caiu cerca de um minuto depois, com os nove serviços ainda no meio da
partida — nenhum chegou a registrar `Started`. A queda levou junto a tarefa
`WSL Ubuntu keepalive`, e a distribuição passou a reiniciar a cada comando
`wsl`; a cada reinício, o `restart: unless-stopped` subia os quinze de novo, e a
VM voltava a cair. **É um laço**, e ele não para sozinho.

Para sair dele:

```bash
docker compose --profile core --profile services stop \
  gateway identity-service merchant-service catalog-service settlement-service \
  order-service payment-service delivery-service conversation-service
```

```powershell
Start-ScheduledTask -TaskName "WSL Ubuntu keepalive"
```

`stop` é respeitado pelo `unless-stopped`: os nove ficam parados nos reinícios
seguintes, e a infraestrutura continua de pé.

**A causa não está medida.** O pico do `vmmemWSL` não foi registrado durante a
subida, então não se sabe se foi a memória da VM, o limite de commit do Windows
(o `CLAUDE.md` já viu esse em 30/08) ou outra coisa. **Gatilho escrito:** a
próxima tentativa de subir a pilha mede o `vmmemWSL` e o commit do Windows
durante o `up`, e sobe os serviços **em grupos**, não os nove de uma vez — e
este parágrafo é reescrito com o que ela medir.

### 2.5 A prova, quando a 2.4 funcionar

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8082/swagger-ui.html
```

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
conferida no `docker-compose.yml` em 06/10/2026.

Infraestrutura: postgres 5432, mongodb 27017, redis 6379, rabbitmq 5672 e 15672,
minio 9000 e 9090 (o console; dentro do contêiner, 9001).

### 2.6 O script

```bash
./scripts/subir-local.sh
```

Confere os jars (não os constrói — eles vêm da 2.2, no Windows), constrói as
imagens, sobe, espera e prova, parando no primeiro erro. `--sem-build` pula a
conferência e as imagens; `--so-infra` sobe só o `core`.

**Ele ainda não rodou do começo ao fim**, porque a 2.4 não passou. As etapas
dele foram rodadas uma a uma, com os mesmos comandos, e são as das tabelas
acima.

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
| `docker compose build` termina em segundos sem construir nada | faltaram os perfis | `--profile core --profile services` |
| `service "…" depends on undefined service "postgres"` | `--profile services` sem o `core` | os dois perfis juntos |
| `COPY failed: no source files were specified` | faltou o `bootJar` | seção 2.2. É o preço aceito na ADR-051 |
| a VM cai e volta, e os contêineres sobem de novo a cada `wsl` | o laço da seção 2.4 | `stop` dos nove serviços e `Start-ScheduledTask` |
| `vmmemWSL` acima de 10 GB, Docker sem responder | build pesado em paralelo | `wsl --shutdown` no PowerShell — o `systemctl enable docker` e o `restart: unless-stopped` trazem a infraestrutura de volta no primeiro `wsl` |
| `Wsl/Service/0x8007274c` | o WSL não consegue mais iniciar a VM | `wsl --shutdown`, espere oito segundos, `wsl` |
| depois do `--shutdown` ou de uma queda, o Ubuntu volta a parar sozinho | a tarefa `WSL Ubuntu keepalive` morreu junto | `Start-ScheduledTask -TaskName "WSL Ubuntu keepalive"`, e confirme `Running` com `Get-ScheduledTask` |
| `127.0.0.1:2375` recusa a conexão logo depois de religar | a ponte do daemon para o lado Windows ainda está subindo | espere e repita. É o caminho que o Testcontainers usa |
| `dial tcp [2600:...]:443: network is unreachable` | Docker Hub por IPv6 | repita o `docker pull` da seção 2.1 |
| o `bootRun` não acha usuário do banco | o `.env` usa `POSTGRES_USER`, o serviço lê `DB_USERNAME` | a linha `export` da seção 1 |
| o painel carrega e nada responde | CORS em 3000 | seção 3 |

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
