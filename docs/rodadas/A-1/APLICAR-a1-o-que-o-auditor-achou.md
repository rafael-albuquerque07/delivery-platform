# A-1 — o que o auditor achou nos marcos 1 e 2: três testes que faltavam, e as duas tabelas

**A S-10 criou o `auditor-de-marco`, e ele refez às cegas as tabelas dos marcos 1 e 2.** Eu conferi
cada achado no repositório antes de escrever este pacote. Os que ficam:

**Marco 2 — a tabela afirmava mais do que os testes:**

1. **3.1c, remoções:** a tabela cita dois testes, e nenhum fala de remoção. O `catalogo.md` define
   remoção como *"grupo com `acrescimo = 0` em todas as opções"* (:336), e nenhum teste de rota monta
   um. **Impede declarar.**
2. **3.2a, os quatro estados:** nenhum teste de rota marca um produto **de volta** a `DISPONIVEL` —
   a volta que o comerciante faz quando repõe —, e o `DisponibilidadeTest.qualitativo_aceita_os_quatro`
   afirma dois dos quatro estados, apesar do nome. E a tabela não cita o
   `sem_horario_indeterminado_passa`, que existe.
3. **3.2a, exercida:** a tabela cita os itens 13 e 15 da passagem, e o 13 é uma recusa. São o 14 e o 15.

**Marco 1:**

4. **1.2h, teste:** a metade *"sobre o valor dos itens — sem a taxa de entrega"* só vale porque não
   existe pedido. **O Rafael decidiu em 25/11:** a coluna passa a *"em parte"* e aponta o Verificável
   da H4.5 (`PRD.md`:204), no marco 3, que prova essa metade. O PRD não muda.
5. **1.2b, a evidência:** os dois testes citados afirmam só a recusa do mapa vazio; o *"sim"* vale,
   por outros três, que a tabela passa a citar.
6. **1.3c, a rota e o teste:** a rota das condições do pedido e o `CondicoesDoPedidoControllerIT`
   afirmam o critério de frente, e a tabela não os citava.

**O Rafael decidiu em 25/11: tabelas e testes juntos.** A tabela fica verdadeira porque o teste
passa a existir. **Três testes, nenhum código de produção:**

- `MontagemDeOpcoesIT` — **um caso novo**: o grupo de remoções pela rota;
- `MarcacaoDeDisponibilidadeIT` — **um caso novo**: a volta a `DISPONIVEL` pela rota;
- `DisponibilidadeTest` — **o caso que existe** passa a afirmar os quatro estados.

**O que fica de fora, de propósito**, porque a tabela afirma **menos** do que existe e isso não
impede nada: a tela da 2.2d, a 2.1c (as três telas sem teste que a tabela nomeia continuam sem
teste), a 1.1b e a 1.1a, a 3.1a e o produtor da abertura na 3.2b. Ficam no relatório.

⚠ **Rodada de código** — só testes no `catalog`, e texto. ⚠ **A `metodo-delivery-platform` e a
`novo-caso-de-uso` valem juntas.** O revisor de pacote roda antes do primeiro comando; o revisor de
diff, antes do commit de código.

⚠ **Esta rodada vem depois da S-10.** A P-7, a R-18, a R-19 e a R-20 não tocam nas tabelas nem no
`catalog`.

---

## §0 · Antes de tudo

```bash
git log --oneline -3; git status --short
date +%d/%m/%Y
ls -A a1/
ls a1/APLICAR-*.md
echo "== controle (tem de achar, é da S-10):"
ls .claude/agents/auditor-de-marco.md
gh run list --repo rafael-albuquerque07/delivery-platform --branch main --limit 200 --json workflowName,conclusion,createdAt -q 'group_by(.workflowName)[] | "\(.[0].workflowName) \(.[0].conclusion) \(.[0].createdAt[0:10])"'
node scripts/razao.mjs
```

- O `main` em **`e2ce3a5`**. O `ls a1/APLICAR-*.md` mostra **só**
  `a1/APLICAR-a1-o-que-o-auditor-achou.md`.
- O controle da S-10 acha o subagente. **Se não, pare.**
- No `gh`, os treze workflows, **nenhum vermelho**. A saída do `razao.mjs` é o **antes** da §6.

---

## §1 · As premissas

**Medidas num clone de `e2ce3a5`.** O script da §4 rodou nesse clone: 2 arquivos, 10 trocas, e o
razão não se moveu.

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | Remoção é grupo com `acrescimo = 0` em todas as opções, e o domínio aceita acréscimo zero e grupo com mínimo zero | `catalogo.md`:336; `Opcao` e `GrupoDeOpcoes`, no clone |
| P2 | O `MontagemDeOpcoesIT` não tem caso de remoção, e tem os auxiliares `rascunho()`, `grupo(…)`, `opcao(…)`, `criarGrupo`, `criarOpcao` e `gruposNoBanco` | lido no clone |
| P3 | O `MarcacaoDeDisponibilidadeIT` não marca `DISPONIVEL` em caso nenhum, e tem `margherita()`, `marcar(…)` e `corpo(…)` | lido no clone |
| P4 | O `qualitativo_aceita_os_quatro` afirma `ACABANDO` e `ESGOTADO_HOJE`, e o `Disponibilidade` tem as fábricas `disponivel` e `esgotadoIndeterminado` | `DisponibilidadeTest`:148–157; `Disponibilidade`:77 e :89 |
| P5 | O item 13 da passagem de 19/10 é a recusa com o horário vazio; o 14 é a marcação | `passagem-humana.md`, os dois itens |
| P6 | O `PRD.md`:204 é o Verificável da H4.5 que recusa o lançamento abaixo do mínimo, sem a taxa | lido no clone |
| P7 | O `CondicoesDoPedidoControllerIT` tem os casos 8 e 10, e o `CriarEstabelecimentoServiceTest`, o `caso1` e o `caso3` | os nomes, no clone |

---

## §2 · As medições

```bash
T=backend/services/catalog-service/src/test/java/com/deliveryplatform/catalog
sed -n 336,338p docs/dominio/catalogo.md
grep -c -i "remo" $T/integration/MontagemDeOpcoesIT.java
grep -c '"DISPONIVEL"' $T/integration/MarcacaoDeDisponibilidadeIT.java
sed -n 147,157p $T/unit/DisponibilidadeTest.java
grep -n "^13\. \|^14\. " docs/operacao/passagem-humana.md | cut -c1-90
sed -n 204p docs/PRD.md | cut -c1-120
ls $T/integration/*IT.java $T/unit/*Test.java | wc -l
```

- `0` remoções no `MontagemDeOpcoesIT`; `0` marcações de `"DISPONIVEL"` no
  `MarcacaoDeDisponibilidadeIT` — *o único `"DISPONIVEL"` que pode aparecer é o da asserção da opção,
  na :209, que é o estado do produto e não uma marcação; se o `grep` der `1`, é ela*.
- O caso dos quatro com só duas asserções; o 13 é a recusa e o 14 a marcação; o Verificável do mínimo.
- **Anote o número de testes do `catalog` antes** (o último relatório que o mediu disse **396**).

---

## §3 · O código — só testes

### §3.1 · `MontagemDeOpcoesIT` — as remoções

Um caso novo, logo depois do `grupo_e_opcao_em_rascunho`, com os auxiliares que a classe já tem:

```java
    /**
     * Remoção é grupo opcional com acréscimo zero em todas as opções (catalogo.md, a forma do
     * grupo): ela não muda o valor, existe porque a cozinha precisa da instrução e o pedido
     * congela o nome (ADR-018). O PRD a lista na H3.1, ao lado do tamanho e dos adicionais (A-1).
     */
    @Test
    @DisplayName("remoções: grupo opcional com opções de acréscimo zero, pela rota — e o banco guarda o zero")
    void remocoes_com_acrescimo_zero() {
        Produto pizza = rascunho();

        criarGrupo(loja, pizza.getId(), grupo("Remover", 0, 2, 0)).expectStatus().isCreated();
        UUID grupoId = gruposNoBanco(pizza).get(0).id();
        criarOpcao(loja, pizza.getId(), grupoId, opcao("Sem cebola", "0.00", 0)).expectStatus().isCreated();
        criarOpcao(loja, pizza.getId(), grupoId, opcao("Sem tomate", "0.00", 1)).expectStatus().isCreated();

        assertThat(gruposNoBanco(pizza)).singleElement().satisfies(g -> {
            assertThat(g.minEscolhas()).as("remoção é opcional").isZero();
            assertThat(g.maxEscolhas()).isEqualTo(2);
            assertThat(g.opcoes()).extracting(Opcao::nome).containsExactly("Sem cebola", "Sem tomate");
            assertThat(g.opcoes()).allSatisfy(o -> assertThat(o.acrescimo()).isEqualTo(Precos.reais("0.00")));
        });
    }
```

### §3.2 · `MarcacaoDeDisponibilidadeIT` — a volta a `DISPONIVEL`

Um caso novo, depois do `sem_horario_indeterminado_passa`:

```java
    /**
     * A volta que o comerciante faz quando repõe: o que acabou volta a ser oferecido pela mesma
     * rota. Nenhum caso marcava DISPONIVEL até a A-1 — o auditor de marco achou a falta.
     */
    @Test
    @DisplayName("DISPONIVEL depois de ESGOTADO_HOJE: 200, e o produto volta a ser vendável")
    void volta_a_disponivel() {
        Produto pizza = margherita();
        marcar(loja, pizza.getId(), "ESGOTADO_HOJE").expectStatus().isOk();

        Map<String, Object> resposta = corpo(marcar(loja, pizza.getId(), "DISPONIVEL"));

        assertThat(resposta.get("disponibilidade")).isEqualTo("DISPONIVEL");
        assertThat(resposta.get("vendavel")).isEqualTo(true);
        assertThat(produtos.buscarPorId(pizza.getId()).orElseThrow().getDisponibilidade().estado())
                .isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
    }
```

### §3.3 · `DisponibilidadeTest` — os quatro, de fato

No `qualitativo_aceita_os_quatro`, depois da asserção do `ESGOTADO_HOJE`, as duas que faltam:

```java
            p.marcar(Disponibilidade.esgotadoIndeterminado(SABADO_A_01H, SEXTA));
            assertThat(p.getDisponibilidade().estado())
                    .isEqualTo(EstadoDeDisponibilidade.ESGOTADO_INDETERMINADO);

            p.marcar(Disponibilidade.disponivel(SABADO_A_01H, SEXTA));
            assertThat(p.getDisponibilidade().estado()).isEqualTo(EstadoDeDisponibilidade.DISPONIVEL);
```

⚠ **Se o código não compilar como está** — um auxiliar com outra assinatura, um import que falta —,
ajuste pelo que a classe já usa e diga no relatório. **Se um dos três casos novos ficar vermelho sem
mutação**, pare e cole: é o código de produção que não faz o que o critério pede, e isso é decisão do
Rafael.

### §3.4 · Os testes e as mutações

```bash
cd backend
./gradlew.bat :services:catalog-service:test --tests "*MontagemDeOpcoesIT" --tests "*MarcacaoDeDisponibilidadeIT" --tests "*DisponibilidadeTest"
```

Os três verdes. Depois, **três mutações**, cada uma com o arquivo **copiado para fora da árvore antes
e restaurado da cópia depois, com `diff` vazio** (regra 16):

| Mutação | Onde | Previsão, das asserções |
|---|---|---|
| **M1** — o domínio recusa acréscimo zero | no construtor da `Opcao`, logo depois da conferência de `acrescimo == null`, uma recusa de valor zero, com a API do `Money` que a classe usa | no `MontagemDeOpcoesIT`: cai **o caso novo**, e **o "a opção criada pela rota é marcável"**, que cria a *Calabresa* com `"0.00"` (:123). **Leia os outros casos da classe antes de rodar** e complete a previsão: um que monte opção de acréscimo zero pelo domínio também cai |
| **M2** — voltar a `DISPONIVEL` não grava | no `MarcarDisponibilidadeService`, quando o estado novo é `DISPONIVEL`, devolver o produto sem gravar | no `MarcacaoDeDisponibilidadeIT`: cai **só o caso novo** — nenhum outro marca `DISPONIVEL` |
| **M3** — a fábrica do indeterminado erra o estado | `Disponibilidade.esgotadoIndeterminado` passa a criar `ESGOTADO_HOJE` | no `DisponibilidadeTest`: cai **o `qualitativo_aceita_os_quatro`** e **o da :111**, *"ESGOTADO_INDETERMINADO não volta sozinho, nem em expediente novo"*, que usa a mesma fábrica. **Leia a classe e complete a previsão antes de rodar** |

**A previsão de cada mutação vai escrita no relatório antes de ela rodar.** Saiu outro caso do que a
previsão diz: cole, restaure, e diga — não ajuste a previsão depois.

### §3.5 · O `catalog` inteiro

```bash
./gradlew.bat :services:catalog-service:test
cd ..
```

Verde, com **dois testes a mais** do que a §2 anotou — o terceiro é um caso que já existia.

---

## §4 · As tabelas — pelo script

```bash
node a1/aplica-a1-docs.js
git diff --stat docs/marcos
```

**Leia o script antes de rodar.** Dez trocas em dois arquivos, cada trecho antigo casando uma vez só,
ou nada é escrito:

| Arquivo | O que muda |
|---|---|
| `marco-1.md` | o cabeçalho cita a A-1; a linha Estado, *"17 com teste, 4 deles em parte"*; a **1.2b**, a evidência certa; a **1.2h**, *"em parte"* com o Verificável da H4.5; a **1.3c**, a rota das condições e o `CondicoesDoPedidoControllerIT` |
| `marco-2.md` | o cabeçalho cita a A-1; a linha Estado, *"5 com teste"*; a **3.1c**, o caso das remoções; a **3.2a**, os três casos de rota, o `DisponibilidadeTest`, e os itens **14** e 15 |

**Os nomes dos casos novos estão no script.** Se você ajustou algum `@DisplayName` na §3, ajuste o
script igual antes de rodar.

⚠ **A regra 15:** as tabelas são o único lugar que cita esses testes por critério. Confira que nenhuma
outra frase diz que a 3.1c ou a 3.2a estão cobertas em parte:

```bash
grep -rn "Marcar disponível com sucesso\|itens 13 e 15" docs --include=*.md --exclude-dir=rodadas | cut -c1-120
```

Tem de achar **nada** depois do script.

---

## §5 · O auditor de novo — a prova de que as tabelas ficaram certas

**Com o commit de código já feito e as tabelas já escritas pela §4, antes do commit de documentos.**
O auditor lê o disco, então ele confere as tabelas novas. Chame-o **nesta sessão**, se ela foi aberta
depois da S-10 — o subagente já carrega —; senão, numa segunda sessão, como na S-10:

> *"Use o auditor-de-marco para auditar o marco 2. Não mude nenhum arquivo. Mostre a resposta do
> auditor inteira, palavra por palavra, sem resumir."*

**A previsão:** nenhuma divergência com peso **impede declarar** na 3.1c nem na 3.2a. O marco 2
continua sem fechar — a 3.2b não tem tela nem foi exercida, e a 3.1c e a 3.1d não foram exercidas —,
mas por colunas vazias, e não por coluna que afirma o que não existe. *Outros achados que só afirmam
menos podem aparecer: vão ao relatório.*

- **Se o auditor acusar** a 3.1c ou a 3.2a com *impede declarar*, pare antes do commit de documentos
  e traga a resposta.

---

## §6 · O razão — **previsão de igualdade**

As tabelas dos marcos não escrevem gatilho nem usam as formas da segunda passada. *Medido no clone:
igual.* Na sua máquina, **113 · 28 · 137**, antes e depois. ⚠ **Se qualquer número se mover, pare.**

---

## §7 · O relatório e os commits

**O relatório:** o §0; as saídas da §2; os três testes verdes; cada mutação com a previsão escrita
antes e o que saiu; o `catalog` antes e depois, em testes; o `git diff --stat` das tabelas; a busca da
regra 15; a resposta inteira do auditor da §5; o razão.

**Dois commits.** O de código, depois do `revisor-de-diff`:

```
test(catalog): as remoções, a volta a disponível e os quatro estados, que as tabelas diziam existir
```

com os três arquivos de teste. E o de documentos, depois da §5:

```
docs: as tabelas dos marcos 1 e 2 conferidas pelo auditor de marco (A-1)
```

com as duas tabelas e a pasta `docs/rodadas/A-1/` (passo 12), com as duas conferências de caminho de
máquina e de token. **Três arquivos fora de `docs/rodadas/` no primeiro, dois no segundo.** E
`git push`. A pasta `a1/` vai para `_to_delete/a1-aplicada`.

**Depois do push, a pipeline:** o `catalog-service` tem de ficar verde.
