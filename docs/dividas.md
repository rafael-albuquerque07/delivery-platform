# O razão das dívidas

**Gerado em 10/10/2026, sobre o commit `6fb73e6`, na rodada R-1. Este arquivo é
derivado e envelhece:** toda ADR, documento ou javadoc que escrever um gatilho novo o
torna incompleto. Refaça-o com os comandos abaixo antes de confiar nele.

```bash
# os candidatos — toda menção a gatilho, em documento e em código
grep -rn -i "gatilho" docs/ contracts/ CLAUDE.md README.md frontend/README.md --include=*.md --exclude=dividas.md
grep -rn -i "gatilho" backend/ frontend/src/ --include=*.java --include=*.ts --include=*.tsx | grep -v "/build/\|/node_modules/\|\.tsbuild"

# o recorte estreito, que é o que a R-1 mandou contar
grep -rn "Gatilho escrito\|gatilho escrito" docs/ contracts/ CLAUDE.md backend/ frontend/ --include=*.md --include=*.java --include=*.ts --include=*.tsx --exclude=dividas.md | grep -v "/build/\|/node_modules/" | wc -l
```

**Os comandos produzem candidatos; a tabela é a leitura deles.** Uma menção a
"gatilho" pode ser a definição de um, a citação de um que já está na tabela, ou o
relato de um que disparou. A conta, em 10/10/2026:

| | linhas |
| --- | --- |
| o recorte estreito devolve | **48** |
| — cópias em `frontend/.tsbuild/`, saída de build que o filtro não exclui | −2 |
| — citação de um gatilho que já tem linha própria (ADR-045:17, ADR-054:15, ADR-055:12, ADR-059:21, `pedido.md`:397) | −5 |
| — o mesmo gatilho repetido em outro lugar, ou substituído por outro (ADR-045:123, que a ADR-054 substituiu; ADR-043:210, ADR-057:101, ADR-058:91, `como-subir-local.md`:480, `ProdutoResponse`:30, `MarcarDisponibilidadeService`:64, `ContratoDeErrosIT`:73 e :105, `ReativacaoNoExpedienteIT`:58) | −10 |
| **= linhas da tabela vindas do recorte estreito** | **31** |
| + entraram por outra redação — *"com gatilho:"* (linhas 1 e 2), *"Gatilho para trazê-lo de volta"* (4), *"Quando o segundo chegar"* (6), *"O gatilho para mudar"* (11), *"O gatilho para reabrir"* (19), *"O gatilho, escrito"* (22), *"Gatilho:"* (40 e 42), *"O gatilho para a função mudar de lugar"* (41), *"volta quando houver"* (43), *"Decidir antes do marco 5"* (44), *"Gatilho novo, e são dois"* (20 e 48) | **+14** |
| + segundos gatilhos dentro de uma seção "## Gatilho escrito", que o comando conta uma vez só, pela linha do título (ADR-057, ADR-058, ADR-059 — linhas 45 a 47) | **+3** |
| **= linhas da tabela** | **48** |

**A coluna "disparou?" tem quatro respostas.** *sim* — disparou e a dívida foi paga;
*não* — e a coluna seguinte diz o que se procurou; **disparou e não produziu nada** —
a condição chegou e ninguém decidiu o que ela mandava decidir; e *rearmado* —
disparou, foi examinado, e a dívida continua com outro gatilho. **A terceira é a que
este arquivo existe para achar.**

---

## Disparou e não produziu nada

| # | Onde | O gatilho, copiado | O que mostra que disparou | O que o fecha |
| --- | --- | --- | --- | --- |
| 1 | ADR-044:97 (e `gateway/config/JwtProperties.java`:12) | *"Fica registrado como duplicação conhecida, com gatilho: o terceiro módulo que precisar do mesmo decoder."* | o `catalog` montou o terceiro decoder com validador de `iss`/`aud` em 28/09 (`e1e78fa`); hoje são quatro: `grep -rln "JwtIssuerValidator\|audience" backend --include=SecurityConfig.java` devolve `gateway`, `catalog`, `identity`, `merchant`. Nenhuma ADR posterior volta ao assunto | uma decisão: módulo comum de segurança (emenda à ADR-001) ou a duplicação assumida sem gatilho |
| 2 | `gateway/src/test/.../IdentityDeMentira.java`:36 | *"Fica registrado na ADR-044 como duplicação conhecida, com gatilho: o terceiro módulo que precisar do mesmo dublê."* | há três cópias: `gateway`, `merchant` e `catalog` têm `support/IdentityDeMentira.java`. A ADR-044 **não** registra esta duplicação — `grep -n "dublê\|IdentityDeMentira" docs/architecture/decisions/ADR-044-*.md` volta vazio | a mesma decisão da linha 1, para teste |
| 3 | `frontend/src/painel/MenuDoPainel.tsx`:34 | *"**Gatilho escrito:** a segunda seção com tela. Aí a URL passa a precisar dizer onde a pessoa está — para recarregar, para voltar e para mandar link a alguém —, e isso é rota, não estado."* | a W-C pôs a segunda seção com tela ("Disponibilidade", `PainelPage.tsx`:103), e a W-D uma terceira tela (o produto aberto). O `App.tsx` continua com uma rota só para o painel | rotas com URL no painel — e a decisão de qual URL é estável, porque ela vira link |
| 4 | `frontend/README.md`:32 | *"**Gatilho para trazê-lo de volta:** a primeira escrita que precise invalidar leitura de **outra** tela."* | **leitura, não medida:** na W-D, marcar uma opção na tela do produto muda o `vendavel` que a lista mostra; a lista é relida por remontagem ao voltar (`ListaDeProdutos.tsx`, `aoVoltar`). Se isso é "invalidar leitura de outra tela", disparou e foi resolvido sem o TanStack Query nem decisão escrita | decidir se a remontagem é a resposta, e escrevê-lo |

## Rearmado

| # | Onde | O gatilho, copiado | O que mostra que disparou | O que o fecha |
| --- | --- | --- | --- | --- |
| 5 | ADR-048:262 → ADR-057:101 | *"o primeiro consumidor que **escreva em banco** — hoje previsto para o `order`, no marco 3."* Rearmado como: *"**O primeiro consumidor cujo efeito não seja uma comparação** — um que acrescente, some, registre ou publique para fora."* | o `OuvinteDeExpedienteAlterado` (G-C3b, `7b1c888`) escreve em banco e foi coberto pela exceção (b) da invariante 7. `grep -rn "@RabbitListener" backend --include=*.java` devolve dois ouvintes, os dois nas exceções | o `processed_messages`, quando o primeiro consumidor que some ou registre chegar — previsto para o `order` |
| 6 | ADR-037:210 → ADR-044:97 | *"**Não se decide agora porque só existe um serviço validando de verdade.** Quando o segundo chegar, a pergunta chega junto, com evidência."* | o segundo chegou (o `merchant`, C-A) e a pergunta foi respondida com adiamento e um gatilho novo, o da linha 1 — que disparou sem resposta | a linha 1 |

## Sim — disparou, e a dívida foi paga

| # | Onde | O gatilho, copiado | O que mostra | Pago por |
| --- | --- | --- | --- | --- |
| 7 | ADR-026:226 | *"o primeiro consumidor que escreva em banco. É ele que precisa desta política de verdade"* | `ConfiguracaoDoConsumoDeExpediente` com a política 1/4/16 s (G-C3b, `7b1c888`) | emenda de 10/10 à ADR-026 |
| 8 | ADR-048:117 | *"o primeiro consumidor que escreva em banco — ele vai precisar da política da ADR-026 de verdade"* | a mesma; a ADR-048 registra *"Disparou em 10/10/2026 (G-C3b)"* | o mesmo; o bloco `retry` do YAML do `catalog` saiu na I-B |
| 9 | ADR-043:193 e :210 | *"o primeiro serviço que precisar autorizar uma requisição própria contra um vínculo do `merchant`"* | o `catalog` (G-B3, `e1e78fa`) | ADR-045 |
| 10 | ADR-053:123 (e `ContratoDeErrosIT`:73) | *"a primeira tela que mostre `detail` a alguém — a W-C."* | a W-C | ADR-055 |
| 11 | ADR-040:212 (e `Permissao.java`:9) | *"O gatilho para mudar é o segundo serviço que precise nomear uma permissão"* | o `catalog` passou a nomear `VER_PRODUTO` (G-B3) | respondido com "não" — `PermissaoDoCatalogo`, recorte próprio |
| 12 | `frontend/src/api/useRecurso.ts`:47 (o da W-A) | o gatilho da W-A para o TanStack Query, *"para esta rodada decidir"* | a W-B | decidido: saiu (`main.tsx`:9). O de volta é a linha 4 |
| 13 | ADR-044:157 | *"sai da lista de exposição antes de qualquer ambiente exposto, junto com a decisão do limite de taxa."* | **pago antes de disparar**: o `/actuator/gateway` saiu na G-B5; `include: health,info` no `application.yml` do gateway | emenda de 30/09 à ADR-044 |

## Não

| # | Onde | O gatilho, copiado | O que se procurou | O que o fecha |
| --- | --- | --- | --- | --- |
| 14 | ADR-001:227 | *"o dia em que houver uma segunda regra de build."* | `grep -rn "tasks.register" backend/build-logic` — uma só, `verificarDependenciaEntreModulos` | suíte própria do `build-logic` |
| 15 | ADR-009:299 | *"quando alguém precisar ordenar o cardápio por preço no banco"* | nenhuma ordenação por `precoBase` no `catalog` | `changeUnit` para `Decimal128` |
| 16 | ADR-012:95 | *"**Gatilho escrito, para os dois:** o primeiro ambiente exposto à internet."* (limite de taxa e `correlationId`) | nenhum workflow implanta: `grep -lni "deploy\|environment:" .github/workflows/*` vazio | as duas peças, com ADR |
| 17 | ADR-044:139 | *"o primeiro ambiente exposto à internet."* (limite de taxa) | o mesmo da 16 | a mesma |
| 18 | ADR-047:123 | *"o primeiro ambiente alcançável de fora da máquina de desenvolvimento. Aí a CSP entra com ADR própria"* | o mesmo da 16 | CSP |
| 19 | ADR-044:226 | *"**O gatilho para reabrir:** no dia em que houver rota demais para conferir de cabeça."* (o `/actuator/gateway`) | é julgamento, não fato; a contagem de rotas do gateway não foi medida | — |
| 20 | ADR-054:48 (substitui o da ADR-045:123) | *"**a primeira chamada de serviço para serviço em que o serviço chamado precise saber quem chamou** para decidir o que responder."* | as duas chamadas que existem (`catalog` → `merchant`, `AutorizacaoComercialHttp` e `ExpedienteCorrenteHttp`) mandam o token do portador, e o `merchant` responde sobre ele | identidade de serviço |
| 21 | ADR-046:179 | *"o dia em que uma passada da varredura levar mais que o intervalo entre passadas."* | **não há medida** da duração de uma passada; o log só conta aberturas publicadas. Não disparou até onde se vê, e não se vê muito | uma consulta restrita, já desenhada |
| 22 | ADR-046:284 (e `pedido.md`:397) | *"**O gatilho, escrito:** quando o `order` ganhar código — marco 3 —"* (emitir fechamento, pausa e retomada) | `order-service` tem 1 arquivo Java, a `*Application` | decisão (a) ou (b) da ADR-046 |
| 23 | ADR-049:150 | *"se algum dia o horário deixar de ser semanal (feriado, exceção por data), esta busca muda junto."* | `Disponibilidade` continua `Map<DayOfWeek, List<Faixa>>` | a busca da próxima abertura |
| 24 | ADR-050:42 | *"quem abrir o `SecurityConfig` do `identity` para qualquer outra coisa acrescenta a propriedade na mesma passada."* | nenhum commit no `SecurityConfig` do `identity` desde a ADR-050 (30/09) | a documentação aberta no `identity` |
| 25 | ADR-050:128 | *"o dia em que a cadeia de filtros virar módulo comum"* | não virou | — |
| 26 | ADR-051:128 | *"O dia em que as imagens tiverem de ser construídas num lugar que **não tem o build do Gradle disponível**"* | nenhum workflow constrói imagem | a imagem construtora |
| 27 | ADR-052:101 | *"a segunda coleção com mais de um escritor concorrente."* | um `@Document` só, `ProdutoDocumento` | `@Version` na segunda |
| 28 | ADR-052:104 (e `ContratoDeErrosIT`:105, `ReativacaoNoExpedienteIT`:58) | *"o primeiro relato de 409 que ninguém consegue explicar."* | nada no repositório registra relato de uso | teste HTTP → 409 |
| 29 | ADR-053:151 | *"O primeiro serviço **sem** rota autenticada, ou com um esquema de erro diferente — o `conversation`"* | `conversation-service` tem 1 arquivo Java | o contrato de erro do webhook |
| 30 | ADR-055:56 | *"a primeira tela que precise de **outro** campo do `ProblemDetail` além do `detail`"* | nenhum `.type` lido em `frontend/src` | o esquema de erro no contrato |
| 31 | ADR-055:72 | *"a primeira que precisar — e aí o servidor ganha um discriminador legível por máquina, que é o campo `type`"* | o mesmo da 30 | o `type` nos tratadores |
| 32 | ADR-056:119 | *"A primeira rota cujo identificador na URL **não** pertença a uma loja"* | todas as rotas com id estão sob `/merchants/{estabelecimentoId}` | a regra para recurso global |
| 33 | ADR-058:114 (e :91) | *"**A primeira rodada que precise de um dos cinco esqueletos de pé.**"* | as rodadas I-B a W-D subiram só o `marco2` | `mem_limit` e `on-failure:3` nos cinco |
| 34 | ADR-059:138 (e `como-subir-local.md`:480) | *"a rodada que der ao `merchant` as rotas de escrita troca esta fixture por uma sequência de chamadas HTTP, e **a semeadura por `ApplicationRunner` sai do repositório nessa rodada**."* | o `merchant` não tem `@PostMapping`, `@PutMapping` nem `@PatchMapping` | as rotas de escrita, e os três semeadores saem |
| 35 | `catalogo.md`:192 (e `MarcarDisponibilidadeService`:64) | *"o `conversation-service` ganhar código, que é quem o consome."* (o `DisponibilidadeAlteradaV1`) | 1 arquivo Java no `conversation` | outbox no `catalog` e o evento |
| 36 | `catalogo.md`:243 (e `ProdutoResponse`:30) | *"a primeira tela que mostre "a partir de"."* | nenhum "a partir de" em `frontend/src` | `precoMinimoPossivel` na resposta |
| 37 | `catalogo.md`:44 (e `ModoDeControle`:32) | *"o valor nasce junto com a primeira baixa de estoque, no marco 10."* | o enum tem `SEM_CONTROLE` e `QUALITATIVO` | marco 10 |
| 38 | `ProdutoDocumento`:40 | *"quando alguém precisar consultar `marcadoEm` por intervalo"* | nenhuma consulta por `marcadoEm` no repositório Mongo | `Date` com truncamento explícito |
| 39 | `ProdutoRepositorioMongo`:129 | *"a primeira loja com mais de mil produtos, ou a primeira varredura que apareça em log de consulta lenta"* | a única loja tem um produto | o índice `002` |
| 40 | ADR-021:221 | *"**Gatilho:** o starter volta com o primeiro leitor do cardápio em cache, no marco 7"* | o build do `catalog` cita Redis só em comentário | marco 7 |
| 41 | `DiaOperacional.java`:34 | *"O gatilho para a função mudar de lugar continua sendo o segundo serviço que precise **calcular**"* | `DiaOperacional` não aparece fora do `merchant` | o `settlement` ou o `order` |
| 42 | `CLAUDE.md`:413 | *"**Gatilho:** o primeiro `bootRun` de `catalog` ou `conversation` que alguém precise fazer"* | nada registra um; as rodadas I-B a W-D usaram contêiner | medir o `bootRun` sem `?replicaSet` |
| 43 | ADR-041:300 | *"Observabilidade volta quando houver **um comerciante real usando o sistema**"* | não há | o stack de observabilidade |
| 44 | `usuario.md`:177 | *"**Decidir antes do marco 5**, quando houver dinheiro na conta."* (número de telefone reciclado) | é prazo, não condição; o marco 5 não começou | a política |
| 45 | ADR-057, seção "Gatilho escrito" | *"**E um segundo, menor:** a primeira mensagem cuja chave de domínio **não venha no corpo** e precise ser buscada."* | o `ExpedienteAlteradoV1` traz o `expedienteDeReferencia` no corpo; é o único consumido com a exceção (b) | o `processed_messages` para esse consumidor |
| 46 | ADR-058, seção "Gatilho escrito" | *"**E um segundo:** o primeiro serviço cuja ocupação passar de 80% dos 512 MiB."* | `docker stats` em 10/10/2026, com a pilha de pé: o maior é o `merchant`, 302,2 MiB — 59% | reescolher o teto |
| 47 | ADR-059, seção "Gatilho escrito" | *"**E um segundo, que mede o andaime:** a primeira vez que alguém quiser semear algo que o agregado não deixa construir."* | os três semeadores constroem pelo agregado; o `identity` usa `reconstituir` para fixar o id, e o agregado aceita | descobrir por que o domínio recusa |
| 48 | ADR-054:51 | *"**a primeira rota `/internal/` que não receba o token de um portador.**"* | as duas rotas `/internal/` do `merchant` caem no `anyRequest().authenticated()` do `SecurityConfig` dele | identidade de serviço |

**Fora da tabela, de propósito:** a ADR-037:203 adia o refresh token *"para ADR
própria"* e não escreve condição — o motivo dado, *"um marco que ainda não tem
painel para exercitar sessão longa"*, deixou de valer com a W-A, mas sem condição
escrita não há o que disparar. **É uma dívida sem gatilho**, e é a seção seguinte.

---

## Dívidas sem gatilho escrito

Achadas nas rodadas, sem condição que as reabra. **Sem gatilho, ninguém olha.**

| Dívida | De onde |
| --- | --- |
| O refresh token, com a baixa do TTL de 30 min | ADR-037:203 |
| O login com hash sem prefixo de cifrador responde **500**, não 401 (`IllegalArgumentException` do `DelegatingPasswordEncoder`) | medido na W-D |
| A rota que marca a opção devolve o resumo, sem os grupos | W-D; `CLAUDE.md`, armadilha "Vai mexer numa opção" |
| A falha de busca do JWKS responde 500 com o corpo do `/error`, e não `ProblemDetail` | I-B; ADR-055, emenda de 10/10 |
| Os seis blocos `listener.simple.retry` com `max-attempts`, que o Boot 4.1.1 não lê | ADR-048, nota de 10/10 |
| O par de chaves na raiz do repositório, que não é o de `secrets/` | I-B |
| `JWT_PUBLIC_KEY_PATH` no `.env.example`, que nada lê | I-B |
| A correção do Mongo no `conversation`, que nenhum teste exerce | `CLAUDE.md`, armadilha do MongoDB no Boot 4 |
