# M3-3 — a fatia 3: a permissão de lançar pedido

**A v2 é a v1 com a S-8 aplicada antes** (regras 17 e 18 do `escrever-rodada.md`): as duas buscas da regra 15 (§2 e §4) passam a um padrão só, com `--exclude-dir=rodadas` — medido no clone: a mesma saída da v1. *Nenhuma decisão mudou.* ⚠ **A pasta `m33/` tem de ter este arquivo, `APLICAR-m3-3-v2-a-permissao-de-lancar.md`, e nenhum outro `APLICAR-`**: se a v1 também estiver lá, pare — a pasta é de uma extração anterior.

**A H4.5 pede *"uma permissão própria, que nenhuma das permissões de H2.1 cobre"*.** A fatia 3 da
ADR-086 a cria no `merchant` e deixou uma pergunta: *"a rodada da permissão decide como a nova
alcança"* — porque a `CONFIGURAR_LOJA`, em 17/10, **não alcançou quem já era membro** (ADR-011,
emenda de 19/10), e essa dívida segue sem pagamento.

**O Rafael decidiu em 22/11:**

1. **O nome é `LANCAR_PEDIDO`.**
2. **Ela alcança quem já existe, por migration** — e **a `CONFIGURAR_LOJA` que faltava vai junto**,
   pagando a dívida de 19/10.
3. **A quem:** a todo membro com **as dez permissões anteriores à E-4** — os fundadores, que
   receberam o conjunto inteiro na criação. **O promovido a administrador não entra:** papel não é
   lista de permissões (`estabelecimento.md` §2), e `Membro.promover` nunca deu permissão.
   *Eu tinha perguntado com a premissa errada — "o administrador tem todas" —, e o Rafael decidiu de
   novo com a certa.*
4. **O convite oferece a permissão**, com o rótulo *"Lançar pedido pelo painel"*: é para o dono
   dá-la ao atendente do balcão.

**O que esta rodada não faz:** não cria a rota de lançar — quem exige a permissão é o `order`, na
fatia 4. Por isso ela é **peça** no critério 4.5f, não o completa.

⚠ **Rodada de código.** ⚠ **A `metodo-delivery-platform` e a `novo-caso-de-uso` valem juntas** —
o revisor de pacote no passo 3, os seis passos do front no passo 9, o revisor de diff no passo 11, e
o pacote guardado em `docs/rodadas/M3-3/` no passo 12.

⚠ **Esta rodada vem DEPOIS da S-7** — para ser a primeira rodada de código guardada em
`docs/rodadas/` e revisada pelo `revisor-de-diff`. **A R-17, a P-7 e a R-18 podem vir depois dela**,
em qualquer ordem: os scripts delas não tocam o que esta rodada muda.

---

## §0 · Antes de tudo

```bash
git log --oneline -3; git status --short
date +%d/%m/%Y
ls -A m33/
ls m33/APLICAR-*.md
echo "== controle da S-8 (tem de dar 1):"; grep -c "^17\. " .claude/skills/metodo-delivery-platform/escrever-rodada.md
echo "== controles (têm de achar, são da S-7 e da S-6):"
ls docs/rodadas/README.md .claude/agents/revisor-de-diff.md
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 200 --json workflowName,conclusion,createdAt -q 'group_by(.workflowName)[] | "\(.[0].workflowName) \(.[0].conclusion) \(.[0].createdAt[0:10])"'
node scripts/razao.mjs
```

- Os dois controles têm de achar os arquivos. **Se não, a S-6 ou a S-7 não foi aplicada: pare.**
- No `gh`, **nenhum workflow vermelho**.
- **Anote o commit de partida** (`git log --oneline -1`): o script da §4 o pede.

---

## §1 · As premissas

**Medidas num clone de `7083fe1`.**

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | O `Permissao` tem onze valores, o último `CONFIGURAR_LOJA`; o javadoc da classe diz *"As permissões de H2.1, mais uma"* — velho desde a `CONFIGURAR_LOJA` | lido no clone |
| P2 | `Membro.fundador` dá `EnumSet.allOf(Permissao.class)`; `Membro.promover` só troca o papel | `Membro.java`:78–84 e :254–257 |
| P3 | As permissões ficam em `membro_permissao (membro_id, permissao VARCHAR(24))`, chave nos dois; as do convite em `convite_permissao`, igual | `V4__cria_membro.sql`:35, `V5__cria_convite.sql`:48 |
| P4 | A última migration do `merchant` é a `V8`; nenhuma menciona `CONFIGURAR_LOJA` | `ls` e `grep` no clone |
| P5 | A dívida de 19/10 não tem linha no razão, nem em "Dívidas sem gatilho escrito" | `grep -n "CONFIGURAR_LOJA" docs/dividas.md`: só as linhas 100, 101, 103, 112 e 115, que são de outra coisa |
| P6 | No front, `ROTULOS` do `ConvidarParaEquipe.tsx` é `Record<PermissaoDoConvite, string>`, e `PermissaoDoConvite` exclui só a `CONFIGURAR_LOJA`; `ITENS` do `secoes.ts` é `Record<Permissao, …>` — **os dois quebram o `typecheck` sem a entrada nova** | lido no clone |
| P7 | O `catalog` descarta permissão que não conhece (`PermissaoDoCatalogo.de`) | lido no clone |
| P8 | Quatro frases dizem que o **administrador** recebe o conjunto inteiro: o javadoc da `CONFIGURAR_LOJA` no `Permissao.java`, o `estabelecimento.md`:122–124, a ADR-086 (fatia 3) e o corpo da emenda de 17/10 da ADR-011 | `grep` no clone; as duas ADRs se respondem por emenda |

---

## §2 · As medições

```bash
M=backend/services/merchant-service/src/main
grep -n "^    [A-Z_]*,\?$" $M/java/com/deliveryplatform/merchant/domain/model/Permissao.java
grep -n "allOf\|void promover" -A2 $M/java/com/deliveryplatform/merchant/domain/model/Membro.java | head -12
ls $M/resources/db/migration/
grep -rn "CONFIGURAR_LOJA" $M/resources/db/migration/; echo "migration=$?"
grep -c '"CONFIGURAR_LOJA"' contracts/openapi/merchant-service.json
grep -n "Record<" frontend/src/painel/ConvidarParaEquipe.tsx frontend/src/painel/secoes.ts
grep -rn -i "ADMINISTRADOR\` a recebe\|ADMINISTRADOR} a recebe\|ADMINISTRADOR\` recebe" docs --exclude-dir=rodadas .claude CLAUDE.md backend/services/merchant-service/src/main | cut -c1-160
```

- Onze valores no enum; a `V8` é a última; `migration=1`; seis `CONFIGURAR_LOJA` no contrato.
- **Meça também o banco da sua máquina, se a pilha estiver de pé** — só contagem, sem dado de
  ninguém:

  ```bash
  wsl -d Ubuntu -- bash -lc "cd /mnt/c/dev/delivery-platform && docker compose --profile full ps --format '{{.Service}} {{.Status}}'"
  ```

  Se o Postgres do `merchant` estiver de pé, conte com `psql` dentro do contêiner: quantos membros
  têm as dez permissões antigas, e quantos desses têm `CONFIGURAR_LOJA`. *A emenda de 19/10 mediu
  7 e 3.* **Se a pilha não estiver de pé, não suba por causa disto:** diga "não medido".

---

## §3 · O código

### §3.1 · O enum

`LANCAR_PEDIDO` **depois** de `CONFIGURAR_LOJA`, com o javadoc:

```java
    /**
     * Lançar no painel o pedido que chegou por telefone ou no balcão (H4.5): <i>"uma permissão
     * própria, que nenhuma das permissões de H2.1 cobre"</i>. O fundador a recebe com as outras, e
     * o convite a oferece — é para o dono dá-la ao atendente. Quem a exige é a rota de lançar do
     * {@code order} (ADR-086, fatia 4).
     */
    LANCAR_PEDIDO
```

E, no mesmo arquivo, **as duas frases velhas**:

- o topo: *"As permissões de H2.1, mais uma"* → *"As permissões de H2.1, mais três"*;
- no javadoc da `CONFIGURAR_LOJA`: *"O {@code ADMINISTRADOR} a recebe por {@code EnumSet.allOf}"* →
  *"O fundador a recebe na criação, por {@code EnumSet.allOf}, e a migration {@code V9} a deu aos
  fundadores anteriores a ela"*.

**Copie cada frase antes de trocar**, e confira que ocorre uma vez só.

### §3.2 · A migration `V9`

`backend/services/merchant-service/src/main/resources/db/migration/V9__a_permissao_de_lancar_alcanca_os_fundadores.sql`,
em minúsculas como a `V8`:

```sql
-- A LANCAR_PEDIDO nasce (M3-3; ADR-011, emenda), e alcança quem já existe — o que a
-- CONFIGURAR_LOJA não fez em 17/10 (ADR-011, emenda de 19/10), e por isso ela vai junto.
--
-- Quem recebe: o membro com as dez permissões anteriores à E-4. É o fundador —
-- Membro.fundador dá o conjunto inteiro na criação. O promovido a administrador não
-- entra: papel não é lista de permissões, e a promoção nunca deu permissão.
--
-- "on conflict do nothing": quem já tem uma das duas — os fundadores criados depois da
-- E-4 já têm a CONFIGURAR_LOJA — fica como está.
insert into membro_permissao (membro_id, permissao)
select m.id, nova.permissao
from membro m
cross join (values ('LANCAR_PEDIDO'), ('CONFIGURAR_LOJA')) as nova (permissao)
where (select count(*)
       from membro_permissao p
       where p.membro_id = m.id
         and p.permissao in ('VER_PRODUTO', 'CRIAR_PRODUTO', 'ALTERAR_PRODUTO', 'DESATIVAR_PRODUTO',
                             'VER_PEDIDO', 'ALTERAR_STATUS', 'VER_VENDAS', 'VER_ENTREGA',
                             'GERENCIAR_EQUIPE', 'GERENCIAR_JORNADA')) = 10
on conflict (membro_id, permissao) do nothing;
```

⚠ *O `catalog` guarda a autorização em cache por 60 s, invalidado pelo `VinculoAlteradoV1`. A
migration não publica evento: quem perguntar ao `catalog` no primeiro minuto depois do deploy pode
ver o conjunto antigo. Nenhuma rota do `catalog` exige `LANCAR_PEDIDO` nem `CONFIGURAR_LOJA`, então
isso não muda resposta nenhuma — e o relatório diz isso.*

### §3.3 · O contrato e o front

```bash
cd backend && ./gradlew.bat :services:merchant-service:test --tests "*ContratoOpenApiIT" -Dopenapi.atualizar=true && cd ..
git diff --stat contracts/
```

O `merchant-service.json` ganha `LANCAR_PEDIDO` **nos mesmos seis lugares** da `CONFIGURAR_LOJA`, e
nada mais muda de forma.

No front:

- `ConvidarParaEquipe.tsx`, em `ROTULOS`: `LANCAR_PEDIDO: 'Lançar pedido pelo painel'`;
- `secoes.ts`, em `ITENS`: `LANCAR_PEDIDO: { rotulo: 'Lançar pedido', secao: null }` — **sem seção**,
  como a `GERENCIAR_JORNADA`: a tela de lançar é da fatia 6.

Depois, os seis passos do `frontend.yml` — o passo 9, com os tipos no índice antes do
`tipos:conferir`.

### §3.4 · Os testes — **com previsão**

**`PermissaoDeLancarIT`**, no `merchant` (Testcontainers):

| # | Caso | Afirma |
|---|---|---|
| 1 | cadastra uma loja, e o fundador pede o contexto de acesso | `permissoes` traz `LANCAR_PEDIDO` |
| 2 | o fundador convida com `LANCAR_PEDIDO`, e o convidado aceita | o contexto do convidado traz `LANCAR_PEDIDO`, e só as do convite |
| 3 | um membro **sem** `LANCAR_PEDIDO` tenta convidar com ela | a mesma recusa que o `ConviteIT` já afirma para a A2 — **copie o código e o corpo de lá**, não decida um novo |
| 4 | a `V9`, num esquema à parte: migra até a `V8`, grava um fundador antigo (as dez permissões, sem `CONFIGURAR_LOJA`), e migra o resto | ele passa a ter `LANCAR_PEDIDO` **e** `CONFIGURAR_LOJA` |
| 5 | a `V9`, no mesmo esquema: um colaborador com três permissões; um promovido, com papel `ADMINISTRADOR` e cinco permissões; e um fundador da E-4 em diante, que já tem a `CONFIGURAR_LOJA` | os dois primeiros **não ganham nada**; o terceiro ganha só a `LANCAR_PEDIDO`, e a `CONFIGURAR_LOJA` dele **não duplica** |

*Para os casos 4 e 5: o Flyway pela API, com `schemas("prova_v9")` e `target("8")`, contra o mesmo
contêiner, sem tocar no esquema que o Spring migrou. **Copie a forma de algum IT que já use o
Flyway pela API, se houver**; se não houver, a forma é esta, e o relatório diz que é a primeira.*

**No front, `ConvidarParaEquipe.test.tsx`, caso 3 novo:** quem convida tendo `LANCAR_PEDIDO` vê a
caixa *"Lançar pedido pelo painel"*; o caso 1, que lista as caixas, **não muda**, porque quem convida
ali não a tem.

**Os testes que já existem e citam o conjunto inteiro** (`MembroTest`, `MembroRepositorioJpaIT`,
`CriarEstabelecimentoServiceTest`, `ConsultarContextoDeAcessoServiceTest`) usam
`Permissao.values()` ou `EnumSet.allOf`, e passam a incluir a nova sem mudança. **Se algum listar as
onze à mão, ele falha — e isso é achado, não conserto de passagem: diga qual.**

**As duas mutações — regra 16**, cada uma com cópia, mutação, só o `PermissaoDeLancarIT`, restauração
da cópia e `diff` vazio. **Antes de escrever a previsão, abra as asserções que você escreveu.**

| | A mutação | A minha previsão |
|---|---|---|
| M1 | na `V9`, tire `('LANCAR_PEDIDO')` do `values` | cai o **4**, e o **5** no fundador da E-4 em diante |
| M2 | na `V9`, troque `= 10` por `>= 3` | cai o **5**, e só ele |

⚠ *A `V9` é arquivo novo: o `git checkout` não a restaura. **Restaure da cópia** (regra 16).*

---

## §4 · Os documentos — pelo script

```bash
node m33/aplica-m33-docs.js "$(date +%d/%m/%Y)" <o commit de partida, 7 caracteres>
git diff --stat
```

**Leia o script antes de rodar.** Onze trocas e duas emendas:

| Arquivo | O que muda |
|---|---|
| ADR-011 | status e emenda: a `LANCAR_PEDIDO`, e a dívida de 19/10 paga |
| ADR-086 | status e emenda: a fatia 3 decidida |
| `README.md` das decisões | as linhas da 011 e da 086 |
| `estabelecimento.md` §2 | *"mais três"*; `LANCAR_PEDIDO` na lista; a nota dela e a da `V9`; e *"O `ADMINISTRADOR` a recebe"* → *"O **fundador** a recebe"*, na nota da `CONFIGURAR_LOJA` |
| `docs/marcos/marco-3.md` | a fatia 3 como peça na **4.5f**, com os testes; a linha Estado e o parágrafo das fatias |

⚠ **A regra 15:**

```bash
grep -rn -i "ADMINISTRADOR\` a recebe\|ADMINISTRADOR} a recebe\|ADMINISTRADOR\` recebe\|mais uma (\|mais duas:" docs --exclude-dir=rodadas .claude CLAUDE.md backend/services/merchant-service/src/main | cut -c1-160
```

Só pode achar: o corpo da ADR-086 (linha 45) e o da emenda de 17/10 da ADR-011, que as emendas desta
rodada respondem. **Outra: troque, ou pare e diga.**

⚠ **Nenhum texto novo usa as formas da segunda passada nem cita na prosa o título da seção de
gatilhos** — e isso vale para o javadoc do `Permissao.java`, que a segunda passada lê.

---

## §5 · O razão — **previsão de igualdade**

Nenhum gatilho novo, nenhuma linha nova: a dívida de 19/10 não tinha linha (P5). **Recorte, segunda
passada e linhas numeradas iguais**, pelo `node scripts/razao.mjs` antes e depois. *Medido no clone,
com o script de documentos: igual.* ⚠ **Se qualquer número se mover, pare.**

---

## §6 · O relatório e os commits

**O relatório**, que vira o `docs/rodadas/M3-3/RELATORIO.md` (passo 12): o §0; as saídas da §2,
com a contagem do banco local ou "não medido"; o diff do contrato; a tabela da §3.4 com uma coluna
**resultado**; as duas mutações; o `merchant` e o front, em testes, antes e depois; a resposta do
`revisor-de-diff`; a busca da regra 15; a conta do razão.

**Dois commits:**

```
feat(merchant): a permissão de lançar pedido, e os fundadores que já existiam a recebem (ADR-011)
```

com o enum, a `V9`, os testes, o contrato e o front; e

```
docs: as emendas das ADRs 011 e 086, o estabelecimento.md e a fatia 3 na tabela do marco 3
```

com o resto **e `docs/rodadas/M3-3/`**. E `git push`. **Depois, a pipeline**, pelo comando do passo 2:
`merchant-service`, `frontend` e `contracts` verdes.
