# Fechar um marco

**As fontes:** o §10 do `docs/PRD.md` diz que histórias cada marco entrega, na coluna "Histórias";
a ordem de construção é a da ADR-083; os critérios de aceite estão nas histórias, no §6.

**A tabela mora no repositório:** `docs/marcos/marco-N.md`, uma por marco (ADR-084, emenda de
17/11/2026). Toda rodada que toca um critério atualiza a linha dele.

1. **Mede-se por critério**, porque é no critério que a promessa mora. Para cada um, quatro colunas:

   | Coluna | Vale quando |
   |---|---|
   | **rota** | um comando mostra a rota que serve o critério |
   | **tela** | um arquivo do front a serve |
   | **teste** | um teste nomeado — classe e método, ou o nome que ele mostra — falha se o critério quebrar; quem preenche abriu o teste e leu a asserção |
   | **exercida** | há registro de uma pessoa no `docs/operacao/passagem-humana.md`, com data |

   *"Nenhum"* é uma resposta válida na coluna teste — e é um achado, não um detalhe.

2. **Critério que depende de um marco posterior na ordem de construção** leva a anotação *"Entra a
   partir do marco N"*, como o PRD manda, e sai da conta deste marco. **Critério que espera um
   gatilho já escrito no razão** leva a anotação que aponta para ele, e também sai da conta.
3. **Critério verdadeiro por vazio não está cumprido.** Se ele vale só porque nada existe — nada
   aparece para o cliente porque não há porta para o cliente —, ele leva a anotação ou vira
   medição. Nunca conta como feito.
4. **O marco fecha quando todo critério sem anotação tem as quatro colunas.** Senão, a rodada diz o
   que falta, critério por critério. Sem "quase", sem "em grande parte".
5. **O teste e a pessoa não se substituem.** O teste prova que o critério vale hoje e continua
   valendo; a pessoa prova que alguém o viu funcionar como o comerciante vai ver. Um sem o outro
   deixa a coluna vazia.
6. **Antes de o Rafael declarar, o subagente `auditor-de-marco` refaz a tabela às cegas** — do PRD
   e do repositório, sem ler `docs/rodadas/`, os pacotes nem a tabela — e só depois a compara com
   ela. Divergência em que a tabela afirma mais do que o repositório **impede declarar**, até a
   tabela ou o repositório mudar; a que afirma menos vai para o relatório. *Quem escreve as rodadas
   preenche a tabela; quem a audita não pode partir do que ele escreveu* (S-10).
7. **Quem declara o marco fechado é o Rafael**, com a tabela e a auditoria na frente.
