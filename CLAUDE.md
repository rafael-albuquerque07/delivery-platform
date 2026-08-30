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

Perfis do Compose: `core` (bancos e brokers) · `services` (aplicações) ·
`observability` · `full`. **Não suba `full` para trabalhar** — são muitos
containers e você quase nunca precisa deles.

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

- `domain/` — regra pura, sem framework. Interfaces de repositório ficam aqui.
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
- **`Money`** (value object com `BigDecimal` escala 2 e `RoundingMode.HALF_UP`)
  para todo valor. `double` e `float` são proibidos na cadeia de dinheiro.
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
| CLI no Windows | `winget install Docker.DockerCLI` — binários estáticos, **sem plugins** |
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

Sem `.env`, as oito variáveis interpolam para string vazia e o compose só avisa.
O Postgres recusa subir sem senha — falha alta, e é o comportamento desejado. O
MinIO sobe com credencial em branco, que é pior porque parece funcionar.

### A VM precisa estar viva quando o build roda

O WSL2 encerra a distro quando nenhuma sessão está anexada, e leva os
contêineres junto. O Testcontainers fala com o daemon por `DOCKER_HOST` — se a
VM estiver desligada na hora do `./gradlew test`, o teste falha por motivo que
não tem nada a ver com o código.

**Não há guardião da VM, e é decisão.** Três tentativas, três falhas
diferentes:

| Tentativa | Falhou como |
|---|---|
| Tarefa agendada no logon | `Register-ScheduledTask` → "Acesso negado" (conta de domínio) |
| `.wslconfig` com `vmIdleTimeout` | chave inexistente na WSL 2.6.3 — ignorada com aviso |
| `sleep infinity` na Inicialização | o processo sobreviveu seis horas e a VM caiu assim mesmo |

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
docker info --format "{{.ServerVersion}}"
```

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
| Vai escalar um serviço para 2+ instâncias | A cache de autorização é em processo. O consumidor de `VinculoAlteradoV1` precisa de **fila exclusiva por instância** ligada a um exchange fanout. Com fila compartilhada, só uma instância invalida e as outras seguem com permissão revogada até o TTL — silenciosamente. ADR-011 |
| `allowEmptyShould(true)` no ArchUnit | Muleta temporária: com só `.gitkeep` nas camadas, zero classes = falha. **Remova por serviço assim que ele tiver classes** — mantido depois, uma camada apagada ou pacote renomeado passa em silêncio |
| Declarar versão de Testcontainers | Não declare. O BOM do Boot 4.1.x traz testcontainers-bom 2.x, onde os artefatos mudaram de nome: `org.testcontainers:testcontainers-junit-jupiter`, `-postgresql`, `-mongodb`, `-rabbitmq` |
| Vai somar `total` num relatório | Não. `total` é o valor congelado no fechamento. O que entrou é `Σ Liquidacao.valorEfetivo`; o que o pedido vale hoje é `totalEfetivo` |
| Vai calcular quanto o entregador ganha por pedido | Não existe. Remuneração vem do vínculo e é apurada por jornada — ADR-022 |
| Vai colocar permissão ou papel dentro do JWT | Não. O token carrega **seis** claims e nada mais: `iss`, `sub`, `aud`, `iat`, `exp`, `jti`. Sem `roles`, sem `scope`, sem lista de estabelecimentos. Permissão é do vínculo usuário × estabelecimento e é resolvida por requisição, com cache curto e falha fechada. ADR-011, ADR-015 emendada |
| Serviço de autorização indisponível | **Negar.** Fail-closed é decisão assumida: liberar em caso de dúvida transforma uma queda em acesso irrestrito |
| Faixa de horário que cruza a meia-noite | `fim < inicio` pertence ao dia de início e se estende ao seguinte. Teste com pedido à 01:00 é obrigatório |
| Build local falha em `build-logic` — ponto diferente a cada tentativa | **Causa encontrada em 30/08/2026: exaustão do limite de commit do Windows.** Três `hs_err_pid*.log` na raiz mostraram `Failed to commit metaspace` em dois daemons do Gradle — um 8.14.3 e um **8.9**, versão órfã ainda viva. Não é antivírus: a varredura por DLL de produto de segurança nos dumps veio vazia. A máquina tem 31,4 GB de RAM e limite de commit de 41,4 GB, com 32,5 GB já comprometidos em repouso. Cada JVM **reserva** commit ao subir: dois daemons do Gradle a `-Xmx2g -XX:MaxMetaspaceSize=768m`, mais o daemon do Kotlin (que **não** obedece ao `org.gradle.jvmargs`), mais seis JVMs da extensão `redhat.java`, mais o WSL2 sem `memory=` no `.wslconfig` reservando até metade da RAM. **Conserto:** `memory=12GB` no `.wslconfig`, uma distribuição do Gradle só em `C:\gradle\wrapper\dists`, e não rodar build com o VS Code carregado de JVMs. Nove workflows compilaram limpos em runner Linux no mesmo dia — o `build-logic` sempre esteve bom |
| Vai desabilitar `redhat.java` | O pacote Salesforce Apex o declara como dependência dura e o mantém ligado. Desabilite o Salesforce no workspace junto |
| Tentado a atualizar o Gradle | **A 9.7.1 falha** em `compilePluginsBlocks` neste build-logic. O wrapper fixa 8.14.3, que é a única versão com build verde. Bump é tarefa própria, verificada com `--rerun-tasks --no-build-cache`. **Reavalie depois do `memory=` no `.wslconfig`** — a falha que motivou o pin pode ter sido o mesmo estouro de limite de commit, não incompatibilidade real com a 9.7.1 |
| `GRADLE_USER_HOME` apontando para dentro de `C:\Users\` | Não. Ver a configuração de ambiente acima |
| Uma ferramenta "não existe" na sessão do agente | Confira você mesmo com `Get-Command`. Instalação por usuário fica em `%LOCALAPPDATA%`, e a sessão do agente não herda o PATH do seu perfil. Já aconteceu com o Gradle e com o Docker — nas duas vezes a ferramenta estava instalada |
| Acabou de rodar `SetEnvironmentVariable(...,"User")` | A janela que executou o comando **não enxerga a própria escrita**. Grava no registro para processos futuros. Para testar na hora, `$env:NOME = "valor"` também |
| Abriu aba nova do terminal e a variável não veio | Aba não é processo. A aba nova nasce filha do Windows Terminal que já estava aberto e herda o ambiente **daquele** processo. Feche o Terminal inteiro — e o VS Code — e abra de novo |
| Vai colar um bloco de comandos no shell do WSL | Rode `sudo -v` antes. Se um `sudo` do meio do bloco pedir senha, as linhas seguintes viram tentativas de senha e o terminal as ecoa — parece que repetiu, e na verdade nada rodou |
| Vai tentar manter a VM do WSL2 sempre viva | Não crie guardião. Três tentativas falharam — tarefa agendada (`Acesso negado` em conta de domínio), `vmIdleTimeout` no `.wslconfig` (chave inexistente na WSL 2.6.3), `sleep infinity` (a VM caiu de qualquer jeito). `restart: unless-stopped` + `systemctl enable docker` bastam: ~13 s do zero, e o primeiro `wsl`/`docker` do dia já dispara a volta |
| Vai rodar comando com `$`, `$(...)` ou aspas aninhadas via `wsl -d Ubuntu -- ...` | Não passe pelo PowerShell. Ele mastiga a passagem de argumento e a variável chega vazia — sem erro, o comando roda e mede outra coisa. Entre com `wsl`, rode lá dentro, `exit`. Diagnóstico: `echo "[$VAR]"` — se vier `[]` e você sabe que a variável existe, é isto |
| Vai guardar coordenada, áudio ou número de cartão | Não guarde. Endereço textual e bairro no lugar da coordenada, transcrição no lugar do áudio, `txid` no lugar do cartão. A forma mais barata de cumprir a LGPD é não ter o dado — ADR-013 §4 |
| Restaurou um banco a partir de backup | **Reaplique as exclusões de titular** com data posterior à do backup antes de o serviço voltar a atender. Backup restaurado ressuscita dado apagado — `docs/operacao/exclusao-de-titular.md` |
| String de conexão do MongoDB sem `?replicaSet=rs0` | O driver conecta em modo avulso e a transação falha **em runtime**, não no boot — possivelmente semanas depois. Todo serviço documental precisa do parâmetro. ADR-008 |
| Vai acrescentar rota no gateway | Por **recurso**, não por serviço, e `merchantId` sempre na mesma posição do caminho. Ordem de predicado importa: o primeiro que casa vence. Nunca acrescente um `/merchants/**` genérico acima dos específicos. ADR-012 |
| Tentado a autorizar no gateway | Não. O gateway autentica; quem autoriza é o serviço, porque só ele sabe qual permissão cada endpoint exige. ADR-011 e ADR-012 |
| Vai configurar segurança no gateway | `/api/v1/webhooks/**` é PÚBLICO, sem JWT — o PSP e o provedor do canal não têm token nosso, e autenticam por assinatura no corpo, dentro do serviço. O `SecurityFilterChain` precisa liberar exatamente esse prefixo e exigir autenticação no resto. **Ainda não escrito** — requisito do marco 1. ADR-012 |
| Vai fazer um serviço usar código de outro | Não. Integração é por API ou evento. Nenhum módulo `:services:*` declara outro como dependência — ADR-001. **A verificação no build ainda não existe**; até existir, isto depende de ninguém errar |
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
| Vai consumir o evento de abertura da loja | É `ExpedienteAlteradoV1`, do `merchant`. Só reativa `ESGOTADO_HOJE` quando `motivo = ABERTURA_DE_EXPEDIENTE` — retomada de pausa não reativa nada, e a idempotência é por `expedienteDeReferencia`, nunca pelo id da mensagem. C11 |
| Vai registrar dinheiro voltando para alguém | Três coisas se chamam devolver, e só uma é `Devolucao`. Troco na porta **não é nada** — se anula sozinho. Falta de moeda é `Ajuste` (H5.2); troco dado a mais é `divergencia`. Entregador acertando com a loja é `saldoLiquido < 0`. `Devolucao` é só loja → cliente, quando entrou mais do que o pedido veio a valer. ADR-030, `liquidacao.md` §4.2 |
| Vai acrescentar consumidor a um evento | A declaração vive em `contracts/eventos.md`, e o comportamento no documento de domínio do consumidor. **Os dois, na mesma alteração.** Produtor que declara consumidor sem o consumidor documentar o que faz é como se acumulam handlers vazios. ADR-031 |
| Vai fazer um serviço saber o estado de um pedido | Pergunte, não projete — a menos que o serviço reaja continuamente. Guarda avaliada num instante vira consulta síncrona; projeção mantida por evento pode divergir, e a guarda passa quando não devia, em silêncio. ADR-032 |
| Vai fechar a jornada de um entregador | A conferência só abre se ele não tiver pedido em `SAIU_PARA_ENTREGA` nem `NAO_ENTREGUE` — consulta ao `order`, no instante. Consulta que falha **recusa**, e não há caminho alternativo: fechar caixa sem saber se há dinheiro na rua é pior que não fechar. ADR-032 |
| Achou uma alteração de vínculo de entregador | O `settlement` **não** reage a ela. `vinculoSnapshot` é congelado na abertura (J6) — mudar remuneração no meio do turno é precisamente o que a invariante impede |
| Vai procurar o carrinho | Não existe. É `rascunhoDePedido`, campo do agregado `Conversa`, em MongoDB — identificadores, nunca valores. Não há TTL próprio: o rascunho vive enquanto a conversa vive. E não há como conter item de duas lojas, porque a conversa é com uma. ADR-006 |
| Vai despachar um pedido | T16 exige jornada aberta, e isso é **consulta ao `settlement`** com cache curto — não projeção local. Falha fechada: sem resposta, sem despacho. É a invariante 1 que depende disso. ADR-033 |
| Vai montar o rodízio | `jornada ABERTA` e a ordem de abertura vêm do `settlement`, em **uma** chamada que devolve a lista com `abertaEm`. Uma por entregador é N chamadas para montar uma sugestão |
| Vai consumir `JornadaAbertaV1` ou `JornadaFechadaV1` | **Para invalidar cache, nunca para projetar.** A verdade mora no `settlement`. Projeção com evento perdido erra até alguém notar; cache com prazo erra por segundos. E vale a fila exclusiva por instância com exchange fanout, igual ao `VinculoAlteradoV1` — ADR-011 |
| Vai decidir entre perguntar e escutar | Guarda avaliada **uma vez** por ciclo → consulta pura (ADR-032). Guarda avaliada **muitas vezes** → consulta com cache e invalidação por evento (ADR-033). Nos dois casos a verdade mora num serviço só, e nos dois casos falha fechada |
| Vai resolver guarda que depende de outro serviço — ramo 1 | O **erro sobrevive à leitura**? Resposta congelada no agregado, ou mostrada ao cliente como o número que ele confirma → consulta **sem cache**. ADR-034 |
| Vai resolver uma guarda que depende de outro serviço — avaliada **uma vez** por ciclo | Consulta pura (ADR-032). Falha fechada |
| Vai resolver uma guarda que depende de outro serviço — avaliada **muitas vezes** | Consulta + cache + invalidação por evento (ADR-011, ADR-033). Falha fechada |
| Vai cachear a cotação de entrega | **Não.** A resposta da `DeliveryQuotePort` vira `taxaSnapshot` dentro do pedido. Cache aqui não produz dado velho por um minuto — produz dado velho para sempre, gravado, e é o que o cliente paga. ADR-034 |
| Vai perguntar se a loja está aberta | `OperacaoDoEstabelecimentoPort`, que já compõe horário **e** pausa — não recomponha a regra das faixas que cruzam a meia-noite fora do `merchant`. Cache curto, invalidada por `ExpedienteAlteradoV1`, falha fechada |
| Vai cachear cotação em qualquer serviço | Não, nos dois lugares onde ela existe. No `order` a resposta vira `taxaSnapshot`; no `conversation` ela vira o total que o cliente confirma e que T01 vai contradizer. ADR-034 §1 |
| Vai publicar porta nova no compose | `127.0.0.1:` na frente, sempre. Sem o prefixo, o Docker liga em `0.0.0.0` e o serviço fica alcançável da rede local — e dois dos bancos deste compose sobem sem senha |
| Vai preencher o `.env` achando que autenticou o Mongo | Não autenticou. O `MONGO_URI` dos serviços não tem credencial e o container não lê `MONGO_USER`. É decisão registrada no `docker-compose.yml`, e o que protege é o bind em `127.0.0.1` |
| Vai escrever peça de infraestrutura — workflow, política de reinício, guardião, varredura | **Force uma execução no mesmo dia.** Quatro peças deste projeto tinham garantia escrita e nunca haviam rodado: o guardião da VM, as políticas de reinício, o gitleaks (dentro de um pipeline que nunca disparava) e os nove pipelines (quebrados desde o commit inicial por falta do bit de execução no `gradlew`). Nenhuma foi descuido de escrita — todas foram ausência de execução. Peça que nunca rodou não é peça, é intenção |
| Vai criar workflow com filtro de caminho | O filtro precisa cobrir tudo que muda o resultado do build, não só o código do módulo: `gradlew`, `gradle/wrapper/**`, `settings.gradle.kts`, o catálogo de versões e o workflow reutilizável. E declare `workflow_dispatch`, senão não há como disparar sob demanda. Em 30/08 o commit que consertou o `gradlew` não disparou nenhum dos nove workflows que ele desbloqueava |

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
