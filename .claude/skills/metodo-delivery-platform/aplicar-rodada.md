# Aplicar uma rodada

O Rafael extrai uma pasta na raiz do repositório e pede para aplicar o `.md` dela. A ordem:

1. **Leia o pacote inteiro antes do primeiro comando.**
2. **Confira o estado.** `git status --short` vazio e `git log --oneline -1` igual ou adiante do
   que o §0 do pacote diz. Diferente: pare e diga o que achou. **E a pipeline**, a última execução de
   cada workflow no `main`:
   `gh run list --branch main --limit 200 --json workflowName,conclusion,createdAt -q 'group_by(.workflowName)[] | "\(.[0].workflowName) \(.[0].conclusion) \(.[0].createdAt[0:10])"'`
   — workflow vermelho no `main` que o pacote não previu: pare e diga qual, com o
   `gh run view <número> --log-failed`.
3. **Confira o pacote contra [escrever-rodada.md](escrever-rodada.md) — pelo subagente
   `revisor-de-pacote`**, antes do primeiro comando. Achado que muda o que se faz vai para o Rafael
   **antes** de aplicar; achado que não muda nada vai para o relatório. *Se o subagente não estiver
   disponível na sessão, confira você mesmo, item por item da lista dele, e diga que foi assim.*
4. **Meça antes de mudar.** Todas as medições do pacote primeiro, com o comando e a saída inteira.
   Comando que pode voltar vazio leva o controle ao lado.
5. **Onde o pacote diz "pare" ou "o Rafael decide", pergunte** com o que mediu e as opções do
   pacote.
6. **Mude só o que o pacote pede.** Defeito achado no caminho vai para o relatório, não vira
   conserto.
7. **Texto literal se copia do arquivo atual.** Antes de trocar uma linha, cole a linha como está
   e confirme que ela ocorre uma vez só.
8. **O razão (`docs/dividas.md`) segue a ADR-072.** Nenhuma linha se apaga. Os números da igualdade
   antes e depois — `node scripts/razao.mjs` dá os três de uma vez; se qualquer um se mover além do que o pacote previu, pare e diga qual linha.
9. **Testes:** os que o pacote nomear. A suíte do front, sob carga, roda com `--maxWorkers=2`.
   **Rodada que toca `frontend/` ou `contracts/openapi/` roda os seis passos do `frontend.yml`**,
   na ordem dele — `format`, `lint`, `tipos:conferir`, `typecheck`, `test`, `build` —, antes do commit,
   com `frontend/src/api/generated/` já no índice (`git add`), porque o `tipos:conferir` compara a
   árvore com o índice.
   Falha intermitente se relata com as duas execuções — não se dá por passada.
10. **Rodada de medição** — a que o pacote disser que é sem commit — termina com **zero commits** e
    a árvore limpa.
11. **O commit leva a mensagem do pacote, palavra por palavra**, mais as linhas de atribuição. Depois
    `git push` e `git log --stat -1`. **Numa rodada que muda código, antes do commit:**
    `git add -N` nos arquivos novos da rodada, para que o `git diff` mostre o conteúdo deles; depois grave
    `git status --short` e `git diff` (com `--cached`, se já houver algo no índice) num arquivo em
    `_to_delete/`, e passe esse arquivo e o pacote ao subagente **`revisor-de-diff`**. Achado que
    impede o commit vai para o Rafael; o resto, para o relatório. *Sem o subagente na sessão,
    confira você mesmo, item por item da lista dele, e diga que foi assim.*
12. **A pasta do pacote vai para `_to_delete/`**, que o git ignora. Não se apaga.

## O relatório

O que se mediu (comando e saída) · o que se mudou (arquivo e linha) · cada pergunta ao Rafael e a
resposta dele · o que ficou de fora, e por quê · o estado final (`git status --short`,
`git log --oneline -1`).

⚠ **Nunca no relatório:** linha do `.env` que o pacote não nomeou, token, senha, nem caminho com o
nome de usuário do Windows.
