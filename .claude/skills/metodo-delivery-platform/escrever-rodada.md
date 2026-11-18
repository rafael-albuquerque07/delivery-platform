# Escrever uma rodada

Cada regra aqui nasceu de um pacote que errou. **Quem escreve segue; quem aplica confere.**

## A forma

- **§0 · Antes de tudo:** o commit esperado do `main`, a data pelo `date`, e um controle que tem de
  achar algo da rodada anterior.
- **§1 · O que a rodada é**, e o que ela não toca.
- **A tabela de premissas.** Cada coisa que o pacote supõe sobre o repositório, sobre a máquina ou
  sobre as ferramentas, marcada **medida** (por qual rodada, em que data) ou **não medida**.
  Premissa não medida vira medição da própria rodada, antes de qualquer mudança.
- **§2 · As medições**, **§3 · O que se escreve**, **§4 · O razão** com a previsão dos números,
  **§5 · O relatório e o commit**, com a mensagem pronta.
- **A aposta**, se a rodada prevê um resultado: escrita antes, comparada no relatório.

## As regras

1. **Controle só sobre o que está no repositório.** *"Confira contra a medição X"* exige o caminho
   do arquivo onde X está escrito. **Medição que só existe num relatório colado na conversa não
   está no repositório.**
2. **O silêncio de um documento se mede.** Antes de escrever *"o documento não diz"*, um `grep`
   com a saída. *Um resumo da conversa não é a conversa.*
3. **Texto para trocar se copia do arquivo atual**, nunca da memória — inclusive o trecho de um
   `sed`, de um `Edit` ou de um `vi.mock`.
4. **Os ramos são os do documento.** Uma bifurcação usa as categorias que o documento usa, não as
   que quem escreve imaginou.
5. **Um corte leva a razão que o documento dá, ou nenhuma.** Não se inventa motivo para tirar algo.
6. **Regra escrita para um caso se testa contra todos.** Antes de entregar, uma varredura: a regra
   pega todos os casos que deveria, e só eles.
7. **Custo se mede antes de prometer.** Disco, tempo, tamanho: com comando, não *"deve dar menos
   de…"*.
8. **Guarda que exige permissão vem com o comando para o Rafael.** O pacote nunca escreve
   "autorizado" por ninguém.
9. **Toda referência aponta para dentro do repositório.** Um número de nota ou de defeito que só
   existe fora daqui não serve de razão.
10. **O que é do Rafael vai como pergunta**, com os fatos medidos e as opções. Quem escreve pode
    recomendar; a recomendação não vale como resposta.
11. **Segredo nunca entra no pacote.** O pacote manda imprimir do `.env` só a linha que precisa, por
    comando que imprima só ela — e de preferência só a contagem (`grep -c`).
12. **Rodada de código diz que critérios cobre, e que teste prova cada um.** E atualiza a linha
    de cada critério em `docs/marcos/marco-N.md`.
13. **O comando de um fato negativo procura a forma da peça, não a palavra.** *"Não há rota
    interna"* se prova com `Mapping("/internal`, não com `/internal/` — que acha javadoc e
    cliente. E vem com um controle que acha a mesma forma em outro lugar.
14. **Comando que vai para célula de tabela não tem `|`.** Nem como separador, nem dentro do
    padrão: onde o padrão começa com `|`, use `.`.
