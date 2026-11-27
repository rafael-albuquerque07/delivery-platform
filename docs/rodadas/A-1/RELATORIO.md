# A-1 — o que o auditor achou nos marcos 1 e 2

- **Aplicada em:** 27/11/2026 · **sobre:** `e2ce3a5` · **versão do pacote:** v1
- **Commits:** `abef897`, os três testes; e o de documentos, com as duas tabelas e esta pasta
- **A resposta do auditor da §5, inteira:** [auditor-marco-2.md](auditor-marco-2.md)

## O que se mediu
- §0: só a v1 em `a1/`; o subagente da S-10 existe; 13 workflows verdes; razão 113 · 28 · 137
- §2: remoção em `catalogo.md`:336; `0` remoções no `MontagemDeOpcoesIT`; `1` `"DISPONIVEL"` no
  `MarcacaoDeDisponibilidadeIT`, a asserção da :209 que o pacote previa; o caso dos quatro com duas
  asserções; o item 13 é a recusa (:230) e o 14 a marcação (:233); `PRD.md`:204, o Verificável do
  mínimo sem a taxa
- `catalog` antes: **396**, todos verdes (a execução veio UP-TO-DATE; a contagem é a dos resultados
  gravados, e bate com a V-1)
- os três trechos entraram como o pacote os escreveu, sem ajuste; os três verdes
  (`MontagemDeOpcoesIT` 11, `MarcacaoDeDisponibilidadeIT` 14, `DisponibilidadeTest` 13)
- `catalog` depois: **398**, todos verdes — dois a mais
- tabelas: `ok: 2 arquivos, 10 trocas`; `marco-1.md` 5+/5−, `marco-2.md` 4+/5−
- regra 15: a busca não acha nada depois do script
- razão depois: 113 · 28 · 137, igual
- passo 12, nesta pasta: `maquina=1`, `token=1`

## As mutações — previsão escrita antes, cada uma só com a classe dela
| Mutação | Previsão | Saiu |
|---|---|---|
| M1 · a `Opcao` recusa acréscimo zero | no `MontagemDeOpcoesIT`, 4: o caso novo, o `opcao_criada_e_marcavel` e os dois de `ATIVO`, porque o `margheritaPublicada()` monta a "Pequena" com `0.00` | os 4 |
| M2 · voltar a `DISPONIVEL` não grava | no `MarcacaoDeDisponibilidadeIT`, só o caso novo | só o caso novo |
| M3 · a fábrica do indeterminado cria `ESGOTADO_HOJE` | no `DisponibilidadeTest`, o `qualitativo_aceita_os_quatro` e o `esgotado_indeterminado_nao_reativa` (:111) | os dois |

Cada uma com o arquivo copiado para fora da árvore e restaurado, com `diff` vazio. A previsão da M1
foi completada antes de rodar, como o pacote manda; rodada com as três classes, ela derrubaria
também quase todo o `MarcacaoDeDisponibilidadeIT`, cujo `margherita()` usa a mesma "Pequena".

## O auditor, na §5
Nenhuma divergência com peso **impede declarar**, na 3.1c, na 3.2a ou em qualquer outro critério.
Duas que afirmam menos, na 3.2b: o teste do produtor (`AberturaDeExpedienteIT`) não está citado, e as
telas que mostram o resultado da volta. E uma ressalva: chamar de *"com as quatro colunas"* uma
linha de exercida *"em parte"* é generoso. Ele não abriu nada de `docs/rodadas/`, `_to_delete/`,
`a1/` nem outro `docs/marcos/`.

## Só para relatório
- revisor de pacote: o javadoc das remoções atribui ao `catalogo.md` o *"opcional"*, que ele não diz;
  a célula da 1.2h fala do marco 3 e não registra que o *"em parte"* é decisão de 25/11; o cabeçalho
  diz *"conferido pelo auditor-de-marco na A-1"* antes de o auditor rodar; o `ACABANDO` não tem caso
  de rota que o marque com sucesso — só o front e o domínio
- revisor de diff: nada impede; os três trechos idênticos aos do pacote, nenhum código de produção
- o marco 1 não foi reauditado nesta rodada: a §5 pede o 2
- o que o pacote deixou de fora de propósito — a tela da 2.2d, a 2.1c, a 1.1b e a 1.1a, a 3.1a e o
  produtor da abertura na 3.2b — continua de fora
