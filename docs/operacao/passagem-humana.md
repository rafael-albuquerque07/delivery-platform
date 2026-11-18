# Procedimento — preparar a pilha para a passagem humana

**Status:** executado na E-2 (15/10/2026), passos 1 a 4, sobre `merchant-service` e `gateway`
· **Base:** `docs/como-subir-local.md`, ADR-051 (a imagem recebe o jar pronto), ADR-058
(a pilha sobe em grupos)

> **O que este documento existe para impedir.** Em 11/10 a passagem humana da W-F parou
> duas horas porque **a pilha não era o código**. Três mentiras educadas em sequência: o
> Gradle sem Java imprimiu erro e seguiu, o Docker reusou a camada e disse *"Built"*, o
> compose reusou o contêiner e disse *"Running"*. **Nenhuma delas é um erro**, e as três
> juntas servem o jar de ontem.

> **De onde vêm os comandos.** Dos nomes reais do `docker-compose.yml` e dos módulos do
> Gradle, e do `como-subir-local.md`. **Os comandos exatos digitados em 11/10 não foram
> registrados no repositório**, e este documento não os reproduz de memória: o que está
> aqui foi **executado na E-2**, e a saída de cada passo está colada no fim.

---

## Antes de tudo: os dois shells

| Shell | Onde | Por quê |
|---|---|---|
| **PowerShell** | `C:\dev\delivery-platform\backend` | **O Java vive no Windows.** O Ubuntu não tem JDK |
| **WSL** (Ubuntu) | `/mnt/c/dev/delivery-platform` | **O Docker vive no WSL.** Não há `docker.exe` no Windows |

**Trocar um pelo outro não dá erro alto** — é a primeira das três mentiras.

---

## Quais serviços cada rodada reconstrói

Os que a rodada **mudou**. Na dúvida, `git diff --stat <commit-anterior> -- backend/` diz.

| Módulo do Gradle | Serviço do compose |
|---|---|
| `:services:identity-service` | `identity-service` |
| `:services:merchant-service` | `merchant-service` |
| `:services:catalog-service` | `catalog-service` |
| `:infra:gateway` | `gateway` |

**A E-2 mexe no `merchant-service` e no `gateway`.**

---

## 1. Construir o jar — PowerShell, em `backend/`

```powershell
.\gradlew.bat :services:merchant-service:bootJar :infra:gateway:bootJar
```

**Confira que terminou em `BUILD SUCCESSFUL`.** Sem Java no caminho, o wrapper imprime o
erro e devolve o controle — e o passo seguinte usa o jar velho que estava lá.

## 2. Conferir a data do jar — WSL, na raiz

```bash
stat -c '%y  %n' backend/services/merchant-service/build/libs/app.jar \
                 backend/infra/gateway/build/libs/app.jar
```

**A data tem de ser de agora.** Se for de ontem, o passo 1 não rodou — volte a ele.

## 3. Construir a imagem e recriar o contêiner — WSL, na raiz

```bash
docker compose --profile marco2 up -d --build --force-recreate --wait --wait-timeout 300 \
  merchant-service gateway
```

**Sem o `--force-recreate`, imagem nova e contêiner velho convivem em silêncio** — o compose
diz *"Running"* sobre o contêiner que já estava de pé. Sem o `--build`, o compose nem olha
para o jar novo.

## 4. As duas provas — WSL

```bash
docker compose --profile marco2 ps --format '{{.Service}} {{.Status}}'
docker compose --profile marco2 logs --since 5m merchant-service | grep -E "Started|Successfully applied|APPLIED"
```

- **`ps` diz segundos**, ou poucos minutos — nunca *"Up 2 days"* para o serviço que a rodada
  mudou;
- **o log mostra o arranque novo** (`Started ...Application`) — e, quando a rodada trouxer
  migração, a migração aplicada: no Flyway, `Successfully applied`; no Mongock, o
  `changeUnit` **`APPLIED`**.

**Só as duas juntas provam.** `ps` sem log diz que o contêiner é novo, não que o jar é.

## 5. Só então o navegador

Com os semeadores **desligados** (`DELIVERY_SEMEADURA=false` no `.env`). Confira sem abrir o
arquivo: `grep -c '^DELIVERY_SEMEADURA=false$' .env` deve imprimir `1`. Os contêineres leem a
chave **na subida**: se ela mudou depois da última, recrie `identity-service`,
`merchant-service` e `catalog-service` com `--force-recreate`, como no passo 3.

**Desligar não apaga.** Com a chave ligada, os semeadores rodam em toda subida e gravam o que
falta, por id fixo. Com ela desligada, não gravam nada, e o que já gravaram fica. Medido em
18/10/2026 (M-1): com a chave em `false` no `.env` e nos três contêineres, o banco tinha a
loja da fixture, o usuário dela e o produto, além de 8 contas, 2 lojas e 2 produtos de
passagens anteriores.

**E isso não atrapalha a passagem.** Uma conta nova não tem vínculo com a loja semeada nem com
as outras, e nenhuma delas aparece no painel dela. O painel vazio passa a ser prova do
vínculo, não de banco vazio. **A colisão é o telefone:** `+5511999990001` é o do usuário da
fixture. Use telefones que nunca foram usados nesta máquina, e anote quais foram.

O roteiro da passagem é da rodada que a pede; este documento só garante que o que está no
navegador é o código do commit.

---

## O que a execução da E-2 mostrou

*Colado da execução de 15/10/2026, sobre `merchant-service` e `gateway`.*

**Antes:** `docker compose --profile marco2 ps` dizia *"Up 2 days"* para os sete — inclusive o
`merchant-service` e o `gateway`, que a E-2 tinha acabado de mudar. **É a pilha que não é o
código**, medida.

```
== 1 · PowerShell
BUILD SUCCESSFUL in 26s

== 2 · a data do jar (2026-10-15 10:54:23, relógio do WSL)
2026-10-15 10:53:48 backend/infra/gateway/build/libs/app.jar
2026-10-15 10:53:56 backend/services/merchant-service/build/libs/app.jar

== 3 · build e recriação
 Image delivery-platform-gateway Built
 Image delivery-platform-merchant-service Built
 Container delivery-platform-gateway-1 Recreated
 Container delivery-platform-merchant-service-1 Recreated
 Container delivery-platform-gateway-1 Healthy
 Container delivery-platform-merchant-service-1 Healthy

== 4 · as duas provas
gateway Up 32 seconds (healthy)
merchant-service Up 31 seconds (healthy)
catalog-service Up 2 days (healthy)          ← não mudou na E-2, e não foi recriado
Current version of schema "public": 8        ← a E-2 não traz migração
Started MerchantServiceApplication in 23.294 seconds
Started GatewayApplication in 15.319 seconds
```

**E a prova pela rota, que é a que vale** (ADR-058: *"a prova é rota protegida pelo gateway,
não `/actuator/health`"*), sem token, pela porta do gateway:

```
GET  /api/v1/convites/nao-existe          404   ← público na cadeia, e o merchant responde
POST /api/v1/convites/nao-existe/aceite   401   ← barrado no gateway, antes do serviço
```

*Os nomes deste documento envelhecem com o compose. Ao renomear um serviço, emende a tabela
"Quais serviços cada rodada reconstrói" na mesma rodada.*

---

## 6 · O que a passagem produz

**Uma passagem humana produz texto, não um "funcionou".** A cada passo, anote **o que
apareceu na tela**: a frase exata, o estado do item, o número, o endereço na barra.

**Por quê:** em 15/10 a coluna do marco 2 foi cumprida e a frase que a provava não foi
copiada por ninguém. O que ficou foi *"tudo certo"* — e *"tudo certo"* não distingue a tela
que mostrou a frase certa da tela que mostrou qualquer outra.

**A regra, e ela vale para teste também:** prova que passa por um olho e não vira texto está
perdida. A memória de quem viu não é fonte.

**A W-G mexe só no front**: o `frontend` não é serviço do compose — ele roda no Vite, no
Windows (`npm run dev`) —, e nenhum dos passos 1 a 4 se aplica a ele. Os jars do
`merchant-service` e do `gateway` são os da E-2, recriados em 15/10 por este roteiro.

---

## A passagem de 19/10/2026 — o que uma pessoa viu

*Registrado em 19/10/2026 (N-1).*

Andada na madrugada de 19/10/2026, nas duas metades, **com `DELIVERY_SEMEADURA=false`** e numa
loja criada naquela noite, **por uma pessoa, em duas contas** — a que convida e a que aceita. **Foi
a primeira vez que as duas passagens deste roteiro foram andadas no navegador.**

**O que foi exercido:** uma conta nova criada pelo telefone, com o código lido na tabela de
verificação; uma loja cadastrada sem semeador; um convite de equipe emitido, aceito e exercido
com uma permissão só; uma semana de horário escrita, recusada, corrigida e salva; um produto
publicado; um item marcado como esgotado dentro de uma faixa aberta; e a varredura da abertura,
esperada no relógio.

⚠ **Esta seção não afirma coluna de marco nenhuma.** Ela diz o que foi exercido e o que foi
visto. **Nada aqui foi consertado.**

### A · O que quem passou no navegador relatou ter visto

**Dezesseis.** Entre aspas está o que a pessoa escreveu ou o que a tela disse. Fora das aspas,
descrição. O que é leitura de quem escreveu o pacote da rodada está na parte C, e não aqui.

**A primeira metade — o time, em duas contas:**

1. Uma conta nova, sem loja, entrou e viu **"Você ainda não faz parte de nenhuma loja"**. **O
   isolamento entre contas existe, e foi visto.**
2. A mesma conta, convidada para uma loja alheia com **apenas "Ver o cardápio"** marcado, viu
   **um item** no cardápio daquela loja, enquanto o cardápio da loja dela mesma mostrava
   **quatro**. **O filtro de permissão chega à tela.**
3. **"quando eu entro na minha conta pelo login não aparece a opção de criar uma loja nova"** —
   a tela de cadastrar loja **não tem porta de entrada** depois que a conta já tem loja.
4. O link do convite, colado **sem sessão aberta**, não registrou o aceite. Depois de entrar na
   conta convidada e colar o link outra vez, **aceitou**: **"só aceita o convite depois de logado
   e isso NÃO é para ser esperado e sim aceitar o convite e em seguida ficar registrado ao fazer
   o login com a nova conta"**.
5. **"vai para o /painel e o convite se perde"** — e **o link é de uso único**, e **não há lista
   de convites pendentes** em lugar nenhum da loja que convidou.
6. Antes da conta limpa do item 1, duas lojas diferentes apareciam uma para a outra na mesma
   sessão: **"Farmácia consegue ver o que Big Lanches Delivery também consegue ver e vice versa.
   Não tem bloqueio? São contas diferentes."** **O item 1 é a passagem que respondeu a essa
   pergunta.**
7. **"Onde está a página de Admin que é o Usuário SUPER?"** — **não há.** Nenhuma tela, nenhuma
   rota e nenhuma decisão escrita criam conta acima das lojas.

**A segunda metade — a cadeia do marco 2:**

8. Uma faixa **das 18:00 às 02:00** foi aceita e **nomeada pela tela** como virando o dia.
9. Uma faixa **das 10:00 às 10:00** foi recusada.
10. Duas faixas que **se sobrepõem** no mesmo dia foram **aceitas**.
11. A recusa do horário: a tela recusou a semana **sem dizer qual dia**; o texto da recusa
    **fica no rodapé**, longe do dia errado; **com o dia corrigido a recusa continuou na tela**;
    e **a semana inteira ficou bloqueada** por um dia só.
12. O salvar com a semana vazia **pediu confirmação e salvou**.
13. Com o horário vazio, marcar um item como esgotado foi **recusado** — e a frase da recusa
    **carrega os nomes `ESGOTADO_HOJE` e `ESGOTADO_INDETERMINADO`** e oferece **"Recarregar a
    lista"**, que não trata a causa.
14. Marcado o item dentro de uma faixa aberta, a lista **não mudou sozinha**: **"ele continua
    mas é preciso atualizar a página e isso é erro de usabilidade"**.
15. **A marcação sobreviveu à abertura do próprio expediente.** Com uma faixa das **04:41 às
    04:43** na segunda 19/10, o item foi marcado **Acabou hoje**; passada a varredura e
    recarregada a lista, **"permanece em Acabou hoje após recarregar a lista"**, com o selo
    **"não vendável"**.

⚠ **O que o item 15 não prova.** A H3.2 promete que **o que acabou volte sozinho na abertura
seguinte**. A faixa de 04:41 a 04:43 e a abertura publicada às 07:41:33 UTC são **o mesmo
expediente** — logo, o que foi exercido é a abertura **não desfazendo** a marcação. **A devolução
na abertura seguinte não foi exercida nessa passagem**, e a emenda de 19/10/2026 à ADR-074 diz o
mesmo.

**E o cadastro da loja:**

16. O cadastro **recusa loja sem endereço**: **"O cadastro não aceita sem endereço. Ele reclamou
    e eu ajustei antes de avançar."** A frase da recusa, medida na tela, é **"A loja não foi
    cadastrada. Confira os campos e tente de novo."** — **ela não nomeia o campo.**

⚠ **Uma entrada falsa foi retirada desta lista antes de ela chegar aqui.** Dizia que o cadastro
tinha aceitado endereço, bairro e troco vazios, e **quem escreveu o pacote a tinha deduzido de
duas capturas** — uma do formulário vazio, outra da loja já criada. **Quem passou no navegador
corrigiu.** O item 16 é o que sobrou dela, e é o contrário. *Captura é um quadro, não um
desfecho.*

⚠ **E uma medição posterior, do mesmo dia.** Às 06h de 19/10/2026, duas capturas da mesma URL
do convite mostraram que **a tela anuncia o retorno** — *"Depois você volta para esta página"* —
e que **o endereço é o mesmo antes e depois do login**. **O retorno existe e funciona pelos
botões da própria tela.** O que o item 4 nomeia, e o que a W-I trata (ADR-076), é a **ordem**:
entrar primeiro, aceitar depois. **O caminho em que o convite se perdeu, do item 5, não foi
medido.**

### B · O que tem causa medida no código

1. **A recusa do horário não limpa quando o dia é corrigido** (item 11) porque o `mudar()` de
   `HorarioDaSemana.tsx` zera `salvo` e `confirmandoVazio` e **não zera `recusa`**.
2. **A recusa de marcar esgotado só aparece quando não existe faixa nenhuma** (item 13):
   `Estabelecimento.expedienteParaCarimbo(agora)` cai em
   `disponibilidade.proximaAberturaApos(...)` quando o instante está fora da faixa, e **só
   devolve vazio quando não há faixa cadastrada**. **Marcar funciona dentro e fora do
   expediente.**
3. **A permissão `CONFIGURAR_LOJA` não alcança nenhum administrador que já existia quando ela
   nasceu.** `Membro.fundador` concede `EnumSet.allOf(Permissao.class)` **no momento da criação**,
   as permissões ficam gravadas em `membro_permissao`, **não houve migração** quando a permissão
   nasceu, e **nenhuma rota concede permissão a membro que já existe**. Em 19/10/2026 o banco
   desta máquina tinha 7 membros com o conjunto de administrador, e só **3** com
   `CONFIGURAR_LOJA` — os criados depois da E-4. A dívida está na ADR-011, emenda de 19/10/2026.
4. **A sessão do front vive em `sessionStorage`** (`frontend/src/auth/armazenamento.ts`, a
   gravação e a leitura da chave `delivery.sessao`), **que é por aba**: o link do convite colado
   numa aba nova abre sem sessão, mesmo no navegador em que a conta está aberta (item 4). Quem
   recebe o convite em outro aparelho chega sem sessão de qualquer forma.
5. **Este roteiro não estava no índice da `docs/operacao/`** — a passagem foi andada a partir de
   um arquivo que o índice não listava.

### C · A leitura de quem escreveu o pacote — e ela não é observação

⚠ **Nada nesta parte foi visto por ninguém.** Ela está separada porque, duas vezes em duas
rodadas, leitura de quem escreve o pacote entrou na lista do que outro viu.

1. O item 2 é o caso que o roteiro previa: permissão estreita filtrando cardápio alheio.
   **Exercido por uma pessoa, e não por teste.**
2. O item 6 se explica pela mesma conta administrando as duas lojas — e **a prova é o item 1**,
   não o raciocínio. *Quem escreveu o pacote afirmou a explicação **antes** de a prova existir, e
   estava apostando.*
3. Os itens 11 e 16 são a **mesma família**: recusa que sabe o que está errado e **não diz qual**
   — qual dia, qual campo. E o `premissas-do-front.md` explica por que o formulário não marca
   nada em volta: **ele não antecipa recusa de domínio.** *A consequência é que o peso inteiro de
   dizer o que falta cai no `detail` do servidor.*
4. Os itens 3 e 5, juntos: **não existe caminho de produto** para a segunda loja de uma conta nem
   para um convite que se perdeu.
5. O item 14, somado à parte B.2, é o que obriga a emenda da ADR-074 nesta mesma rodada.
