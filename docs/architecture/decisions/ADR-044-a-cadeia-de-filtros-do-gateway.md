# ADR-044 — A cadeia de filtros do gateway, e o que passa sem token

- **Estado:** aceita
- **Data:** 24/09/2026
- **Fecha:** a exigência escrita na tabela de armadilhas do `CLAUDE.md` — *"precisa liberar exatamente esse prefixo e exigir autenticação no resto. Ainda não escrito — requisito do marco 1"*
- **Emenda:** ADR-037 §1 (a lista que dizia "nada além disso"), ADR-012 (o limite de taxa)
- **Relacionadas:** ADR-011, ADR-015 emendada, ADR-021 emendada, ADR-037, ADR-042

## Contexto

O `infra/gateway` tem um `application.yml` com quinze rotas, um
`GatewayApplication` com o `main`, e três pacotes vazios com `.gitkeep`:
`config/`, `filter/`, `security/`. Nenhum teste. **Nada nunca subiu este módulo.**

Duas consequências, e as duas são do tipo que não dói até doer de uma vez.

### 1. Hoje o gateway recusaria o login e os webhooks

O `spring-boot-starter-oauth2-resource-server` vem pelas convenções, e o
`application.yml` configura `jwk-set-uri`. Sem `SecurityFilterChain` própria, o
Spring Boot registra a cadeia padrão: **`anyRequest().authenticated()`**.

Isso é, letra por letra, o modo de falha que a ADR-012 nomeia ao decidir o
prefixo dos webhooks:

> *"Isso é o erro clássico de configuração de gateway, nas duas direções: ou o
> filtro de autenticação bloqueia o webhook e as notificações somem em silêncio,
> ou a exceção é escrita larga demais e abre mais do que devia."*

A primeira direção está configurada agora. Nenhum PSP conseguiria entregar uma
confirmação de Pix, e ninguém conseguiria fazer o primeiro login — porque exigir
token para emitir token não fecha.

### 2. O namespace das rotas nunca foi verificado

O próprio `application.yml` carrega quinze linhas de aviso dizendo que
`spring.cloud.gateway.server.webmvc.routes` é o prefixo *esperado* da variante
WebMVC, que a variante reativa usa outro, e que **o build ficar verde não prova
nada** porque nada sobe o gateway. O aviso termina mandando um humano rodar
`curl localhost:8080/actuator/gateway/routes`.

Um comentário que instrui uma pessoa a conferir à mão é uma asserção sem
executor. É a mesma categoria de *"peça que nunca rodou não é peça, é
intenção"* — só que aplicada a um módulo inteiro.

## Decisão

### 1. A cadeia existe, e declara três exceções

```
/api/v1/auth/**        público   — o portão de entrada
/api/v1/webhooks/**    público   — assinatura no corpo, validada no serviço
/actuator/health/**    público   — probe de contêiner não carrega credencial
qualquer outra coisa   autenticado
```

**Nada além disso**, e desta vez a frase vem com teste.

### 2. Por que `/api/v1/auth/**` inteiro, e não rota a rota

A alternativa estreita — liberar `login`, `signup` e `verification-code`
nominalmente — foi rejeitada, e o motivo é um fato deste repositório e não uma
preferência.

A ADR-037 §1 lista as rotas públicas do `identity-service` e fecha com **"Nada
além disso."** Uma semana depois (07/09 → 14/09) a ADR-042 acrescentou `POST /api/v1/auth/signup` e
`POST /api/v1/auth/verification-code`, e a ADR-037 nunca soube — não cita nenhuma
das duas, nem a ADR-042. A lista fechada envelheceu em outro arquivo.

Copiar essa lista para o gateway criaria **uma segunda cópia da mesma lista**,
com a mesma tendência a envelhecer, e com um sintoma pior: a próxima rota pública
do `identity` nasceria funcionando no serviço e recusada na borda, com `401` e
sem nenhuma pista de onde a decisão mora.

A composição escolhida é:

- **o gateway abre o prefixo**, porque é o portão e não é ele quem sabe;
- **o `identity` decide rota a rota**, porque é dele o conhecimento — e a cadeia
  dele é `anyRequest().authenticated()` por padrão, de modo que uma rota nova sob
  `/auth` **nasce protegida lá** mesmo passando por aqui.

O prefixo é largo na borda e estreito onde a informação está. Defesa em
profundidade sem lista duplicada.

### 3. O gateway valida emissor e audiência

O `jwk-set-uri` sozinho monta um decoder que confere **assinatura e tempo, e mais
nada** — um token de homologação, assinado pela chave certa, passaria em
produção. É exatamente a falha que a C-A fechou no `merchant`, e ela está aberta
aqui pelo mesmo motivo: o padrão do Resource Server é permissivo e silencioso.

O gateway monta o `JwtDecoder` à mão, com `JwtIssuerValidator` e um
`JwtClaimValidator` para `aud`, **repetindo** o desenho do `merchant`. A
repetição é deliberada: a ADR-001 proíbe o gateway de depender de um serviço, e
um módulo compartilhado de segurança seria um segundo `:value-types` — decisão
maior do que esta rodada. Fica registrado como duplicação conhecida, com gatilho:
o terceiro módulo que precisar do mesmo decoder.

**Emissor e audiência têm uma fonte só.** Quem assina (`identity`) e quem confere
(`merchant`, `gateway`) recebem no `docker-compose.yml` a **mesma** expressão:
`JWT_ISSUER: ${JWT_ISSUER:-http://localhost:8081}` e
`JWT_AUDIENCE: ${JWT_AUDIENCE:-delivery-platform}`. Até esta rodada nenhum dos
três recebia as variáveis — `identity` e `merchant` concordavam por terem o mesmo
valor padrão no próprio `application.yml`. Passar a variável só ao gateway teria
quebrado o sistema de dois jeitos: com o `.env` definindo o emissor, o gateway
leria um valor e o `identity` assinaria com outro; com o `JWT_ISSUER=` vazio do
`.env.example`, o Compose entregaria **string vazia** — que conta como definida,
não dispara a recusa de subir, e faz o gateway recusar todo token em silêncio. O
`:-` cobre o vazio e o ausente.

### 4. O `Authorization` atravessa

O gateway **não remove** o cabeçalho ao encaminhar. A ADR-012 diz que o serviço
revalida o token, e a razão está escrita lá: *"é o que impede que alcançar a rede
interna, por qualquer caminho, equivalha a estar autorizado"*. Um gateway que
autentica e retira o token transformaria todo serviço num serviço que confia na
rede.

Isso vira asserção: um teste confere que o token chega do outro lado.

### 5. CORS sem credenciais

Origens vêm de `cors.allowed-origins`; `allowCredentials` é **falso**. A
autenticação deste sistema é um cabeçalho `Authorization`, não um cookie —
ligar credenciais só abriria a porta para a combinação com `*` que é o erro
clássico de CORS. Métodos e cabeçalhos são listados, não abertos.

### 6. O Redis sai do gateway

O `build.gradle.kts` declara `spring-boot-starter-data-redis` com o comentário
*"usado para rate limit"*, e **não existe `RequestRateLimiter` em lugar nenhum do
repositório**. É a mesma configuração que a emenda à ADR-021 acabou de remover do
`merchant`, com o mesmo efeito: o *starter* registra um indicador de saúde, e um
gateway sem Redis se declara fora de serviço por uma peça que ele não usa.

O limite de taxa que a tabela da ADR-012 atribui ao gateway **não é marco 1**, e
não é uma linha de configuração: exige decidir por IP ou por token, quanto por
minuto, e o que responder no estouro. **Gatilho escrito:** o primeiro ambiente
exposto à internet. Até lá a linha do gateway na ADR-021 continua com persistência
"—", que passa a ser verdade.

### 7. O comentário vira teste

As quinze linhas de aviso sobre o namespace saem do `application.yml`. No lugar
entra `RoteamentoIT`, que **sobe o gateway de verdade** contra oito *upstreams* de
mentira, um por serviço, cada um respondendo o próprio nome e o caminho que
recebeu — e confere as quinze rotas, uma a uma.

Isso não substitui o comentário por um comentário melhor. Substitui uma instrução
para um humano por uma asserção que roda: se o namespace estiver errado, nenhuma
rota casa, e a suíte inteira fica vermelha em vez de o gateway subir calado.

O `/actuator/gateway` **continua exposto**, e agora protegido por autenticação.
Ele é o diagnóstico de primeira pergunta quando uma rota deixa de casar — tirar a
ferramenta na mesma rodada que estreia o subsistema seria mau momento.
**Gatilho escrito:** sai da lista de exposição antes de qualquer ambiente
exposto, junto com a decisão do limite de taxa.

## Consequências

**Positivas**

- Um módulo que nunca rodou passa a rodar em cada `./gradlew build`.
- As quinze rotas da ADR-012 deixam de ser configuração que se espera que
  funcione e passam a ser comportamento verificado.
- O `iss`/`aud` fecha na borda também, e não só no `merchant`.

**Negativas**

- **O decoder está em dois lugares.** Registrado, com gatilho.
- **A suíte do gateway sobe um contexto Spring e nove servidores HTTP** (oito
  *upstreams* mais o JWKS). São em processo e morrem com o teste, mas o `build`
  fica mais lento.
- **O gateway abre `/api/v1/auth/**` inteiro.** Se algum dia nascer uma rota
  sob `/auth` que precise de token, quem a protege é o `identity`. Isso está
  escrito aqui e precisa continuar verdadeiro — é a contrapartida de não
  duplicar a lista.

## Emenda à ADR-037 §1

A ADR-037 lista três rotas públicas do `identity-service` e fecha com **"Nada
além disso."** A frase deixou de ser verdadeira em 14/09, quando a ADR-042
acrescentou duas rotas ao mesmo serviço sem voltar lá.

A lista correta do `identity-service` é de **cinco** rotas:

| Rota | Decidida em |
|---|---|
| `POST /api/v1/auth/login` | ADR-037 |
| `POST /api/v1/auth/signup` | **ADR-042** |
| `POST /api/v1/auth/verification-code` | **ADR-042** |
| `GET /.well-known/jwks.json` | ADR-037 |
| `/actuator/health/**`, qualquer método | ADR-037 |

Conferida contra o `SecurityConfig` do `identity-service` em 24/09: são estas
cinco, e é a cadeia que manda — o `health` de lá é liberado sem restrição de
método, e o actuator só responde `GET` de qualquer jeito.

O defeito não é a lista: é a forma. **Uma frase que fecha um conjunto precisa
dizer onde o conjunto é mantido**, ou ela vira uma afirmação que envelhece em
silêncio — do mesmo jeito que a coluna "PostgreSQL + Redis" da ADR-021
sobreviveu um mês à decisão que a invalidou. A ADR-037 passa a apontar para esta
tabela como a lista viva.
