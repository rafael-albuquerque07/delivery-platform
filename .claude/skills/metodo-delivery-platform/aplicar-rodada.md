# Aplicar uma rodada

O Rafael extrai uma pasta na raiz do repositório e pede para aplicar o `.md` dela. A ordem:

1. **Leia o pacote inteiro antes do primeiro comando.**
2. **Confira o estado.** `git status --short` vazio e `git log --oneline -1` igual ou adiante do
   que o §0 do pacote diz. Diferente: pare e diga o que achou.
3. **Confira o pacote contra [escrever-rodada.md](escrever-rodada.md).** Violação que muda o que se
   faz — controle que aponta para registro que não está no repositório, ramo que o documento não
   tem, ação que o pacote dá como "autorizada" — vai para o Rafael **antes** de aplicar. Violação
   que não muda nada vai para o relatório.
4. **Meça antes de mudar.** Todas as medições do pacote primeiro, com o comando e a saída inteira.
   Comando que pode voltar vazio leva o controle ao lado.
5. **Onde o pacote diz "pare" ou "o Rafael decide", pergunte** com o que mediu e as opções do
   pacote.
6. **Mude só o que o pacote pede.** Defeito achado no caminho vai para o relatório, não vira
   conserto.
7. **Texto literal se copia do arquivo atual.** Antes de trocar uma linha, cole a linha como está
   e confirme que ela ocorre uma vez só.
8. **O razão (`docs/dividas.md`) segue a ADR-072.** Nenhuma linha se apaga. Os números da igualdade
   antes e depois; se qualquer um se mover além do que o pacote previu, pare e diga qual linha.
9. **Testes:** os que o pacote nomear. A suíte do front, sob carga, roda com `--maxWorkers=2`.
   Falha intermitente se relata com as duas execuções — não se dá por passada.
10. **Rodada de medição** — a que o pacote disser que é sem commit — termina com **zero commits** e
    a árvore limpa.
11. **O commit leva a mensagem do pacote, palavra por palavra**, mais as linhas de atribuição. Depois
    `git push` e `git log --stat -1`.
12. **A pasta do pacote vai para `_to_delete/`**, que o git ignora. Não se apaga.

## O relatório

O que se mediu (comando e saída) · o que se mudou (arquivo e linha) · cada pergunta ao Rafael e a
resposta dele · o que ficou de fora, e por quê · o estado final (`git status --short`,
`git log --oneline -1`).

⚠ **Nunca no relatório:** linha do `.env` que o pacote não nomeou, token, senha, nem caminho com o
nome de usuário do Windows.
