# Fechar um marco

**As fontes:** o §10 do `docs/PRD.md` diz que histórias cada marco entrega, na coluna "Histórias";
a ordem de construção é a da ADR-083; os critérios de aceite estão nas histórias, no §6.

1. **Mede-se por critério**, porque é no critério que a promessa mora. Para cada um, três colunas:

   | Coluna | Vale quando |
   |---|---|
   | **rota** | um comando mostra a rota que serve o critério |
   | **tela** | um arquivo do front a serve |
   | **exercida** | há registro de uma pessoa no `docs/operacao/passagem-humana.md`, com data |

2. **Critério que depende de um marco posterior na ordem de construção** leva a anotação *"Entra a
   partir do marco N"*, como o PRD manda, e sai da conta deste marco.
3. **Critério verdadeiro por vazio não está cumprido.** Se ele vale só porque nada existe — nada
   aparece para o cliente porque não há porta para o cliente —, ele leva a anotação ou vira
   medição. Nunca conta como feito.
4. **O marco fecha quando todo critério sem anotação tem as três colunas.** Senão, a rodada diz o
   que falta, critério por critério. Sem "quase", sem "em grande parte".
5. **Quem declara o marco fechado é o Rafael**, com a tabela na frente.
