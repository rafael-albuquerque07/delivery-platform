# ADR-037 — A emissão do access token: a chave, o tempo, e o que fica público

**Status:** Aceita — 07/09/2026
**Relacionada:** ADR-011 (autorização por requisição), ADR-012 (roteamento do
gateway), ADR-015 emendada (claims), ADR-029 (recuperação), ADR-035 (idioma),
ADR-036 (identificador de login)
**Precisa existir antes do primeiro token** — sem ela, seis decisões seriam
tomadas em silêncio por quem escrevesse o `NimbusJwtEncoder`

## Contexto

A ADR-015 decidiu **com o quê** emitir — `NimbusJwtEncoder`, par RSA, JWKS — e a
emenda de 26/08 decidiu **o que vai dentro**: seis claims, nada de permissão. A
ADR-011 decidiu que a permissão é resolvida por requisição.

Nenhuma das duas decide o que segue. Uma leitura do repositório em 07/09/2026
encontrou seis lacunas e um defeito, e o defeito impede a primeira execução.

### O defeito: o sistema não dá a partida em si mesmo

Os nove serviços têm `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`
configurado. **Nenhum tem `SecurityFilterChain`.** Com o starter de Resource
Server no classpath e nenhuma cadeia declarada, o padrão do Spring Boot exige
token válido em toda rota. Logo:

```
serviço quer validar um token
   └─ busca /.well-known/jwks.json no identity-service
         └─ o endpoint exige token válido
               └─ para ter token válido, precisa do JWKS
```

O `identity-service` sofre o mesmo consigo próprio: o `jwk-set-uri` dele aponta
para ele mesmo, e ele sairia pela rede atrás da chave pública que tem em memória —
para levar 401 do próprio filtro. E o `POST /auth/login` exigiria token para
emitir token.

Nada disso apareceu até hoje porque **nenhum token jamais foi emitido**. É a nona
peça deste repositório com garantia escrita e execução zero, e a armadilha do
`CLAUDE.md` que prevê exatamente isso foi escrita ontem.

### `iss` e `aud` estão declarados e ninguém os confere

A tabela da ADR-015 emendada diz que `iss` é *"quem emitiu — confere com o JWKS"*
e `aud` é *"para quais serviços o token vale"*. Com `jwk-set-uri` puro, o Spring
monta um decoder que valida **só tempo**. Nem emissor, nem público.

Dois claims com aparência de controle e nenhum consumidor. É, palavra por palavra,
o argumento com que a emenda da ADR-015 apagou o `scope`.

### E o que ninguém decidiu

Tempo de vida do token — a ADR-015 diz "curto", que não é número. Valor do `aud`.
Origem e formato da chave privada em execução. Algoritmo do hash de senha — nada
no projeto produz hash; o `Usuario` recebe a string pronta. Quais rotas são
públicas. E o refresh token, que a ADR-015 nomeia e não resolve.

---

## Decisão

### 1. A cadeia de filtros existe, e diz o que é público

O `identity-service` declara uma `SecurityFilterChain` explícita. Três rotas
públicas, o resto autenticado:

| Rota | Por quê |
|---|---|
| `POST /api/v1/auth/login` | exigir token para emitir token não fecha |
| `GET /.well-known/jwks.json` | é a chave pública; protegê-la trava a partida do sistema inteiro |
| `GET /actuator/health/**` | *probe* de contêiner não carrega credencial |

**Nada além disso.** Sem `SecurityFilterChain` explícita não existe rota pública,
e sem rota pública não existe primeiro token.

Os outros oito serviços têm cadeia própria a escrever, e a deles tem uma entrada
a mais — `/api/v1/webhooks/**`, público e autenticado por assinatura no corpo.
Não é esta ADR que a escreve, mas fica registrado que a ausência é a mesma.

### 2. A chave privada nunca entra no repositório nem no jar

| O quê | Decisão |
|---|---|
| Tamanho | RSA 2048 |
| Formato | PKCS#8 PEM |
| Origem em execução | caminho de arquivo vindo do ambiente — `delivery.jwt.private-key-path` |
| Origem em teste | PEM gerado em diretório temporário, uma vez por JVM |
| `kid` | *thumbprint* JWK do RFC 7638, derivado da própria chave pública |

**Por que nunca sob `src/main/resources`.** O `.gitignore` casa `jwt-private*` em
qualquer diretório, então o arquivo lá dentro seria ignorado pelo git — e
empacotado pelo Gradle. Ignorada no repositório, publicada no artefato. É o pior
dos dois mundos e não tem aviso.

**Por que PEM gerado, e não par efêmero em memória.** Um par em memória provaria
que o token é validado pela chave correspondente, mas deixaria o carregamento de
PEM sem teste — e é esse o caminho que roda em produção e o que quebra na
primeira implantação. Gerar o arquivo num diretório temporário custa três linhas
e faz o caminho de produção ser o caminho de teste. Nenhum material de chave
entra no repositório: o arquivo nasce temporário e morre com a JVM.

**Por que o `kid` é derivado, e não escolhido.** Rotação vai existir, e id
escolhido à mão descola da chave que nomeia. Thumbprint não descola: muda a chave,
muda o id, sem ninguém lembrar de mudar.

### 3. O access token vale 30 minutos

E o raciocínio importa mais que o número, porque o número vai mudar.

**Por que não é mais curto.** Não há refresh token neste marco (§ "o que não
decide"). Sem refresh, o access token *é* a sessão: quinze minutos significaria
relogar quatro vezes por hora no painel.

**Por que trinta minutos não é imprudente.** O token **não carrega permissão**
(ADR-015 emendada). A janela de revogação de acesso a uma loja não é o tempo de
vida do token — é o TTL de 60 segundos da ADR-011. Demitir o Rafa às 20h corta o
acesso dele em segundos pelo evento, ou em um minuto pelo TTL, **com o token dele
ainda válido**. O que um token longo prolonga é o uso de um token *roubado*, não
o de um acesso *revogado*.

**Quando esta decisão cai.** No dia em que houver refresh token, este número deve
**diminuir** — ele só é trinta porque não há como renovar. Quem escrever a ADR do
refresh deve tratar os trinta minutos como consequência da ausência dela, não como
valor herdado.

Propriedade: `delivery.jwt.access-token-ttl`, com o valor no `application.yml`,
não no código.

### 4. Um público só: `delivery-platform`

`aud` recebe a string `delivery-platform` — uma, não uma lista, não um por
serviço.

Público em JWT existe para impedir que um token emitido para um sistema seja
apresentado a outro. Aqui há **um** sistema com nove processos, todos confiando
no mesmo emissor. Um `aud` por serviço obrigaria o painel a carregar nove tokens
para desenhar uma tela.

O valor vive em `delivery.jwt.audience` e é o mesmo nos nove `application.yml`.

### 5. `iss` e `aud` são validados por validador explícito

Cada serviço compõe o decoder com `JwtValidators.createDefault()` **mais** um
validador de emissor e um de público. Sem isso os dois claims são enfeite.

**Por que não `issuer-uri`, que validaria `iss` de graça.** Ele exige que o
emissor sirva documento de descoberta OIDC — precisamente o que a ADR-015 dispensou
ao recusar o Authorization Server. Ganhar a validação de `iss` ao custo de
implementar descoberta é reabrir a ADR-015 pela porta dos fundos.

Propriedade: `delivery.jwt.issuer`, e o valor tem de ser **idêntico** no emissor e
nos nove validadores. É o tipo de string que quebra em produção por causa de uma
barra no fim.

### 6. A senha é `DelegatingPasswordEncoder`, com bcrypt como padrão

O hash sai prefixado — `{bcrypt}$2a$10$…` — e é isso que torna literalmente
verdadeira a frase do `usuario.md` §3: *"hash moderno carrega o próprio
identificador de algoritmo, custo e sal dentro da string"*.

**Por que não argon2 direto.** É o algoritmo que as recomendações atuais preferem,
e a decisão de usá-lo continua disponível — de graça. Com o prefixo gravado, trocar
o padrão para argon2 não exige migration nem invalidar senha nenhuma: o encoder lê
o hash antigo pelo prefixo, valida, e regrava no formato novo no próximo login. Sem
o prefixo, a mesma troca custaria uma senha nova para cada usuário.

Argon2 hoje exigiria BouncyCastle no classpath — dependência nova num projeto onde
nada de segurança é declarado explicitamente, tudo vem do BOM.

### 7. O login não diz qual metade errou

`POST /api/v1/auth/login`, corpo com `telefone` e `senha` — identificador de
negócio em português, superfície em inglês (ADR-035).

Sucesso devolve `accessToken`, `tokenType` e `expiresIn`. **Não devolve o
`Usuario`**: o token já carrega o `sub`, e devolver nome ou e-mail no corpo do
login espalha dado pessoal por um lugar a mais.

Falha devolve **401 com o mesmo corpo**, seja o telefone inexistente, a senha
errada, ou o canal não verificado (U3). Resposta diferente por caso transforma o
login em verificador de cadastro: quem tem conta aqui, quem não tem. É a mesma
regra que a ADR-011 aplica ao 403, e vale mais aqui, porque o identificador é um
telefone.

E a verificação de senha roda **mesmo quando o telefone não existe**, contra um
hash descartável. Sem isso, o tempo de resposta responde o que o corpo se recusou
a dizer.

---

## O que esta decisão **não** decide

**O refresh token.** Adiado com registro. A ADR-015 já nomeou o desenho — família,
rotação, e reúso de token consumido revogando a família inteira — e nada disso
cabe num marco que ainda não tem painel para exercitar sessão longa. Fica para
ADR própria, e ela deve baixar o TTL de trinta minutos ao chegar.

**Onde mora o validador compartilhado.** Nove serviços vão precisar do mesmo
validador de `iss` e `aud`. Copiá-lo nove vezes é ruim; criar módulo comum é
decisão de arquitetura que este projeto evitou de propósito. **Não se decide agora
porque só existe um serviço validando de verdade.** Quando o segundo chegar, a
pergunta chega junto, com evidência.

**Limite de tentativas de login.** Continua aberto no `usuario.md` §7, e agora com
mais motivo: sem ele, o 401 indistinguível ainda pode ser varrido por força bruta.

**Revogação por `jti`.** O claim existe e nada o lê. É honesto: ele está lá porque
o refresh vai precisar dele, e a ADR-015 já disse isso.

**A cadeia de filtros dos outros oito serviços**, incluindo a rota pública de
webhook. Mesma ausência, escopo diferente.

---

## Consequências

**Positivas**

- O sistema passa a conseguir dar a partida: a primeira busca de JWKS não bate em
  401.
- `iss` e `aud` deixam de ser enfeite — ou são validados, ou não estariam no token.
- Nenhum material de chave existe no repositório, em nenhum ambiente, nem para
  teste.
- Trocar bcrypt por argon2 no futuro não custa migration nem senha de ninguém.
- O tempo de vida do token está escrito, com o motivo, em vez de digitado dentro
  de uma classe.

**Negativas**

- **Trinta minutos é longo para um access token**, e a razão é a ausência de
  refresh. É dívida assumida, não escolha de segurança.
- **`delivery.jwt.issuer` precisa ser idêntico em dez lugares.** Uma barra a mais
  no fim derruba a validação em produção, e o erro não diz isso com clareza.
- **O par de chaves passa a ser operação:** gerar, guardar, entregar ao contêiner
  e um dia rotacionar. O Authorization Server recusado pela ADR-015 faria isso.

---

## Alternativas consideradas

- **Chave em variável de ambiente, como texto do PEM.** Evita o arquivo e combina
  com contêiner. Rejeitada por legibilidade: PEM tem quebras de linha, e o valor
  vira uma linha só com `\n` escapado que ninguém consegue revisar. Continua
  disponível se a implantação exigir.
- **`kid` fixo em configuração.** Mais simples de ler no JWKS. Rejeitada: descola
  do material que nomeia, e a rotação é exatamente o momento em que esse
  descolamento causa dano.
- **Token de oito horas, do tamanho do expediente.** Tornaria o painel confortável
  sem refresh. Rejeitada: oito horas de token roubado é longo demais, e o conforto
  é o problema que o refresh existe para resolver — não se compra conforto com
  janela de exposição.
- **`aud` por serviço.** É o desenho correto quando os serviços têm confianças
  diferentes. Rejeitada hoje: são nove processos de um sistema só, e o painel
  precisaria de nove tokens.
- **Deixar `iss` e `aud` sem validação e removê-los do token.** Coerente — seria
  aplicar a régua do `scope` até o fim. Rejeitada porque, ao contrário do `scope`,
  os dois são baratos de validar e ficam certos com um validador cada.

---

## Emenda que esta decisão provoca

**ADR-015 emendada, na tabela "O conjunto vigente"** — acrescentar após a tabela:

```markdown
> **Detalhado pela ADR-037 (07/09/2026).** `iss` e `aud` não são validados pela
> configuração padrão do Resource Server: `jwk-set-uri` sozinho valida apenas
> tempo. Cada serviço compõe validador explícito para os dois. O `aud` vale
> `delivery-platform`, um público para os nove processos.
```

Não é contradição: a ADR-015 disse para que os claims servem, e estava certa. O
que faltava era dizer que servir não é automático.
