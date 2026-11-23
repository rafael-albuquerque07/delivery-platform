# R-17 — o que falta para operar como SaaS, guardado com gatilho

**A v2 era a v1 com a S-8 aplicada antes** (regras 17 e 18 do `escrever-rodada.md`): a busca de implantação, na §2 e na célula da linha 133, perde o `-i` e lista as grafias com `-e` — a célula não aceita `\|` (regra 14); medido no clone: vazio, igual. E a frase *"A conta, em …"* do cabeçalho do razão está agora na **linha 75**, depois da S-7. *Nenhuma decisão mudou.*

**A v3 é a v2 com três frases da proposta corrigidas**, que o revisor de pacote achou falsas contra o
repositório de 23/11. *Os três defeitos são meus: escrevi a proposta sem abrir os documentos que ela
resume.*

- **acesso e portabilidade:** a proposta dizia que eles *"não têm procedimento"*. O
  `exclusao-de-titular.md` já serve a todos os direitos — mesmo canal, mesmo registro, mesmo prazo — e
  os nomeia como *"a varredura deste procedimento no sentido inverso"*. O que falta são os passos
  dessa varredura. A propriedade 4, o §4, o terceiro gatilho e a linha 135 passam a dizer isso, e
  citam a linha 84 do razão, que já espera o primeiro titular para a saga da exclusão;
- **o cofre:** a proposta punha *"três segredos num cofre só"*, com o certificado A1 dentro e a
  credencial do PSP *"no marco 8"*. A R3 da proposta fiscal, que prevalece enquanto o projeto for de
  uma pessoa só, diz que a plataforma **nunca** guarda certificado de lojista; e o `pagamento.md` põe o
  Pix com webhook no **marco 4**, enquanto a R5 da proposta fiscal diz que a plataforma não inicia
  cobrança. O cofre começa pelo token do canal, e a ADR dele diz o que acontece com os outros dois;
- **a área de entrega:** a proposta dizia que ela *"se desativa"* e não se perde. O `PUT` substitui a
  lista inteira, e a área omitida some (ADR-081 §1). Ela entra na lista do que uma loja perde por
  engano.

**E o que o agente achou na v2, além das três:** a coluna do meio da §4 dizia segunda passada 29 (é
28 desde a M3-2); a busca da regra 15 não pulava `docs/rodadas/`; o commit tinha "duas linhas" sem
contar a pasta da rodada; o §0 não dizia o commit de partida nem a versão; o quarto gatilho, *"o
primeiro lojista pagante… antes da primeira cobrança"*, disparava depois do que mandava anteceder —
passa a ser *o primeiro lojista que aceitar pagar*; e a linha 134 procurava o cofre pelo nome do
arquivo — passa a procurar pelo título da ADR.

⚠ **A pasta `r17/` tem de ter este arquivo, `APLICAR-r17-v3-o-que-falta-para-operar-como-saas.md`, e
nenhum outro `APLICAR-`.** Se a v2 também estiver lá, pare. **Os números das cinco linhas do razão não
mudam**: a R-18 continua escrevendo a 138.

**O Rafael pediu em 18/11 para guardar no repositório a análise das seis propriedades de um SaaS**,
para implementar em breve. Eu a conferi contra o repositório em `b275220` e a corrigi em três
pontos:

- a propriedade 2 (quem escreve opera) e a 4 (direitos do titular) estão **desenhadas, sem uso**;
- a 3 (uma versão só) já tem o padrão certo;
- a 6 (segredo por loja) já está armada pela ADR-085 e pela proposta fiscal.

E pediu, depois, para guardar três das que vêm **além das seis**: o ciclo da assinatura, o acesso
de suporte, e voltar uma loja só ao estado de antes.

**Medindo antes de escrever, o acesso de suporte já estava armado** — ADR-085, linha 125 do razão
—, e a chave por loja que eu tinha posto como gatilho também — ADR-085, linha 126. *Eu as tinha dado
ao Rafael como "sem gatilho" sem procurar. As duas entram na proposta como ponteiro para o dono, e
não como gatilho novo: duas cópias da mesma regra divergem (regra 3 do `SKILL.md`).*

**O que esta rodada faz:** põe a análise em `docs/propostas/`, como a R-15 fez com a emissão
fiscal, com **cinco gatilhos** para o que não tinha dono:

1. onde o produto roda — e a release sem parar o horário de pico;
2. o cofre dos segredos de loja, a partir do token do canal;
3. os passos de acesso e portabilidade do titular;
4. o ciclo da assinatura;
5. voltar uma loja só ao estado de antes.

E as cinco linhas entram no razão.

**E uma seção de candidatos, a §5 da proposta**, a pedido do Rafael: criptografia em envelope para
o cofre, estados da assinatura com carência, acesso de suporte com prazo e registro, e o que já
existe para voltar uma loja só. **Opções para cada ADR pesar, nenhuma decidida** — cada uma com os
cuidados que a conferência contra o repositório achou. *A seção não escreve gatilho: o razão não
muda por causa dela.*

⚠ **Proposta, não ADR**: não entra no índice das decisões e não decide nada. ⚠ **Texto. Nenhum
código.** ⚠ **A `metodo-delivery-platform` vale** — o revisor roda antes do primeiro comando.

⚠ **Esta rodada vem DEPOIS da M3-2.** As duas mexem no `docs/dividas.md`, e as previsões do §4 partem
do que a M3-2 deixou. Uma rodada de cada vez.

---

## §0 · Antes de tudo

```bash
git log --oneline -3; git status --short
date +%d/%m/%Y
ls -A r17/ r17/proposta/
ls r17/APLICAR-*.md
echo "== controle da S-8 (tem de dar 1):"; grep -c "^17\. " .claude/skills/metodo-delivery-platform/escrever-rodada.md
echo "== controle (tem de achar, é da M3-2):"
grep -c "ADR-088" docs/architecture/decisions/README.md
ls docs/propostas/
```

- O `main` em **`4819195`** (a M3-3). O `ls r17/APLICAR-*.md` mostra **só**
  `r17/APLICAR-r17-v3-o-que-falta-para-operar-como-saas.md`: se a v2 também estiver lá, pare.
- O controle da S-8 dá **`1`**; o da M3-2, **`1` ou mais**. Se algum falhar, pare.
- `docs/propostas/` tem de ter **só** `emissao-fiscal-onde-mora-o-certificado.md`.
- O `git status` só pode mostrar `r17/`.

---

## §1 · As premissas

**Medidas num clone de `b275220`; a P3, a P7, a P11 e a P12, num de `4819195`, para a v3.** A §2
reconfere na sua máquina.

| # | O que este pacote supõe | Situação |
|---|---|---|
| P1 | Nenhum workflow implanta | linha 16 do razão; o mesmo comando no clone: vazio |
| P2 | Nenhuma ADR decide o cofre; a ADR-085 o arma pelo token do canal | nenhum título de ADR com *cofre*, no clone de `4819195`; ADR-085:58 |
| P3 | O `exclusao-de-titular.md` serve a todos os direitos do art. 18 — mesmo canal, registro e prazo —, é esqueleto, e nomeia acesso e portabilidade numa frase só, sem os passos; a linha 84 do razão espera o primeiro titular | `exclusao-de-titular.md`, linhas 3, 82 e 93 a 99; razão, linha 84 |
| P11 | A R3 da proposta fiscal tira o certificado da plataforma enquanto o 3-B valer; o `pagamento.md` põe o Pix com webhook no marco 4; a R5 diz que a plataforma não inicia cobrança | `emissao-fiscal-onde-mora-o-certificado.md`:164 e :166; `pagamento.md`:360–364 |
| P12 | O `PUT` das áreas de entrega substitui a lista inteira | ADR-081:37–41 |
| P4 | A ADR-013 fixa o backup de 30 dias e a reaplicação das exclusões depois de restaurar | ADR-013:89 e :174 |
| P5 | A pergunta do prazo de cada direito é a 3 da revisão jurídica | `revisao-juridica.md`:169 |
| P6 | O modelo de negócio é assinatura mensal por estabelecimento, cobrada fora do produto | `PRD.md`:89, :341 e :370 |
| P7 | A fundadora nasce `ADMINISTRADOR` com todas as permissões | `estabelecimento.md`:487 |
| P8 | As chaves de ambiente `DELIVERY_SEMEADURA` e `DELIVERY_DOCS_ABERTAS` estão no `.env.example` | linhas 38 e 43 no clone |
| P9 | A ADR-085 arma a observabilidade, o acesso de suporte, a funcionalidade por loja e a versão nova da API — linhas 124 a 127 do razão —, o limite por estabelecimento (121) e o cofre (118) | lido no clone |
| P10 | Nenhum documento fala de inadimplência, e a restauração só existe para o banco inteiro | `grep -rln -i "inadimpl" docs/architecture docs/dominio docs/PRD.md` vazio; `grep -rn -i "restaura"` só acha o banco inteiro e dois sentidos sem relação |

---

## §2 · As medições

```bash
grep -ln -e "deploy" -e "Deploy" -e "environment:" .github/workflows/*; echo "deploy=$?"
echo "== controle (tem de achar):"; grep -ln -e "runs-on" .github/workflows/* | head -2
grep -l "^# .*[Cc]ofre" docs/architecture/decisions/*.md; echo "cofre=$?"
echo "== controle (tem de achar):"; grep -l "^# .*[Rr]etenção" docs/architecture/decisions/*.md
sed -n 3p docs/operacao/exclusao-de-titular.md; sed -n 82p docs/operacao/exclusao-de-titular.md
grep -c "no sentido inverso" docs/operacao/exclusao-de-titular.md
grep -c -i "^#.*acesso" docs/operacao/exclusao-de-titular.md; echo "== controle (tem de achar):"; grep -c -i "^#.*exclus" docs/operacao/exclusao-de-titular.md
grep -n "^| 84 " docs/dividas.md | cut -c1-120
grep -n "^| R3 \|^| R5 " docs/propostas/emissao-fiscal-onde-mora-o-certificado.md | cut -c1-120
sed -n 360,364p docs/dominio/pagamento.md
sed -n 37,41p docs/architecture/decisions/ADR-081-*.md
sed -n 89p docs/architecture/decisions/ADR-013-*.md; sed -n 174,177p docs/architecture/decisions/ADR-013-*.md
sed -n 169,170p docs/operacao/revisao-juridica.md
sed -n 89p docs/PRD.md; sed -n 370p docs/PRD.md
sed -n 487,488p docs/dominio/estabelecimento.md
grep -n -e "DELIVERY_SEMEADURA=" -e "DELIVERY_DOCS_ABERTAS=" .env.example
sed -n 58,60p docs/architecture/decisions/ADR-085-*.md
grep -n -e "^| 118 " -e "^| 121 " -e "^| 12[4-7] " docs/dividas.md | cut -c1-130
grep -rln -i "inadimpl" docs/architecture docs/dominio docs/PRD.md; echo "inadimplencia=$?"
echo "== controle (tem de achar):"; grep -c "Cobrança da assinatura" docs/PRD.md
grep -rn -i "restaura" docs/architecture/decisions docs/operacao | cut -c1-120
```

- `deploy=1`; o controle acha workflows. `cofre=1`: nenhuma ADR tem *cofre* no título; o controle acha
  a ADR-013. *A busca é pelo título, e não pelo nome do arquivo nem pelo corpo: a ADR-085 cita o cofre
  no corpo, e uma ADR que o decidisse o teria no título.*
- O `exclusao-de-titular.md`: o *"status: esqueleto"*, o prazo de 15 dias para todos os direitos, e
  **`1`** para *"no sentido inverso"*; **`0`** títulos de acesso, e o controle **`1`**. Se o arquivo
  tiver ganhado uma seção de acesso, pare. A linha 84 do razão: a saga da exclusão.
- A R3 e a R5 da proposta fiscal, o marco 4 do `pagamento.md` e o *"substitui"* da ADR-081.
- As linhas 118, 121 e 124 a 127 do razão têm de ser os gatilhos da ADR-085 que a proposta cita —
  o cofre, o limite por estabelecimento, a observabilidade, o suporte, a funcionalidade por loja e a
  `/api/v2/`.
- `inadimplencia=1`; o controle dá `1`. *O comando olha onde se decide — ADRs, domínio, PRD —, e
  não a proposta nem o razão, que passam a ter a palavra depois desta rodada.* O `restaura` acha só a restauração do banco inteiro
  (ADR-013, `exclusao-de-titular.md`, `revisao-juridica.md`) e dois sentidos sem relação (ADR-050 e
  ADR-069).
- ⚠ **Cada linha citada tem de dizer o que a proposta diz que ela diz.** Se alguma tiver mudado de
  lugar, troque o número na proposta e diga no relatório. Se tiver mudado de **sentido**, pare.

---

## §3 · O que se escreve

### §3.1 · A proposta

```bash
cp r17/proposta/o-que-falta-para-operar-como-saas.md docs/propostas/
sed -i "s|<<DATA>>|$(date +%d/%m/%Y)|" docs/propostas/o-que-falta-para-operar-como-saas.md
grep -c "<<" docs/propostas/o-que-falta-para-operar-como-saas.md
```

O último comando tem de dar `0`. **Não mude o texto**, salvo um número de linha que a §2 achar em
outro lugar. Se discordar de alguma frase, diga no relatório: o conserto é outra rodada.

### §3.2 · O razão — cinco linhas, 133 a 137

**Como a R-15 fez com a proposta fiscal** (as linhas 129 e 130): o mesmo formato de cinco colunas, a
mesma forma de citar a seção, e a mesma conciliação no cabeçalho. Cada linha cita o gatilho
**palavra por palavra** da seção de gatilhos da proposta, entre aspas e em itálico, com o negrito.

| Linha | O que se procurou, e o comando de reverificar | O que dispara decide |
|---|---|---|
| 133 | nenhum workflow implanta. **Reverifica com:** `grep -ln -e "deploy" -e "Deploy" -e "environment:" .github/workflows/*` — medido na R-17: vazio; com `-e "runs-on"`, acha | onde o produto roda, numa ADR |
| 134 | nenhuma ADR decide o cofre. **Reverifica com:** `grep -l "^# .*[Cc]ofre" docs/architecture/decisions/*.md` — medido na R-17: vazio; com `"^# .*[Rr]etenção"`, acha a ADR-013 | o cofre, a partir do token do canal, e o que acontece com o certificado e a credencial do PSP |
| 135 | o procedimento serve a todos os direitos, e acesso e portabilidade cabem numa frase, sem passos; nada registra um lojista pagante. A saga da exclusão espera o mesmo titular na linha 84. **Reverifica com:** `grep -c -i "^#.*acesso" docs/operacao/exclusao-de-titular.md` — medido na R-17: `0`; com `"^#.*exclus"`, acha um | os passos de acesso e portabilidade, no `exclusao-de-titular.md` |
| 136 | nenhum documento decide a loja que não pagou nem a que cancelou. **Reverifica com:** `grep -rln -i "inadimpl" docs/architecture docs/dominio docs/PRD.md` — medido na R-17: vazio; `grep -c "Cobrança da assinatura" docs/PRD.md` acha um | o ciclo da assinatura, numa ADR |
| 137 | a restauração só existe para o banco inteiro. **Reverifica com:** `grep -rn -i "restaura" docs/architecture/decisions docs/operacao` — medido na R-17: ADR-013:175, `exclusao-de-titular.md`:57 e `revisao-juridica.md`:212, todos do banco inteiro, e ADR-050:96 e ADR-069:119, sem relação | voltar uma loja só, na ADR do ambiente |

⚠ **O cabeçalho do razão também acompanha:** a linha 3 ganha *"e na R-17, sobre `<commit>` mais a proposta do que falta para operar como SaaS"*, e a frase *"A conta, em …, na …:"* (a linha 75, depois da S-7) passa a dizer esta data e a R-17.

⚠ **Nenhum `|` dentro das células** (regra 14) — os comandos acima usam `-e`. ⚠ A 133 tem a mesma
condição de partida da linha 16, sem repeti-la: a 16 pede duas defesas do gateway, a 133 pede a
decisão do ambiente. **Diga isso numa frase na própria célula da 133**, depois do comando.

---

## §4 · O razão — **previsão**

| | o que a M3-3 deixou | depois | por quê |
|---|---|---|---|
| o recorte estreito devolve | 112 | **113** | a seção de gatilhos da proposta |
| linhas vindas do recorte | 57 | **58** | o primeiro gatilho, a linha 133 |
| segundos gatilhos dentro de uma seção | +43 | **+47** | os outros quatro, linhas 134 a 137 — *"e, na R-17, a proposta do SaaS — linhas 134 a 137"* na célula |
| linhas que contam | 129 | **134** | |
| linhas numeradas | 132 | **137** | e a frase *"A tabela tem 132 linhas numeradas: as 129 que contam…"* passa a 137 e 134 |
| a segunda passada | 28 | **28** | a proposta não usa nenhuma das formas |

⚠ **Se o `main` tiver números diferentes dos da coluna do meio**, a previsão é a mesma **em
diferença**: +1, +1, +4, +5, +5, 0. **Se qualquer diferença sair outra, pare.**

E a regra 15: `grep -rn "o-que-falta-para-operar" docs --exclude-dir=rodadas` tem de achar as cinco
linhas do razão, e nada mais — a proposta não cita o próprio nome, e os pacotes guardados ficam fora
(regra 17).

---

## §5 · O relatório e o commit

**O relatório:** o §0; as saídas da §2; as cinco linhas do razão como ficaram; a conciliação do
cabeçalho antes e depois; a conta do razão.

**Um commit**, com a proposta, o `docs/dividas.md` e a pasta `docs/rodadas/R-17/`:

```
docs: a proposta do que falta para operar como SaaS, com cinco gatilhos no razão
```

Confira com `git diff --cached --name-only` — **duas linhas fora de `docs/rodadas/R-17/`**: a
proposta e o `docs/dividas.md`. A pasta da rodada entra no mesmo commit (passo 12), com as duas
conferências de caminho de máquina e de token. E `git push`. A pasta `r17/` vai para
`_to_delete/r17-aplicada`.
