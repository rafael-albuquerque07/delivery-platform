# V-1 — o validador de contrato ganha configuração, e passa

**O workflow `contracts` falhou em todas as execuções de 10/10 a 22/11/2026**, sempre com os mesmos
11 erros e 7 avisos do redocly, e ninguém leu. Ele roda as regras padrão, o `recommended`, porque a
raiz não tem configuração (*"No configurations were provided"*). O `contracts/README.md` promete os
contratos *"validados no CI"*, e o `CLAUDE.md` já tinha escrito a armadilha: *"uma ferramenta de
validação que nunca validou é pior do que nenhuma, porque parece que alguém já conferiu"*.

**O Rafael decidiu em 22/11:** uma rodada que mede cada regra e decide, uma por uma, entre
**consertar o contrato** e **configurar a regra com o motivo escrito**.

**Medido num clone de `759eafb`, com o redocly 2.55.0 do `package-lock.json` da raiz:**

| Regra | Onde | Quantas | A decisão |
|---|---|---|---|
| `no-empty-servers` | os três contratos | 3 erros | **configurar**: o endereço é do gateway e do ambiente (ADR-012), e o contrato descreve rotas, não um host |
| `operation-summary` | as quatro rotas do `identity` | 4 erros | **consertar**: `@Operation(summary = …)` nas quatro, como o `merchant` e o `catalog` já fazem |
| `security-defined` | as mesmas quatro | 4 erros | **consertar**: elas são públicas de propósito (o javadoc do `OpenApiConfig` do `identity` diz isso), e o contrato passa a dizer com `security: []` |
| `info-license` | os três contratos | 3 avisos | **consertar**: o repositório é MIT, e o contrato passa a dizer |
| `operation-4xx-response` | as quatro rotas do `identity` | 4 avisos | **configurar como aviso**: o `identity` é anterior à ADR-053 e não declara as recusas, e o JWKS não tem recusa nenhuma. Declarar exigiria o `ContratoDeErrosIT` dele, que é outra rodada. A dívida vai para *"Dívidas sem gatilho escrito"* do razão |

**Simulado no clone** — o `redocly.yaml` desta pasta, mais os contratos com resumo, `security: []`
e licença: **0 erros e 4 avisos**, *"Your API descriptions are valid"*, saída `0`.

⚠ **Rodada de código** — pequena: anotações e uma linha em três configurações. ⚠ **A
`metodo-delivery-platform` e a `novo-caso-de-uso` valem juntas.** O revisor roda antes do primeiro
comando.

⚠ **Esta rodada vem AGORA, antes da S-6.** A S-6 põe no passo 2 do `aplicar-rodada.md` que workflow
vermelho no `main` para a rodada — com o `contracts` vermelho, toda rodada seguinte pararia. **Ela
não mexe na contagem do razão**, então as linhas que a R-17 e a R-18 vão escrever continuam as
mesmas.

---

## §0 · Antes de tudo

```bash
git log --oneline -1; git status --short
date +%d/%m/%Y
ls -A v1/
echo "== controle (tem de achar, é da M3-2):"
grep -c "ADR-088" docs/architecture/decisions/README.md
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 8
ls redocly.yaml .redocly.yaml 2>/dev/null; echo "config=$?"
npm run contracts 2>&1 | tail -3
```

- O `main` em **`759eafb`**; o controle dá **`1` ou mais**.
- No `gh`: **só o `contracts` vermelho**. Outro vermelho: pare e diga qual.
- `config=2`: nenhuma configuração do redocly na raiz.
- O `npm run contracts` tem de falhar com **11 errors and 7 warnings**.

---

## §1 · As premissas

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | As quatro rotas do `identity` — `POST /api/v1/auth/verification-code`, `/signup`, `/login` e `GET /.well-known/jwks.json` — não têm `summary`, nem `security`, e só declaram a resposta de sucesso | medido no clone, lendo o `identity-service.json` |
| P2 | O `merchant` e o `catalog` declaram `bearerAuth` na raiz e `summary` em toda operação | medido no clone |
| P3 | Os três `OpenApiConfig` montam o `Info` sem `license`; o `LICENSE` da raiz é MIT | medido no clone |
| P4 | O springdoc 3.0.3 escreve `"security": []` numa operação anotada com `@SecurityRequirements` vazio | **não medido** — §3.2 mede, e para se não for |
| P5 | Cada `ContratoOpenApiIT` regrava o contrato com `-Dopenapi.atualizar=true` | a mensagem do próprio teste |
| P6 | O workflow `contracts` roda `npm run contracts` na raiz, e o redocly lê o `redocly.yaml` da raiz sozinho | `.github/workflows/contracts.yml`; simulado no clone |

---

## §2 · As medições

```bash
python -c "import json;d=json.load(open('contracts/openapi/identity-service.json',encoding='utf-8'));[print(m.upper(),p,'summary' in o,o.get('security'),list(o['responses'])) for p,ops in d['paths'].items() for m,o in ops.items()]"
grep -n "new Info()" -A6 backend/services/*/src/main/java/com/deliveryplatform/*/config/OpenApiConfig.java | grep -n -e "license" -e "Info()" 
grep -n "@PostMapping\|@GetMapping\|@Operation\|@SecurityRequirements" backend/services/identity-service/src/main/java/com/deliveryplatform/identity/api/controller/*.java
```

- As quatro rotas: `summary` **False**, `security` **None**, só o código de sucesso.
- Nenhum `license` nos três `OpenApiConfig`; nenhuma `@Operation` nem `@SecurityRequirements` no
  `identity`. *Se o `python` não estiver no caminho, use `node -e` com a mesma leitura.*

---

## §3 · O código

### §3.1 · O `identity`: resumo e segurança pública

Nos três controllers, `@SecurityRequirements` **vazio no nível da classe** — com
`import io.swagger.v3.oas.annotations.security.SecurityRequirements;` — e, logo acima, o comentário:

```java
// Pública de propósito: sem token (o javadoc do OpenApiConfig). O `security: []`
// vazio diz isso no contrato, e o redocly do workflow `contracts` confere (V-1).
@SecurityRequirements
```

E `@Operation(summary = …)` em cada rota, com `import io.swagger.v3.oas.annotations.Operation;`:

| Rota | `summary` |
|---|---|
| `POST /api/v1/auth/verification-code` | `"Envia o código de verificação para o telefone do cadastro"` |
| `POST /api/v1/auth/signup` | `"Cria a conta, com o código de verificação"` |
| `POST /api/v1/auth/login` | `"Entra no painel com telefone e senha, e recebe o token"` |
| `GET /.well-known/jwks.json` | `"A chave pública com que os serviços validam o token"` |

### §3.2 · A licença nos três `OpenApiConfig`

No `Info` de cada um — `identity`, `merchant`, `catalog` —, com
`import io.swagger.v3.oas.models.info.License;`:

```java
.license(new License().name("MIT").url("https://opensource.org/licenses/MIT"))
```

### §3.3 · Os contratos, regravados — e a medição da P4

```bash
./gradlew.bat :services:identity-service:test --tests "*ContratoOpenApiIT" -Dopenapi.atualizar=true
./gradlew.bat :services:merchant-service:test --tests "*ContratoOpenApiIT" -Dopenapi.atualizar=true
./gradlew.bat :services:catalog-service:test --tests "*ContratoOpenApiIT" -Dopenapi.atualizar=true
git diff --stat contracts/
python -c "import json;d=json.load(open('contracts/openapi/identity-service.json',encoding='utf-8'));[print(m.upper(),p,o.get('summary'),o.get('security')) for p,ops in d['paths'].items() for m,o in ops.items()]"
```

- As quatro rotas têm de mostrar o `summary` da tabela e **`security` igual a `[]`**.
- ⚠ **Se o `security` sair `None`** — o springdoc ignorou o `@SecurityRequirements` vazio —,
  **pare e cole**. Não troque a anotação por outra por conta própria, e não desligue a regra: as
  duas são decisões, e quem as toma é o Rafael, com a medição na mão.
- O `git diff` dos contratos do `merchant` e do `catalog` tem de ser **só o `license`**.

### §3.4 · O `redocly.yaml` e o lint

```bash
cp v1/redocly.yaml .
npm run contracts 2>&1 | tail -4; echo "contracts=$?"
```

**A previsão: *"Your API descriptions are valid"*, *"You have 4 warnings"*, e `contracts=0`.** Os
quatro avisos são o `operation-4xx-response` das rotas do `identity`. **Qualquer erro: pare e cole.**

### §3.5 · O front e o back

```bash
cd frontend && npm run tipos && git add src/api/generated && npm run format && npm run lint && npm run tipos:conferir && npm run typecheck && npm test -- --maxWorkers=2 && npm run build && cd ..
./gradlew.bat :services:identity-service:test :services:merchant-service:test :services:catalog-service:test
```

- Os seis passos do `frontend.yml`, na ordem dele, todos verdes. O `identity.ts` deve ganhar os
  quatro resumos como comentário; se `merchant.ts` e `catalog.ts` não mudarem, está certo — a
  licença não aparece nos tipos.
- Os três serviços verdes, com **o mesmo número de testes de antes**: esta rodada não cria teste —
  quem prova é o próprio lint, no CI.

---

## §4 · Os documentos — pelo script

```bash
node v1/aplica-v1-docs.js "$(date +%d/%m/%Y)"
git diff --stat
```

Quatro trocas e uma emenda, cada trecho casando uma vez só, ou nada é escrito:

| Arquivo | O que muda |
|---|---|
| ADR-039 | status e emenda: o validador do CI tem configuração, e passa |
| `README.md` das decisões | a linha da 039 |
| `contracts/README.md` | o item do `openapi/` passa a dizer que o workflow `contracts` valida, com o `redocly.yaml` |
| `docs/dividas.md` | uma linha nova em **"Dívidas sem gatilho escrito"**: o `identity` não declara as recusas |

⚠ **A tabela de "Dívidas sem gatilho escrito" não é numerada e não entra nas contas.** O razão não
se move: **recorte, segunda passada e linhas numeradas iguais** — *medido no clone*. Se qualquer
número se mover, pare.

⚠ **A regra 15:**

```bash
grep -rn -i -e "validado no CI" -e "validados no CI" -e "redocly" docs contracts CLAUDE.md .claude --include=*.md | cut -c1-160
```

Só pode achar: a emenda da ADR-039, que cita a promessa antiga para dizer que ela vale agora; o
`contracts/README.md`; a linha nova do razão; e a armadilha do `CLAUDE.md`:439, que conta a história
de 29/09 e **fica**. Outra: troque, ou pare e diga.

---

## §5 · O relatório e os commits

**O relatório:** o §0, com o `npm run contracts` de antes; as saídas da §2; a medição da P4; o diff
dos contratos; o `npm run contracts` de depois; os seis passos do front; os três serviços, em
testes, antes e depois; a busca da regra 15; a conta do razão.

**Dois commits:**

```
fix(contracts): o validador do CI ganha configuração, e os contratos dizem o que faltava (ADR-039)
```

com as anotações do `identity`, os três `OpenApiConfig`, os três contratos, os tipos do front que
mudaram e o `redocly.yaml`; e

```
docs: a emenda da ADR-039, e a dívida das recusas do identity no razão
```

com o resto. E `git push`. **Depois do push, espere a pipeline:**

```bash
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 10
```

**O `contracts` tem de ficar verde pela primeira vez desde 10/10**, e `identity-service`,
`merchant-service`, `catalog-service` e `frontend`, verdes. Se não, cole o `gh run view <número>
--log-failed`. A pasta `v1/` vai para `_to_delete/v1-aplicada`.
