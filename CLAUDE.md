# CLAUDE.md

Plataforma de delivery para comércio de bairro. Monorepo com 8 microsserviços de
negócio e um gateway, cada um com processo, banco, migrations, testes, contrato e
pipeline próprios.

**Contexto de produto:** o cliente é o **comerciante**, não o consumidor. A
plataforma frequentemente **não custodia o dinheiro** (pagamento na entrega), o
entregador **pertence ao estabelecimento** e o pedido nasce numa **conversa de
WhatsApp**. Se uma decisão parecer estranha, confira as premissas em
`docs/PRD.md` §5 antes de "corrigir".

---

## Comandos

```bash
# infraestrutura (o dia a dia usa só isto)
docker compose --profile core up -d

# build e testes
cd backend
./gradlew build                              # tudo
./gradlew :services:order-service:test       # um serviço
./gradlew :services:order-service:bootRun    # rodar um serviço
./gradlew printModules                       # listar módulos
```

Perfis do Compose: `core` (bancos e brokers) · `services` (aplicações) · `full`.
**Não suba `full` para trabalhar** — são muitos
containers e você quase nunca precisa deles.
**Todo serviço tem `profiles:`**, então sem perfil o compose não enxerga
nenhum — `docker compose build` sozinho não constrói nada. E `services` sozinho é
projeto inválido (os serviços dependem do `postgres`): é sempre
`--profile core --profile services`. Para subir tudo, `docs/como-subir-local.md`.

---

## Regras que quebram o build se violadas

Cada serviço tem `HexagonalArchitectureTest` (ArchUnit). Ele falha se:

- `domain` importar Spring, JPA, Hibernate ou Jackson
- uma classe de `domain` tiver anotação de persistência
- a direção das dependências for invertida

```
api ─────────────────┐
                     v
infrastructure ──> application ──> domain
```

- `domain/` — regra pura, sem framework. **Porta de repositório não fica aqui**: mora em `application/port/out`, como o `README.md` descreve e como o `identity-service` e o `merchant-service` fazem. Os diretórios `domain/repository/` vazios foram removidos (13/09/2026).
- `application/` — casos de uso e orquestração; depende de portas (`port/out`).
- `infrastructure/` — persistência, mensageria, clientes HTTP, segurança.
- `api/` — traduz HTTP em comando.

Entidade JPA vive em `infrastructure/persistence/entity`, **nunca** em `domain`.
A duplicação entre modelo de domínio e entidade é intencional.

---

## Invariantes de negócio

Estas não são preferências de estilo. Quebrar qualquer uma é defeito.

1. **Nenhuma entrega ou retirada conclui sem liquidação registrada** — método
   efetivo, valor efetivo e responsável pela custódia. `NAO_LIQUIDADO` é registro
   válido; ausência de registro não é.
   A garantia é estrutural porque T16 exige jornada aberta, e não porque alguém
   se lembra de conferir — ver ADR-033, que é o que torna essa guarda exequível.

2. **Valor cobrado vem do pedido.** Preço, total e taxa são sempre recalculados
   no servidor a partir do catálogo. Valor que chega no request é ignorado.

3. **`metodo_declarado` ≠ `metodo_liquidado`.** São campos distintos. O cliente
   pede dinheiro e paga no cartão porque não teve troco — isso é o caso normal.

4. **Item do pedido é congelado.** Nome, preço-base, opções escolhidas com nome e
   acréscimo. Alteração posterior no catálogo não muda pedido existente.

5. **Total é imutável.** Substituição e correção geram `Ajuste` (lista
   somente-inserção, com autor, motivo e data). `totalEfetivo = total + Σ
   ajustes.delta` é derivado, nunca gravado por cima. Nenhum relatório soma
   `total` para dizer quanto entrou.

6. **Comprovante em imagem não confirma Pix.** Só o webhook do PSP, com `txid`
   correlacionado ao pedido.

7. **Quem publica precisa de outbox. Quem consome precisa de
   `processed_messages`.** Sem exceção.
   **Uma exceção, escrita na ADR-048 §2:** consumidor cujo único efeito é em
   memória do processo e é naturalmente idempotente — hoje só o
   `OuvinteDeVinculoAlterado` do `catalog`. O motivo é que a invariante, aplicada
   a ele, produziria a falha que a ADR-011 manda evitar: a `processed_messages` é
   compartilhada entre instâncias, e a segunda instância pularia a própria
   invalidação. Quem escreve em banco não tem exceção.

8. **Nenhum serviço lê o banco de outro.** Integração é por API ou evento.
   ADR-002 — e é de lá que vêm Saga, outbox, idempotência e a duplicação
   deliberada entre serviços.

9. **Nenhum identificador vindo da URL é confiável** sem confronto com o usuário
   autenticado e as permissões no estabelecimento.

10. **Divergência de caixa nunca é compensada em silêncio.** Falta e sobra são
    registradas e aparecem no fechamento. Abatê-las automaticamente do
    pagamento do entregador apaga a métrica que o produto existe para produzir.

11. **Dinheiro que entrou a mais é registrado, não descontado em silêncio.**
    `Σ liquidações confirmadas > totalEfetivo` gera `Devolucao` — objeto próprio,
    somente-inserção, valor sempre positivo. **Nunca** liquidação negativa,
    **nunca** um `Ajuste` no lugar. Na maior parte dos casos o sistema não
    devolve nada: ele registra que é devido, porque não custodiou o valor (P1).
    ADR-030

---

## Convenções de código

- **Injeção por construtor**, campos `final`. Nada de `@Autowired` em campo.
- **`record` para DTOs.** Não usamos Lombok.
- **Enum, nunca `String` livre**, para status, método, modalidade e papel.
  Persistir com `@Enumerated(EnumType.STRING)`.
- **`Money`** — escala 2, `RoundingMode.HALF_UP` **e código de moeda** — para
  todo valor. Mora em `:value-types` (ADR-040); não escreva outro. A moeda não
  prepara multimoeda: faz somar reais com outra coisa estourar em vez de somar.
  `double` e `float` são proibidos na cadeia de dinheiro.
- **Entidade JPA nunca é `@RequestBody`.** DTO de request próprio, com Bean
  Validation.
- **Máquina de estados como tabela de transições válidas**, testada; transição
  fora da tabela lança exceção de domínio → HTTP 409.
- **Paginação obrigatória** em toda listagem. Nada de `findAll()` sem `Pageable`.
- **Erro em `ProblemDetail`** (RFC 7807). Nunca stack trace na resposta.
- **Timeout obrigatório** em toda chamada entre serviços. O padrão da biblioteca
  é esperar para sempre.
- **`Instant` para quando algo aconteceu; `timestamptz` no banco, sempre UTC.**
  Tempo civil — dia, hora do calendário da loja — só existe convertendo com o
  `fusoHorario` do estabelecimento. **Nunca** `LocalDateTime` sem zona num campo
  persistido, **nunca** `LocalDate.now()` ou `LocalDateTime.now()` (leem o fuso
  do servidor, que é do datacenter e não da pizzaria), **nunca** aritmética
  sobre hora local. ADR-025
- **Consumidor tolera valor desconhecido.** Enum que chega com valor que este
  serviço não conhece **não estoura, não bloqueia e não vira exceção** — vira
  registro de que chegou algo não entendido. É o que torna a evolução de contrato
  possível sem parar consumidor. ADR-027
- **Domínio em português, o resto em inglês.** `Usuario`, `Pedido`,
  `Liquidacao`; `JpaRepository`, `SecurityFilterChain`. Padrão de engenharia é
  inglês mesmo dentro do domínio — `Money`, `Port`, `Repository`, `Snapshot`.
  Critério: **a Marli usaria essa palavra?** ADR-035

---

## Configuração de ambiente que funciona (Windows)

Verificado em 23/08/2026, build verde de ponta a ponta com o VS Code aberto.
Se o build quebrar, volte para exatamente isto antes de investigar.

| Item | Valor |
|---|---|
| Repositório | `C:\dev\delivery-platform` — **fora** do perfil do usuário |
| `GRADLE_USER_HOME` | `C:\gradle` — permanente, nível User, fora do perfil |
| Gradle | **8.14.3**, fixado no wrapper |

Definir a variável só com `$env:` não basta: vale numa janela só, e o VS Code
herda o ambiente na inicialização. Use
`[Environment]::SetEnvironmentVariable("GRADLE_USER_HOME","C:\gradle","User")`
e reabra o VS Code.

### Docker — motor no WSL2, sem Docker Desktop

Verificado em 25/08/2026. Docker Desktop **não** foi usado: em máquina Windows
gerenciada ele esbarra em licença e em direito de instalação. O motor mora no
WSL2 e a CLI do Windows fala com ele por TCP na loopback.

| Item | Valor |
|---|---|
| CLI no Windows | **Não é necessária.** O Testcontainers lê `DOCKER_HOST` e fala a API direto; `docker compose` roda pelo WSL (a instalação por winget vem sem plugins); e o diagnóstico usa `Invoke-WebRequest`. Se instalar por conveniência, saiba que ela **some sozinha**: em 14/09/2026 o `winget list` dizia Docker CLI 29.7.2 instalada, e não havia `docker.exe` em lugar nenhum — a árvore do winget em `%LOCALAPPDATA%` tinha sido limpa, provavelmente por política da máquina gerenciada, deixando a matrícula e a entrada de PATH para trás |
| Motor | Docker Engine 29.7.2 dentro do WSL2 Ubuntu, via `get.docker.com` |
| systemd no WSL | `/etc/wsl.conf` com `[boot]` e `systemd=true` — **dentro** da distro |
| Socket TCP | drop-in em `/etc/systemd/system/docker.service.d/tcp.conf` |
| Rede | `%USERPROFILE%\.wslconfig` com `[wsl2]` e `networkingMode=mirrored` |
| `DOCKER_HOST` | `tcp://127.0.0.1:2375`, nível **User** |

O drop-in do systemd:

```
[Service]
ExecStart=
ExecStart=/usr/bin/dockerd -H unix:///var/run/docker.sock -H tcp://127.0.0.1:2375
```

**`127.0.0.1` e nunca `0.0.0.0`.** O próprio instalador avisa: acesso à API
remota de um daemon privilegiado equivale a root na máquina. O modo espelhado do
WSL faz a loopback do Windows e a do Ubuntu serem a mesma, então a ponte existe
sem que a porta seja exposta à rede.

A primeira linha vazia do `ExecStart=` não é engano — é o jeito de limpar o
comando herdado da unidade original antes de substituí-lo.

**O Testcontainers lê `DOCKER_HOST` sozinho.** Nenhuma configuração no Gradle,
nenhum código condicional. É o que torna a ADR-014 viável nesta máquina.

### `docker compose` não existe na CLI do Windows

A instalação por `winget` traz o binário e nada mais. Rode pelo WSL:

```powershell
wsl -d Ubuntu -- bash -lc "cd /mnt/c/dev/delivery-platform && docker compose --profile full config -q"
```

Ou instale o plugin com `winget install Docker.DockerCompose`. **O download
direto do GitHub por `Invoke-WebRequest` é cortado pelo proxy corporativo** —
"conexão anulada pelo software no computador host" é essa assinatura, não falha
de rede.

### Antes de qualquer `docker compose up`

```powershell
Copy-Item .env.example .env
```

Sem `.env`, as variáveis do `.env.example` interpolam para string vazia e o
compose só avisa.
O Postgres recusa subir sem senha — falha alta, e é o comportamento desejado. O
MinIO sobe com credencial em branco, que é pior porque parece funcionar.

### A VM precisa estar viva quando o build roda

O WSL2 encerra a distro quando nenhuma sessão está anexada, e leva os
contêineres junto. O Testcontainers fala com o daemon por `DOCKER_HOST` — se a
VM estiver desligada na hora do `./gradlew test`, o teste falha por motivo que
não tem nada a ver com o código.

> **Revisto em 30/08/2026.** Há guardião: a tarefa `WSL Ubuntu keepalive`, no
> logon. A decisão anterior — não ter guardião — valia enquanto o único
> sintoma era contêiner parado, e as duas peças abaixo resolvem isso. O que
> mudou é o Testcontainers: ele fala com `DOCKER_HOST=tcp://127.0.0.1:2375` e
> **não sabe acordar a VM** — se ela estiver desligada, o teste falha por
> motivo que não tem nada a ver com o código.
>
> **E ela é a peça descartável do trio.** Manter a VM viva custa o teto do
> `.wslconfig` em commit o tempo todo, e commit é o recurso que faltou na
> madrugada de 30/08. Se a pressão voltar, remova esta antes das outras duas.

Três tentativas, três falhas diferentes:

| Tentativa | Falhou como |
|---|---|
| Tarefa agendada no logon | `Register-ScheduledTask` → "Acesso negado" (conta de domínio) |
| `.wslconfig` com `vmIdleTimeout` | chave inexistente na WSL 2.6.3 — ignorada com aviso |
| `sleep infinity` na Inicialização | o processo sobreviveu seis horas e a VM caiu assim mesmo |
| Tarefa agendada no logon, **com os padrões** | ✅ funciona — 30/08/2026. `Ready` após `Register-ScheduledTask`, `Running` após `Start-ScheduledTask`, `wsl -l --running` confirmando o Ubuntu. Roda como o próprio usuário: `LogonType Interactive`, `RunLevel Limited`. A tentativa da primeira linha falhou com "Acesso negado"; o comando dela não foi preservado, mas a diferença provável é ter pedido elevação (`-RunLevel Highest`) ou conta `SYSTEM` — nenhum dos dois é possível para conta de domínio sem admin, e nenhum dos dois é necessário para manter uma VM viva |

Não é perda: `systemctl enable docker` mais `restart: unless-stopped` trazem os
cinco contêineres de pé em **~13 segundos** a partir da VM desligada, medido em
26/08/2026. O primeiro comando `wsl` ou `docker` do dia acorda tudo.

| Peça | O que faz |
|---|---|
| `restart: unless-stopped` no compose | traz os contêineres de volta quando o daemon sobe |
| `systemctl enable docker` | traz o daemon de volta quando a distro sobe |

As duas juntas fecham o ciclo. Política de reinício **não** liga VM desligada —
mas o primeiro comando `wsl`/`docker` do dia já liga, e o resto segue sozinho.

Diagnóstico rápido quando algo não responde:

```
wsl -l --running                → a distro está de pé?
wsl -d Ubuntu -- uptime -s      → desde quando? (se for recente, ela caiu)
wsl -d Ubuntu -- systemctl is-active docker
(Invoke-WebRequest "http://127.0.0.1:2375/version" -TimeoutSec 5).Content
```

> **As três primeiras linhas rodam de dentro da distro, e podem dar falso
> positivo.** Em 13/09/2026 a distro estava de pé, `systemctl is-active docker`
> respondia e um `curl 127.0.0.1:2375` **de dentro do Ubuntu** devolvia 200 — e o
> Testcontainers falhava com `Could not find a valid Docker environment`, três
> vezes seguidas. Quem fala com o daemon é a ponte do **Windows**, e de lá
> `Test-NetConnection -Port 2375` recusava a conexão: o `networkingMode=mirrored`
> tinha caído sozinho, **com a VM viva**. Conserto: `wsl --shutdown`, depois
> `wsl -d Ubuntu -- true`. A quarta linha do bloco — `docker info` — é a única
> que roda do lado do Windows, e é a única que mede o que o teste mede.

`Exited (255)` no Postgres depois de a VM cair é normal: os outros atendem o
SIGTERM e saem com 0; ele demora mais que o tempo limite e leva SIGKILL.

Se o `./gradlew test` falhar por daemon indisponível, rode
`wsl -d Ubuntu -- true`, espere quinze segundos e repita.

---

## Armadilhas deste repositório

| Situação | O que fazer |
|---|---|
| Precisa criar ou alterar tabela | Migration Flyway. `ddl-auto` é `validate` e continua assim |
| Precisa de índice ou validação no MongoDB | Mongock `changeUnit`, nunca comando manual no shell |
| MongoDB não aceita transação | Ele sobe como **replica set de nó único**. Outbox depende disso |
| `too many clients` no Postgres | `maximum-pool-size: 5` por serviço já está configurado; não aumente sem motivo |
| Tentado a adicionar H2 para acelerar teste | Não. Testcontainers contra a mesma imagem de produção. Ver ADR-014 |
| Tentado a usar `subprojects {}` no build raiz | Não. Convention plugins em `build-logic/`. `subprojects` quebra o configuration cache |
| Teste de integração lento localmente | `testcontainers.reuse.enable=true` em `~/.testcontainers.properties` |
| Precisa de permissão comercial em outro serviço | Consultar `merchant-service` via porta, com cache **em processo** (60 s, Caffeine) e política de **negar** quando indisponível — ADR-011. Nunca no Redis: a cache existe para evitar ida à rede |
| Vai escalar um serviço para 2+ instâncias | A cache de autorização é em processo. O consumidor de `VinculoAlteradoV1` precisa de **fila exclusiva por instância** ligada à exchange `topic` `delivery.eventos` (ADR-048 §1; a ADR-011 dizia fanout, e a propriedade que importa é a fila exclusiva). Com fila compartilhada, só uma instância invalida e as outras seguem com permissão revogada até o TTL — silenciosamente. ADR-011 |
| `allowEmptyShould(true)` no ArchUnit | Muleta temporária: com só `.gitkeep` nas camadas, zero classes = falha. **Remova por serviço assim que ele tiver classes** — mantido depois, uma camada apagada ou pacote renomeado passa em silêncio |
| Declarar versão de Testcontainers | Não declare. O BOM do Boot 4.1.x traz testcontainers-bom 2.x, onde os artefatos mudaram de nome: `org.testcontainers:testcontainers-junit-jupiter`, `-postgresql`, `-mongodb`, `-rabbitmq` |
| Vai depender de uma tecnologia no Spring Boot 4 | A autoconfiguração foi **quebrada em módulos por tecnologia**. Depender só do motor não traz a integração: `flyway-core` sem `spring-boot-starter-flyway` significa nenhum `FlywayAutoConfiguration`, nenhuma leitura de `spring.flyway.*` e nenhuma ordenação antes do `entityManagerFactory` — os cinco serviços relacionais ficaram sem migration até 07/09/2026, e só apareceu quando a primeira foi escrita. O mesmo corte tirou `@DataJpaTest` do `spring-boot-test-autoconfigure`. **Declare o `spring-boot-starter-*`, não a biblioteca**, e confirme o artefato no BOM antes de escrever o nome |
| Vai escrever teste de JPA com `@DataJpaTest` | **Não existe no Spring Boot 4.1.1.** O `spring-boot-test-autoconfigure` só traz `jdbc` e `json`; `orm/jpa`, `TestEntityManager` e `@AutoConfigureTestDatabase` saíram. Use `@SpringBootTest(webEnvironment = NONE)` com `@ServiceConnection`, e injete `EntityManager` por `@PersistenceContext`. Verificado em 06/09/2026 listando o jar |
| Vai usar classe ou método de biblioteca — Spring, Nimbus, Testcontainers | **Confirme a assinatura no jar resolvido, não na memória.** `./gradlew :servico:dependencies --configuration testRuntimeClasspath` acha o jar, `unzip -l` diz se a classe existe, `javap -p` diz a assinatura real. Seis vezes este projeto apanhou de API que a memória lembra e a versão não tem: `@DataJpaTest`, `TestEntityManager` e `@AutoConfigureTestDatabase` sumiram no Boot 4; `flyway-core` sem o starter não traz autoconfiguração; `TestRestTemplate` não existe em artefato nenhum do Boot 4.1.1 e virou `RestTestClient`; `RSAKey.toRSAPublicKey()` passou a declarar exceção verificada; e o `ObjectMapper` que o Boot 4 injeta é o do **Jackson 3** (`tools.jackson.databind`) — o `com.fasterxml` continua no classpath por transitividade, então o import compila e o contexto não sobe por falta do bean. Achar leva dois minutos, e o palpite custa uma rodada |
| Vai somar `total` num relatório | Não. `total` é o valor congelado no fechamento. O que entrou é `Σ Liquidacao.valorEfetivo`; o que o pedido vale hoje é `totalEfetivo` |
| Vai calcular quanto o entregador ganha por pedido | Não existe. Remuneração vem do vínculo e é apurada por jornada — ADR-022 |
| Vai colocar permissão ou papel dentro do JWT | Não. O token carrega **seis** claims e nada mais: `iss`, `sub`, `aud`, `iat`, `exp`, `jti`. Sem `roles`, sem `scope`, sem lista de estabelecimentos. Permissão é do vínculo usuário × estabelecimento e é resolvida por requisição, com cache curto e falha fechada. ADR-011, ADR-015 emendada |
| Serviço de autorização indisponível | **Negar.** Fail-closed é decisão assumida: liberar em caso de dúvida transforma uma queda em acesso irrestrito |
| Faixa de horário que cruza a meia-noite | `fim < inicio` pertence ao dia de início e se estende ao seguinte. Teste com pedido à 01:00 é obrigatório |
| Build local falha em `build-logic` — ponto diferente a cada tentativa | **Causa encontrada em 30/08/2026: exaustão do limite de commit do Windows.** Três `hs_err_pid*.log` na raiz mostraram `Failed to commit metaspace` em dois daemons do Gradle — um 8.14.3 e um **8.9**, versão órfã ainda viva. Não é antivírus: a varredura por DLL de produto de segurança nos dumps veio vazia. A máquina tem 31,4 GB de RAM e limite de commit de 41,4 GB, com 32,5 GB já comprometidos em repouso. Cada JVM **reserva** commit ao subir: dois daemons do Gradle a `-Xmx2g -XX:MaxMetaspaceSize=768m`, mais o daemon do Kotlin (que **não** obedece ao `org.gradle.jvmargs`), mais seis JVMs da extensão `redhat.java`, mais o WSL2 sem `memory=` no `.wslconfig` reservando até metade da RAM. **Conserto:** `memory=12GB` no `.wslconfig`, uma distribuição do Gradle só em `C:\gradle\wrapper\dists`, e não rodar build com o VS Code carregado de JVMs. Nove workflows compilaram limpos em runner Linux no mesmo dia — o `build-logic` sempre esteve bom |
| Vai desabilitar `redhat.java` | O pacote Salesforce Apex o declara como dependência dura e o mantém ligado. Desabilite o Salesforce no workspace junto |
| Tentado a atualizar o Gradle | **A 9.7.1 falha** em `compilePluginsBlocks` neste build-logic. O wrapper fixa 8.14.3, que é a única versão com build verde. Bump é tarefa própria, verificada com `--rerun-tasks --no-build-cache`. **Reavalie depois do `memory=` no `.wslconfig`** — a falha que motivou o pin pode ter sido o mesmo estouro de limite de commit, não incompatibilidade real com a 9.7.1 |
| `GRADLE_USER_HOME` apontando para dentro de `C:\Users\` | Não. Ver a configuração de ambiente acima |
| Uma ferramenta "não existe" na sessão do agente | Confira você mesmo com `Get-Command` ou `where.exe`. Instalação por usuário fica em `%LOCALAPPDATA%`, e a sessão do agente não herda o PATH do seu perfil. Aconteceu duas vezes com a ferramenta **instalada** — Gradle e Docker — e uma terceira em que ela **não estava**: o `winget list` afirmava Docker CLI 29.7.2, o `where.exe` não achava nada, e o diretório de atalhos que estava no PATH **nem existia**. **Gerenciador de pacotes não é evidência de que o arquivo existe; `where.exe` é.** É a mesma regra que este repositório já tem para biblioteca, aplicada a executável |
| Acabou de rodar `SetEnvironmentVariable(...,"User")` | A janela que executou o comando **não enxerga a própria escrita**. Grava no registro para processos futuros. Para testar na hora, `$env:NOME = "valor"` também |
| Abriu aba nova do terminal e a variável não veio | Aba não é processo. A aba nova nasce filha do Windows Terminal que já estava aberto e herda o ambiente **daquele** processo. Feche o Terminal inteiro — e o VS Code — e abra de novo |
| Vai colar um bloco de comandos no shell do WSL | Rode `sudo -v` antes. Se um `sudo` do meio do bloco pedir senha, as linhas seguintes viram tentativas de senha e o terminal as ecoa — parece que repetiu, e na verdade nada rodou |
| Vai tentar manter a VM do WSL2 sempre viva | **Crie só a tarefa agendada no logon**, com os padrões — sem `-RunLevel Highest`, sem conta `SYSTEM`. Registrar não é executar: confirme com `Start-ScheduledTask` e depois `Get-ScheduledTask \| Select State`. `vmIdleTimeout` no `.wslconfig` (chave inexistente na WSL 2.6.3) e `sleep infinity` (sobrevive até a VM cair de qualquer jeito) continuam desaconselhados. `restart: unless-stopped` + `systemctl enable docker` fecham o ciclo depois que a VM acorda — ~13 s do zero |
| Vai registrar tarefa agendada | `Register-ScheduledTask` deixa em `Ready`, não em `Running` — registrar não é executar. Falta o `Start-ScheduledTask`, e depois `Get-ScheduledTask \| Select State` para confirmar |
| Vai mexer num Dockerfile de serviço | A imagem recebe o jar pronto, nunca o compilador (ADR-051). `./gradlew bootJar`, de dentro de `backend/`, vem **antes** do `docker compose build`; o jar se chama `app.jar` desde o primeiro commit e o jar simples está desligado no `delivery.spring-service-conventions`. O contexto é `backend/`, e o `backend/.dockerignore` exclui tudo menos `**/build/libs/app.jar` — um `COPY` de qualquer outra coisa falha com `no source files were specified`, e a mensagem não culpa o ignore. Se você se vir escrevendo `RUN ./gradlew` dentro de um Dockerfile, pare: nove disso em paralelo esgotaram os 12 GB da VM do WSL em 01/10 |
| Vai subir o ambiente | `docs/como-subir-local.md`, ou `./scripts/subir-local.sh`. De dentro do WSL — o `docker` não existe no lado Windows (ver a configuração de ambiente acima). E o laço com variável passado por `wsl -- bash -lc` é a armadilha logo abaixo |
| Vai rodar comando com `$`, `$(...)` ou aspas aninhadas via `wsl -d Ubuntu -- ...` | Não passe pelo PowerShell. Ele mastiga a passagem de argumento e a variável chega vazia — sem erro, o comando roda e mede outra coisa. Entre com `wsl`, rode lá dentro, `exit`. Diagnóstico: `echo "[$VAR]"` — se vier `[]` e você sabe que a variável existe, é isto |
| Vai guardar coordenada, áudio ou número de cartão | Não guarde. Endereço textual e bairro no lugar da coordenada, transcrição no lugar do áudio, `txid` no lugar do cartão. A forma mais barata de cumprir a LGPD é não ter o dado — ADR-013 §4 |
| Restaurou um banco a partir de backup | **Reaplique as exclusões de titular** com data posterior à do backup antes de o serviço voltar a atender. Backup restaurado ressuscita dado apagado — `docs/operacao/exclusao-de-titular.md` |
| String de conexão do MongoDB sem `?replicaSet=rs0` | O driver conecta em modo avulso e a transação falha **em runtime**, não no boot — possivelmente semanas depois. Todo serviço documental precisa do parâmetro. ADR-008 |
| Vai acrescentar rota no gateway | Por **recurso**, não por serviço, e `merchantId` sempre na mesma posição do caminho. Ordem de predicado importa: o primeiro que casa vence. Nunca acrescente um `/merchants/**` genérico acima dos específicos. ADR-012 |
| Tentado a autorizar no gateway | Não. O gateway autentica; quem autoriza é o serviço, porque só ele sabe qual permissão cada endpoint exige. ADR-011 e ADR-012 |
| Vai acrescentar um starter ao build | **Starter no classpath é dependência, mesmo sem uso.** Ele registra indicador de saúde, e o serviço passa a se declarar fora de serviço por uma peça que ninguém chama — aconteceu duas vezes: Redis no `merchant` e no `gateway`. Starter entra quando há código que o use. Persistência além do banco principal aponta a decisão que a pôs ali (coluna "Por quê" da ADR-021) |
| Vai escrever uma lista fechada — "nada além disso" | **Uma frase que fecha um conjunto diz onde o conjunto é mantido.** A da ADR-037 envelheceu em outro arquivo em uma semana; a coluna da ADR-021 sobreviveu um mês à decisão que a invalidou. Quem estende o conjunto emenda a frase na mesma rodada |
| Vai fazer um serviço usar código de outro — um segundo `project(...)` num módulo | **Não.** A ADR-001 permite `:value-types` e nada mais, e desde a ADR-040 a regra é "um, e só um" — lista de exceção com um item cresce por argumento razoável. O `check` recusa: `verificarDependenciaEntreModulos`, em `build-logic`, roda nos dez módulos. Precisou de uma segunda aresta? Porta HTTP, evento, ou emenda à ADR-001 — nessa ordem |
| Vai escrever regra de arquitetura que cite o Jackson | **Jackson 2 e Jackson 3 são pacotes-raiz diferentes**: `com.fasterxml.jackson..` e `tools.jackson..`. O Boot 4 injeta o segundo, e uma regra que só proíba o primeiro deixa passar o que importa. Regra de arquitetura que cite um cita os dois. Já custou um contexto que não subia, na C-B |
| Vai acrescentar `modalidade` na cotação | Não. Preço não varia por modalidade e `cotar` não a recebe. A diferença é a taxa (ADR-020) mais o `descontoDeRetirada` (ADR-024). Se a modalidade virar parâmetro do preço, ela vira pergunta de abertura da conversa |
| Vai somar alguma coisa em `desconto` | Hoje `desconto` tem origem única — o desconto de retirada. Antes de acrescentar cupom, decomponha o campo: senão o comerciante deixa de separar o que deu para incentivar retirada do que queimou em promoção. ADR-024 |
| Vai perguntar "que dia é hoje" | Não existe sem a loja. É `diaOperacional(instante, fusoHorario)`, com corte às 04:00 — a venda à 01:30 de domingo é do dia operacional de sábado. ADR-025 |
| Vai gravar hora de funcionamento, expediente ou fechamento | Instante em UTC no banco; a conversão para o calendário da loja acontece na leitura, com a zona explícita. Hora local persistida é uma hora sem lugar |
| Vai recalcular o dia de um lançamento pelo momento dele | Não. `Lancamento` herda o `diaOperacional` da jornada, que é congelado na abertura. Recalcular quebra J9 num caso raro e invisível. ADR-025 |
| Vai configurar retentativa de consumidor | Quatro tentativas com espera crescente (1 s, 4 s, 16 s), depois fila morta. **Nunca** requeue imediato — é laço apertado. **Nunca** retentativa infinita — é como se perde pedido em silêncio. ADR-026 |
| Achou mensagem na fila morta | Não cancele pedido. Nunca. Leia `docs/operacao/mensagem-na-fila-morta.md` antes de mexer — e decida primeiro se a causa é transitória ou permanente, porque reprocessar causa permanente só muda o horário |
| Apareceu sobra no fechamento de caixa | **Confira a fila morta do `settlement` antes de falar com o entregador.** Liquidação que não virou lançamento faz o dinheiro esperado ficar menor que o real, e a diferença aparece como sobra — parece erro de caixa e é mensagem perdida. ADR-026 §6 |
| Vai acrescentar campo a um evento | Opcional é compatível; obrigatório não é. Valor novo em enum **não** é compatível. Mudar o significado mantendo nome e tipo é a pior de todas, e nenhum esquema pega. ADR-027 |
| Vai mudar produtor e consumidor de um evento | Consumidor primeiro, sempre: entende as duas versões, depois o produtor muda, depois a tolerância sai — e só depois de a fila drenar. ADR-027 §3 |
| Vai validar pedido mínimo | Sobre `subtotalDosItens`, **nunca** sobre o `total`. Sobre o total, a taxa de entrega ajuda o cliente a atingir o mínimo, e o mínimo efetivo passa a depender do bairro. ADR-028 |
| Vai implementar recuperação de acesso | São **dois** problemas, não um. Recuperar conta é provar que você é aquela pessoa; recuperar estabelecimento é provar que o negócio é seu. O segundo **nunca** altera credencial de `Usuario` — cria ou promove `Membro`. Resetar a senha daria acesso a todas as outras lojas do mesmo usuário. ADR-029 §2 |
| Vai aceitar o CNPJ como prova de titularidade | Não. O documento do estabelecimento é **público no Brasil** — um ex-funcionário sabe, um estranho descobre. Exige-se documento do titular **mais** um elemento que só quem opera a loja controla: o número do canal da loja ou a origem do pagamento. ADR-029 §3 |
| Vai executar recuperação assim que a prova convencer | Não. Notifica todos os membros ativos e **espera a janela** — hoje 72 h, proposta. Sem ela a tomada de conta é instantânea e irreversível, porque quem entra remove os outros. Contestação encerra o pedido. `docs/operacao/recuperacao-de-acesso.md` |
| Vai escolher base legal para um tratamento novo | Execução de contrato é o padrão. Legítimo interesse tem **exatamente duas** hipóteses, ambas com teste de balanceamento escrito em `docs/operacao/legitimo-interesse.md` — uma terceira exige teste novo. Consentimento quase nunca: é revogável, e revogação obriga a apagar. ADR-013 §2 |
| Vai implementar devolução ou estorno | São coisas diferentes. Estorno é uma **forma** de devolver, e só existe onde a plataforma custodiou o valor — cartão e Pix online, marco 8. No marco 4 não há um único caso: o sistema registra que é devido e alguém devolve por fora. ADR-030 |
| Vai registrar devolução como liquidação negativa | Não. `Σ valorEfetivo` bateria sozinha e o custo apareceria em outro lugar: J1 e J3 passariam a filtrar sinal, o fechamento somaria dinheiro que o entregador não viu, e pagamento parcial deixaria de se distinguir de devolução. ADR-030 §3 |
| Chegou webhook de Pix com o pedido já `CANCELADO` | Confirme a liquidação assim mesmo e gere `Devolucao` de origem `CONFIRMACAO_APOS_CANCELAMENTO`. Recusar não traz o dinheiro de volta, só o esconde — e `CANCELADO` é terminal, não há para onde levar o pedido. ADR-030 §6 |
| Vai validar assinatura de webhook | Sobre o **corpo bruto**, antes de desserializar. Conferir o que já foi normalizado é não conferir. E a idempotência é pela chave **do provedor**, não pela nossa — o PSP reenvia por desenho. `pagamento.md` §4 |
| Vai gerar cobrança Pix | Grave a correlação `txid ↔ pedidoId` **antes** de devolver o QR. O cliente paga em três segundos; o webhook pode chegar antes da sua resposta síncrona. `pagamento.md` §3, B5 |
| Achou divergência com o extrato do PSP | Não edite o nosso lado para bater com o deles. Nunca confirme liquidação na mão. `docs/operacao/reconciliacao-de-pagamento.md` — e confira a fila morta antes, que é a causa mais provável |
| Vai criar um evento novo | O nome tem de ser **único no repositório inteiro**, não no serviço. Varra os nomes existentes antes de escolher — a varredura está em `contracts/README.md`. Nome repetido entre serviços falha o build a partir do marco 3. ADR-031 |
| Vai criar classe, tabela ou coluna | Domínio em português — `Usuario`, `estabelecimento_id`, `idx_pedido_estado`. Framework e infraestrutura em inglês. Padrão de engenharia em inglês mesmo no domínio: `Money` não vira `Dinheiro`, porque é nome de padrão e não palavra de negócio. ADR-035 |
| Vai criar rota | Prefixo e serviço em inglês — `/api/v1/catalog/**`. **Identificador e recurso em português** — `/merchants/{estabelecimentoId}/pedidos`. O adaptador traduz; é a função dele. ADR-012 emendada, ADR-035 §3 |
| Vai renomear campo do domínio | Renomeie também **as citações com força de regra** em outras ADRs e documentos — elas apontam para o campo, não registram a decisão de quem as escreveu. Ficam como estão: o modelo que a própria ADR decidiu, e tabelas de exemplo com números concretos. Um renome pela metade é pior que um nome errado consistente (ADR-031, mesmo princípio) |
| Achou dois eventos com o mesmo nome | Renomeie o que nomeou um **atributo** em vez de um fato — quase sempre é um só dos dois. E renomeie **antes** de existir esquema e consumidor: depois disso são três implantações (ADR-027 §3), não uma edição de texto |
| Vai consumir o evento de abertura da loja | É `ExpedienteAlteradoV1`, do `merchant`. Só reativa `ESGOTADO_HOJE` quando `motivo = ABERTURA_DE_EXPEDIENTE` — retomada de pausa não reativa nada, e a idempotência é por `expedienteDeReferencia`, nunca pelo id da mensagem — com a comparação `carimbo < expediente que abriu`, nunca `≠` (G-C1). C11 |
| Vai registrar dinheiro voltando para alguém | Três coisas se chamam devolver, e só uma é `Devolucao`. Troco na porta **não é nada** — se anula sozinho. Falta de moeda é `Ajuste` (H5.2); troco dado a mais é `divergencia`. Entregador acertando com a loja é `saldoLiquido < 0`. `Devolucao` é só loja → cliente, quando entrou mais do que o pedido veio a valer. ADR-030, `liquidacao.md` §4.2 |
| Vai acrescentar consumidor a um evento | A declaração vive em `contracts/eventos.md`, e o comportamento no documento de domínio do consumidor. **Os dois, na mesma alteração.** Produtor que declara consumidor sem o consumidor documentar o que faz é como se acumulam handlers vazios. ADR-031 |
| Vai fazer um serviço saber o estado de um pedido | Pergunte, não projete — a menos que o serviço reaja continuamente. Guarda avaliada num instante vira consulta síncrona; projeção mantida por evento pode divergir, e a guarda passa quando não devia, em silêncio. ADR-032 |
| Vai fechar a jornada de um entregador | A conferência só abre se ele não tiver pedido em `SAIU_PARA_ENTREGA` nem `NAO_ENTREGUE` — consulta ao `order`, no instante. Consulta que falha **recusa**, e não há caminho alternativo: fechar caixa sem saber se há dinheiro na rua é pior que não fechar. ADR-032 |
| Achou uma alteração de vínculo de entregador | O `settlement` **não** reage a ela. `vinculoSnapshot` é congelado na abertura (J6) — mudar remuneração no meio do turno é precisamente o que a invariante impede |
| Vai procurar o carrinho | Não existe. É `rascunhoDePedido`, campo do agregado `Conversa`, em MongoDB — identificadores, nunca valores. Não há TTL próprio: o rascunho vive enquanto a conversa vive. E não há como conter item de duas lojas, porque a conversa é com uma. ADR-006 |
| Vai despachar um pedido | T16 exige jornada aberta, e isso é **consulta ao `settlement`** com cache curto — não projeção local. Falha fechada: sem resposta, sem despacho. É a invariante 1 que depende disso. ADR-033 |
| Vai montar o rodízio | `jornada ABERTA` e a ordem de abertura vêm do `settlement`, em **uma** chamada que devolve a lista com `abertaEm`. Uma por entregador é N chamadas para montar uma sugestão |
| Vai consumir `JornadaAbertaV1` ou `JornadaFechadaV1` | **Para invalidar cache, nunca para projetar.** A verdade mora no `settlement`. Projeção com evento perdido erra até alguém notar; cache com prazo erra por segundos. E vale a fila exclusiva por instância com exchange `topic` (ADR-048 §1), igual ao `VinculoAlteradoV1` — ADR-011 |
| Vai decidir entre perguntar e escutar | Guarda avaliada **uma vez** por ciclo → consulta pura (ADR-032). Guarda avaliada **muitas vezes** → consulta com cache e invalidação por evento (ADR-033). Nos dois casos a verdade mora num serviço só, e nos dois casos falha fechada |
| Vai resolver guarda que depende de outro serviço — ramo 1 | O **erro sobrevive à leitura**? Resposta congelada no agregado, ou mostrada ao cliente como o número que ele confirma → consulta **sem cache**. ADR-034 |
| Vai resolver uma guarda que depende de outro serviço — avaliada **uma vez** por ciclo | Consulta pura (ADR-032). Falha fechada |
| Vai resolver uma guarda que depende de outro serviço — avaliada **muitas vezes** | Consulta + cache + invalidação por evento (ADR-011, ADR-033). Falha fechada |
| Vai cachear a cotação de entrega | **Não.** A resposta da `DeliveryQuotePort` vira `taxaSnapshot` dentro do pedido. Cache aqui não produz dado velho por um minuto — produz dado velho para sempre, gravado, e é o que o cliente paga. ADR-034 |
| Vai perguntar se a loja está aberta | `OperacaoDoEstabelecimentoPort`, que já compõe horário **e** pausa — não recomponha a regra das faixas que cruzam a meia-noite fora do `merchant`. Cache curto, invalidada por `ExpedienteAlteradoV1`, falha fechada |
| Vai cachear cotação em qualquer serviço | Não, nos dois lugares onde ela existe. No `order` a resposta vira `taxaSnapshot`; no `conversation` ela vira o total que o cliente confirma e que T01 vai contradizer. ADR-034 §1 |
| Vai publicar porta nova no compose | `127.0.0.1:` na frente, sempre. Sem o prefixo, o Docker liga em `0.0.0.0` e o serviço fica alcançável da rede local — e dois dos bancos deste compose sobem sem senha |
| Vai preencher o `.env` achando que autenticou o Mongo | Não autenticou. O `MONGO_URI` dos serviços não tem credencial e o container não lê `MONGO_USER`. É decisão registrada no `docker-compose.yml`, e o que protege é o bind em `127.0.0.1` |
| Vai escrever peça de infraestrutura — workflow, política de reinício, guardião, varredura | **Force uma execução no mesmo dia.** Nove peças deste projeto tinham garantia escrita e nunca haviam rodado: o guardião da VM — a tarefa `WSL Ubuntu keepalive`, escrita como decisão de *não* ter guardião e criada só em 30/08; as políticas de reinício (`restart: unless-stopped` e `systemctl enable docker`); o gitleaks, dentro de um pipeline que nunca disparava; os nove pipelines, quebrados desde o commit inicial por falta do bit de execução no `gradlew`; `flyway-core` sem o `spring-boot-starter-flyway` — cinco serviços relacionais sem migration nenhuma até 07/09; `contracts/openapi/`, que prometia "um arquivo por serviço, validado no CI" e tinha um `.gitkeep`; `:value-types`, treze testes que workflow nenhum rodava; o stack de observabilidade inteiro — quatro contêineres, dez linhas de exposição e um `prometheus.yml` raspando um endpoint que nunca existiu (ADR-041); e os oito `application-test.yml`, que só valeriam com `@ActiveProfiles("test")` e nunca tiveram um. Nenhuma foi descuido de escrita — todas foram ausência de execução. Peça que nunca rodou não é peça, é intenção |
| Vai escrever peça de infraestrutura para um marco distante | Não escreva ainda. A regra acima manda forçar uma execução no mesmo dia, e peça de marco distante **não tem como** ser executada hoje — então ela nasce exatamente como as nove. O stack de observabilidade inteiro foi escrito no marco 0 para o marco 11: Prometheus, Grafana, Loki e Tempo, quatro contêineres, nenhuma entrada, e um `prometheus.yml` raspando `/actuator/prometheus` nos nove serviços, que nunca existiu em nenhum. Saiu no marco 1 — ADR-041 |
| Vai criar workflow com filtro de caminho | O filtro precisa cobrir tudo que muda o resultado do build, não só o código do módulo: `gradlew`, `gradle/wrapper/**`, `settings.gradle.kts`, o catálogo de versões e o workflow reutilizável. E declare `workflow_dispatch`, senão não há como disparar sob demanda. Em 30/08 o commit que consertou o `gradlew` não disparou nenhum dos nove workflows que ele desbloqueava |
| Vai editar uma migration | **Só se ela nunca rodou contra um banco que sobrevive.** No Testcontainers vale à vontade — o banco morre no fim do teste. Contra dev, outra máquina ou homologação, o Flyway grava o checksum em `flyway_schema_history` e recusa subir a aplicação se o arquivo mudar depois disso. Não é disciplina, é o que a ferramenta faz sozinha. Migration é editável até rodar em banco que sobrevive; depois é história, e correção vira `V2` |
| Uma funcionalidade parece obviamente necessária | **É sinal de conferir contra o PRD, não de começar a escrever.** "App de delivery" significa iFood para quase todo mundo — carrinho, catálogo navegável, avaliação de entregador, rastreio no mapa, app do consumidor. P1, P3, P4 e P6, mais a ADR-004 (um estabelecimento por pedido) e a ADR-006 (sem carrinho, `Conversa.rascunhoDePedido`), descartam a maior parte disso. O óbvio chega com desenho pronto, sem ter passado por decisão nenhuma — foi essa leitura que segurou metade do rascunho do front-end |
| Vai depurar SQL num teste com `spring.jpa.show-sql=true` | Não aparece. O `delivery.java-conventions` tem `showStandardStreams = false` no `testLogging`, e a saída padrão do teste morre ali. Use `logging.level.org.hibernate.SQL=DEBUG` e leia o XML em `build/test-results/test/`. Descoberto na A2b, medindo o produto cartesiano do `buscarPorId` |
| Vai criar um `Usuario` | Não há `INSERT` nem caso de uso de criação direta. São dois passos: `POST /api/v1/auth/verification-code` e `POST /api/v1/auth/signup` com o código. O `telefoneVerificadoEm` é o instante em que o código foi conferido — é o que torna a U4 verdadeira em vez de afirmada. Enquanto não houver `CanalPort`, quem entrega o código é o operador, lendo a tabela. ADR-042 |
| Vai tomar cadeado pessimista numa entidade com `@ElementCollection` | Não use `@Lock(PESSIMISTIC_WRITE)` sobre ela. A consulta sai com um `left join` por coleção — cinco, no `EstabelecimentoJpaEntity` — e o PostgreSQL **recusa** `FOR UPDATE` sobre o lado anulável de um `LEFT JOIN`. É erro em tempo de execução, e só aparece sob concorrência. Consulta nativa escalar (`select id from ... for update`) não carrega entidade e não emite `join`. B1 |
| Vai testar constraint de banco num teste de integração | `entityManager.flush()` chamado direto **não passa** pela tradução de exceção do Spring — ela só age sobre beans `@Repository`. O que vaza é o `ConstraintViolationException` do Hibernate, não o `DataIntegrityViolationException`. Asserte com `hasStackTraceContaining("<nome_da_constraint>")`, que é o padrão do `EstabelecimentoRepositorioJpaIT`. Custou dois testes vermelhos na B1 |
| Vai modelar estado de um agregado | Separe **fase** de **ato**. Cancelar é ato: alguém decidiu, num instante que se registra. Expirar não é — é o relógio passando de uma data que já está guardada, e virá estado só se alguém escrever uma rotina para virar a chave. Dois valores de enum deste repositório eram fase disfarçada de estado: `Membro.CONVIDADO` (B1) e `Convite.EXPIRADO` (B2) |
| Vai consertar produto cartesiano de coleções `EAGER` | `@BatchSize` **não serve**: ele agrupa o carregamento de várias entidades-donas, e o problema é uma dona com várias coleções na mesma consulta. Use `@Fetch(FetchMode.SUBSELECT)` — um `select` por coleção, com subconsulta que repete o filtro do dono. `SELECT` também desfaz o produto, mas custa 1 + NxM ao carregar uma lista; `SUBSELECT` custa sempre 1 + M. Eu recomendei `@BatchSize` na A2b e estava errado — C-A |
| Testcontainers falhou e você consertou a ponte do Docker | Não basta. Ele guarda o resultado negativo de "não achei Docker" **dentro do daemon do Gradle**, que sobrevive entre execuções: `Previous attempts to find a Docker environment failed. Will not retry.` Rode `./gradlew --stop` depois de consertar a ponte, ou a próxima execução falha igual com a ponte no ar. Custou três execuções na B2 |
| Vai publicar um evento | **Nunca fora do outbox.** A invariante 7 não tem exceção, e a porta `Outbox` não tem método `publicar` justamente para que ninguém possa chamá-lo. Toda escrita que muda vínculo passa pelo `GerenciarEquipeService`: as sete operações pelo funil `executar(...)`, o aceite de convite por `registrarVinculoNascido`, na mesma transação. Operação nova = método novo no funil, nunca `rabbitTemplate.send` avulso. E o formato é o envelope de `contracts/events/_envelope-v1.json`, com o `eventType` sem a versão. ADR-043 |
| Vai travar linha com `SKIP LOCKED` | **Não existe em JPQL.** `@Lock(PESSIMISTIC_WRITE)` gera `FOR UPDATE` e para por aí. Trava de linha se escreve em SQL nativo — segunda vez no repositório: o cadeado da B1 e o lote do relay do outbox |
| Vai escrever teste de integração no `merchant` | Estenda `support.Infraestrutura`, que sobe PostgreSQL e RabbitMQ **uma vez para a JVM inteira** (singleton do Testcontainers; o Ryuk derruba no fim). `@Container` amarra o contêiner à classe, e com dois contêineres isso multiplica a execução. O banco passa a ser compartilhado entre classes: asserção sobre a tabela inteira precisa de filtro próprio, `@Transactional` ou limpeza no `@BeforeEach` — nunca `@DirtiesContext`. Teste que não publica declara `delivery.outbox.habilitado=false`. C-B |
| Vai apontar o RabbitMQ para um contêiner de teste | Registre `spring.rabbitmq.addresses`, **não** `host` e `port`. O `application.yml` define `addresses`, e com ela definida o Spring Boot ignora `host` e `port` em silêncio — o teste falaria com `localhost:5672`. Custou uma leitura de metadado na C-B |
| Vai fazer um serviço perguntar algo a outro | **Não existe credencial de serviço neste sistema, e não é esquecimento.** Encaminhe o token de quem pediu — ADR-045. A rota vive sob `/internal/`, que o gateway não roteia, e responde sobre *o portador do token*, nunca sobre um `usuarioId` no caminho. Precisa autorizar sem pessoa do outro lado? Aí a decisão é outra, e ela ainda não foi tomada |
| Vai configurar MongoDB no Spring Boot 4 | **A chave é `spring.mongodb.uri`, não `spring.data.mongodb.uri`.** A antiga está depreciada com nível `error` desde a 4.0.0 — não é lida —, e o serviço conecta em silêncio no padrão `mongodb://localhost/test`, que nesta máquina é **o Mongo do compose**. O primeiro teste do `catalog` gravou lá em vez de no contêiner (G-B1). O mesmo vale para `spring.mongodb.representation.uuid`: o padrão agora é `unspecified`, e o driver se recusa a gravar `UUID` — declare `standard`. **O `conversation` foi corrigido em 27/09/2026 e a correção ainda não foi exercida** — o serviço não tem nenhum teste que suba contexto. Vale enquanto isso o que vale para toda configuração sem ativação: ela não está provada |
| Contêiner que sobe não é contêiner que é usado | Uma classe-base pode subir o Testcontainers, a aplicação falar com outro servidor, e todos os testes passarem — foi o que a G-B1 fez, escrevendo no MongoDB de desenvolvimento da máquina. Uma chave de propriedade depreciada basta. **Todo serviço com classe-base de contêiner tem um `ConteinerDeVerdadeIT`**: grava pela porta da aplicação e procura o registro numa conexão aberta **a partir do contêiner**. Não compare endereços em replica set — o nó anuncia o nome interno dele, e o teste falharia estando certo. ADR-014, emenda de 27/09/2026 |
| `bootRun` de um serviço documental não conecta ao Mongo | O `mongo-init` registra o membro do replica set como `mongodb:27017`, nome que só resolve dentro da rede do Docker; com `?replicaSet=rs0` o driver descobre esse nome e tenta falar com ele, e da máquina não resolve. Contêiner e Testcontainers não são afetados. Para uma sessão local, tire o `?replicaSet=rs0`: num nó só o driver conecta direto no primário e **a transação continua funcionando** — medido no `TransacaoDoMongoIT`, que roda exatamente assim (ADR-008 emendada). O `bootRun` em si ainda não foi medido. **Gatilho:** o primeiro `bootRun` de `catalog` ou `conversation` que alguém precise fazer |
| Configuração sem ativação | **Um bloco de YAML correto não prova que a peça roda.** O Mongock ficou desde o primeiro commit assim: configurado, comentado, corrigido — e nunca executado, porque faltava `@EnableMongock` e nenhum teste subia contexto. Para toda peça que se liga por anotação ou autoconfiguração, escreva um teste que afirme o **resultado no mundo** — o índice existe, a fila existe, a linha foi gravada. Contexto que sobe não é peça que roda |
| Vai escrever `changeUnit` que cria índice ou validador | Em `@BeforeExecution`, com reversão em `@RollbackBeforeExecution` — **não** em `@Execution`. Com `MongoTransactionManager` no contexto, o Mongock roda o `@Execution` numa transação, e o Mongo recusa `createIndexes` ali (erro 72). `@Execution` é para transformação de dado. ADR-007 emendada |
| Vai resolver acesso a partir de `MembroRepositorio.buscarPorUsuarioELoja` ou `buscarPorUsuario` | **Ele devolve o vínculo suspenso e o removido.** O adaptador faz `findByUsuarioIdAndEstabelecimentoId` sem filtrar estado, e o contrato do `AutorizacaoComercialPort` (`estabelecimento.md` §3) diz "vazio quando não há vínculo **ativo**". O `ConsultarEquipeService` sobrevive porque `Membro.pode(...)` confere `ativo()` por dentro — quem não chamar `pode(...)` fica sem rede. `filter(Membro::ativo)` explícito em todo caso de uso que resolva acesso sem exigir permissão, como o `ConsultarContextoDeAcessoService` e o `ConsultarMinhasLojasService` (G-B5) — o segundo usa o `buscarPorUsuario`, que devolve qualquer estado pelo mesmo motivo. Sem o filtro, um funcionário demitido continua autorizado em todos os serviços, ou vê no seletor a loja de onde saiu, e o caminho feliz passa |
| Vai regravar um contrato OpenAPI pelo PowerShell | **Ponha o `-D` entre aspas:** `'-Dopenapi.atualizar=true'`. Sem aspas, o PowerShell parte o argumento no ponto e o Gradle recebe `.atualizar=true` como nome de tarefa — o build falha com "Task not found", e um filtro de saída que só mostra as primeiras linhas esconde a falha. Custou três execuções na G-B2 |
| Serviço com o starter do AMQP responde 503 no `/actuator/health` do teste | O indicador é registrado pelo **starter**, não pelo uso: um serviço que ainda não publica nem consome continua sendo reprovado por ele quando o broker não existe. Suba o contêiner do RabbitMQ na classe-base dos testes de integração, mesmo antes de haver mensagem — o `catalog` fez isso na G-B3. **Não desligue o indicador**: o `merchant` fez isso na C-A e teve de desfazer na C-B, porque um serviço que se declara são sem conseguir falar com a infraestrutura é a pior forma de indisponibilidade, a que o orquestrador não vê |
| Vai chamar outro serviço por HTTP | Até a G-B3 não existia nenhum cliente de saída no repositório, e o que existe agora é um só: `AutorizacaoComercialHttp`, no `catalog`. **Tempo limite próprio e curto** — de conexão e de leitura —, porque a chamada acontece em toda requisição, e segurar é encher a fila de conexões esperando um serviço que já caiu. E separe **403** (é uma resposta) de **401, 5xx e silêncio** (não são): os dois negam, e só o primeiro poderá ser cacheado. ADR-011, emenda de 28/09/2026 |
| Vai expor `Pageable` num controlador | **`@ParameterObject`** junto do `@PageableDefault`. Sem ele o springdoc descreve a paginação como um parâmetro de consulta só, do tipo objeto e **obrigatório** — e o contrato congelado (ADR-039) passa a descrever uma rota que nenhum cliente consegue chamar. Custou uma regravação na G-B3 |
| Vai usar o instante de um evento como dia | **Instante não é dia.** O `occurredAt` de um evento e o `expedienteDeReferencia` dele divergem todo dia, entre a meia-noite e as 04:00 — que é justamente quando a pizzaria está vendendo. Dia operacional vem de `DiaOperacional.de(instante, fuso)` (ADR-025), e só o `merchant` calcula. Quem recebe o valor, compara |
| Vai tocar em tela, formulário ou rota do front | O `docs/front/premissas-do-front.md` lista o que o produto **decidiu não ter** — carrinho, mapa, estoque, vitrine de lojas, pool de corridas —, cada item com a premissa que o mata. Um protótipo feito por referência de mercado redesenha metade. Leia o documento **antes** de desenhar. E a regra mais fácil de errar não é tela: **o cliente nunca declara o que pode fazer.** Sem seletor de perfil no login, sem rota escolhida por papel guardado no navegador. O token tem seis claims e nenhuma permissão; quem autoriza é o `merchant`, por loja, por requisição |
| Vai mexer no CORS, ou o front deixou de chamar a API | A lista de origens mora **só no gateway** (`CORS_ALLOWED_ORIGINS`), e o padrão do `application.yml` é `http://localhost:5173`. No `docker-compose.yml` a variável precisa de `:-` e não `:` — com `:`, um `.env` sem valor passa string vazia, que **ganha do padrão do Spring** e bloqueia toda origem. Quando o navegador der "Failed to fetch" com o servidor de pé, o suspeito é este, e o erro **não** diz que é CORS. Confira `CORS_ALLOWED_ORIGINS` no `.env` e a forma `${VAR:-padrão}` no compose. O `.env.example` já diz `5173`; um `.env` copiado antes disso nasceu com `http://localhost:3000`, a porta do Create React App, e ficou assim até a I-A — porque nada tinha subido. E não ponha proxy no Vite para contornar: o proxy faz as chamadas saírem da mesma origem e a configuração errada só apareceria em produção |
| Vai declarar uma ferramenta Node no `package.json` | Até 29/09/2026 a raiz declarava quatro — redocly, spectral, asyncapi-cli, ajv — sem lockfile, sem `node_modules`, sem nenhum workflow chamando `npm`, e com os scripts apontando para `*.yaml` quando os contratos são `*.json`. Mais uma peça vestida de funcionando, depois das nove da armadilha de infraestrutura e do Mongock. Ferramenta declarada tem de ter workflow que a execute e lockfile commitado, na mesma rodada. *"Peça que nunca rodou não é peça, é intenção"* — e uma ferramenta de validação que nunca validou é pior do que nenhuma, porque parece que alguém já conferiu |
| Vai publicar num broker, ou revisar quem publica | `publisher-confirm-type: correlated` está em sete `application.yml` desde a C-B e **nunca foi esperado por ninguém** até a G-B4. Pior: sem `mandatory`, mensagem sem fila de destino é **descartada em silêncio** — todo `VinculoAlteradoV1` publicado antes da G-B4 foi descartado, e o outbox marcou cada um como entregue. Confirmação diz *"recebi"*, não *"entreguei"*: o `ack` vem mesmo quando a mensagem é descartada, e o que revela o descarte é a **devolução**, que só existe com `mandatory`. Marque a linha como publicada **depois** do `ack` sem devolução, nunca depois do `send` — é o que o `RelayDoOutbox` faz desde a G-B4 (ADR-043, emenda de 29/09/2026) |
| Vai escrever um consumidor de evento | Não há retentativa universal: consumidor de invalidação não usa a ADR-026 (ADR-048 §3), consumidor que escreve em banco usa. E o `listener.simple.retry` do YAML diverge da ADR-026 — os dois nunca rodaram. Decida antes de escrever qual dos dois você é. Se for fila exclusiva por instância (ADR-011), **não use `processed_messages`**: ela é compartilhada e faria a segunda instância pular o próprio trabalho. E o `SecurityContextHolder` é por thread: teste que consulta a porta de autorização dentro de um `await()` precisa de `pollInSameThread()`, senão o Awaitility pergunta de outra thread, sem portador |
| Vai acrescentar rota sob `/api/v1/me` | **O `/me` não é mais só do `identity`.** `/api/v1/me/estabelecimentos` é do `merchant`, por um predicado **exato** acima do genérico `/api/v1/me/**` (G-B5, ADR-012 emendada). Escolha o serviço pelo recurso, ponha o predicado no lugar certo da ordem no `application.yml` do gateway, e acrescente o caso ao `RoteamentoIT` — ele tem três casos que pegam as três falhas: rota abaixo da genérica, rota que engoliu o `/me`, rota com `/**` no fim |
| Vai expor endpoint do actuator no gateway | A exposição é `health,info`. O `/actuator/gateway` saiu na G-B5: ele lista o mapa das rotas internas, e o gateway **autentica, não autoriza** — qualquer comerciante com token o lia. Se voltar, volta atrás de autorização. E teste de ausência de endpoint se escreve **com** token: sem token o 401 vem antes, e o teste passa com o endpoint exposto ou não (ADR-044, emenda de 30/09/2026) |
| Vai carimbar `expedienteDeReferencia` | **Não calcule.** O dia operacional tem um dono só, o `merchant` (ADR-046 §6). Pergunte em `GET /internal/merchants/{id}/expediente-corrente`, com o token de quem pediu, e **sem cache** — a resposta muda quando a faixa fecha. Com a loja **fechada**, a resposta é o expediente da **próxima abertura**, não o de agora (ADR-049); `409` quando a loja não abre por horário. Na marcação, esse 409 recusa `ESGOTADO_HOJE` e deixa os outros três estados passarem **sem carimbo** — o par `marcadoEm`/`expedienteDeReferencia` nasce inteiro ou não nasce (ADR-049, emenda de 06/10/2026). O instante é o `Clock` do serviço, e nenhum dos dois vem do corpo da requisição |
| Vai mexer numa opção | Só pela raiz, com `Produto.marcarOpcao`. Id desconhecido **estoura**, e isso é de propósito: engolir em silêncio faria a reativação varrer, não achar nada e reportar sucesso. E marcar uma opção pode derrubar o `vendavel` do produto — por isso a rota devolve o produto inteiro |
| Vai gravar um `Produto` | O `ProdutoDocumento` tem `@Version` desde a G-C3a (ADR-052) — o único do repositório. Toda gravação pode falhar por conflito, e o conflito chega por **dois** caminhos: `OptimisticLockingFailureException` fora de transação, e `WriteConflict` (erro 112, entregue como `DataIntegrityViolationException`) dentro de uma. O `ProdutoRepositorioMongo.salvar` traduz o segundo no primeiro; **nenhum chamador pode deixá-lo vazar como 500** — na marcação é 409, na reativação é refazer. A versão atravessa o agregado como valor **opaco**: quem a ler numa regra está errado. E produto novo tem versão nula de propósito — é o que faz o Spring Data inserir em vez de atualizar |
| Vai pôr `@Transactional` num método | Confira quem o chama. Chamada de dentro da **própria classe** não passa pelo proxy do Spring: a anotação é ignorada **sem erro, sem aviso e sem teste vermelho**. Foi por isso que a G-C3a separou o laço da reativação (`ReativarNoExpedienteService`) da unidade de trabalho (`ReativacaoDeUmProduto`) em dois beans, e por isso o `ReativacaoNoExpedienteIT` afirma `AopUtils.isAopProxy` e que a varredura não tem `@Transactional` |
| Vai comparar `expedienteDeReferencia` | `carimbo < expediente que abriu`, nunca `≠`. Com desigualdade, um evento de abertura velho reentregue depois de uma marcação de hoje reativa o que acabou agora — a coluna direita da C11. O `!=` esteve no `catalog` e em dois documentos da G-A.1 até a G-C1 |
| Vai abrir um caminho sem token | A cadeia de cada serviço libera `/actuator/health/**` e mais nada. A documentação (`/swagger-ui/**`, `/v3/api-docs/**`) sai da regra **só** com `delivery.docs.abertas=true`, que é `false` por padrão — ADR-050, hoje no `merchant` e no `catalog`. O teste disso é um **par**: aberta responde 200 com `openapi`, fechada responde 401 — a fechada sozinha passa com a rota inexistente. E nada de documentação passa pelo gateway: o `/actuator/gateway` saiu dele na G-B5 pelo mesmo motivo |

---

## Onde as decisões moram

`docs/architecture/decisions/` — uma ADR por decisão, com contexto, alternativas
consideradas e consequências negativas assumidas.

**Se você for mudar algo que uma ADR decidiu, atualize a ADR na mesma alteração.**
Código que contradiz ADR sem justificativa é o defeito, não a ADR.

Documentos de referência:

- `docs/PRD.md` — o que o produto é, para quem, e o que está fora de escopo
- `docs/architecture/` — como o sistema funciona
- `docs/dominio/` — as regras vigentes: agregados, invariantes, tabelas de
  transição e fórmulas de apuração. **Leia antes de escrever regra de negócio.**
- `contracts/` — OpenAPI, AsyncAPI e JSON Schema dos eventos

---

## Segurança

- Nenhum segredo no repositório. Apenas `.env.example`, com nomes de variáveis.
  Gitleaks roda no CI.
- Token JWT assinado com chave assimétrica; só o `identity-service` emite, todos
  validam pela chave pública.
- Log sem token, senha, documento, dado de pagamento ou coordenada exata.
- Webhook com assinatura validada e processamento idempotente — o endereço é
  público e a mensagem se repete.
- **Todo dado pessoal é alcançável por identificador estável do titular**
  (`usuarioId`, `contatoId`), indexado. Nunca só dentro de texto livre — sem isso
  não há como cumprir pedido de exclusão. ADR-013 §6.

---

## Escopo — o que **não** construir

Estes itens saíram deliberadamente. Não os reintroduza sem revisar as premissas
do PRD:

leilão de ofertas · pool competitivo de entregadores · navegação entre
estabelecimentos · busca no marketplace · cálculo de rota viária e ETA ·
telemetria GPS · aplicativo nativo de consumidor · comissão sobre venda ·
repasse financeiro

Adiados com justificativa (não cortados): controle **quantitativo** de estoque
(marco 10) e rastreamento em mapa (marco 11).
