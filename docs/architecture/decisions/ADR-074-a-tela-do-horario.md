# ADR-074 — A tela do horário, e a tela que não valida o que o domínio decide

- **Status:** aceita · **emendada em 19/10/2026**: marcar o que acabou não espera a varredura · **emendada em 13/11/2026**: o item do menu passa a ser a configuração (ADR-082)
- **Data:** 18/10/2026
- **Rodada:** W-H
- **Relacionada:** ADR-016 (front mínimo), ADR-047 (a sessão), ADR-055 e ADR-063 (o corpo de
  erro), ADR-061 (a loja escolhida mora na URL), ADR-070 (a tela do convite), ADR-073 (o horário
  ganha rota, e a emenda da irmã de leitura), a **H1.2** do `PRD.md`, e o
  `docs/front/premissas-do-front.md`

## Contexto

**A E-4 deu rota ao horário e nenhuma tela a chama.** É a quarta vez que esta figura aparece —
a E-1, a E-3c, a E-2 e a E-4, que a ADR-073 §5 contou —, e o vigésimo nono defeito a nomeou na
primeira.

**E a rota sem chamador, aqui, tem um custo medido:** a cadeia *definir horário → a varredura
publica a abertura → marcar o que acabou* **existe por rota e não existe no navegador**. A
coluna do marco 2 foi cumprida em 15/10 numa loja **semeada**; uma loja criada por rota só chega
lá se alguém chamar a rota do horário **fora do navegador**.

**O domínio decidiu três coisas que uma tela ingênua desfaria:**

- **o mapa vazio é o estado nomeado** *"nunca abre por horário"*, e é como a loja nasce;
- **faixas sobrepostas são aceitas** — *"redundância, não contradição"*;
- **fim antes do início é a faixa que cruza a meia-noite**, e é válida.

*Uma tela que valide "fim depois do início" apaga a terceira. Uma que exija ao menos uma faixa
apaga a primeira.*

## Decisão

> **A semana inteira numa tela, um `PUT` ao salvar, e a tela não valida nada que o domínio
> decide.**

### 1. A semana inteira, e um `PUT`

Os sete dias na tela, cada um com as suas faixas, e **um `PUT` quando a pessoa salva** — o
corpo é a semana completa, porque é o que a rota recebe (ADR-073 §1).

**Não há salvar por dia.** *Salvar a terça sozinha mandaria um `PUT` cujo corpo é a semana
inteira de todo jeito, e deixaria a pessoa achando que o resto não foi enviado.*

**E a tela mostra o que voltou, não o que foi digitado.** A rota responde com o horário **como
ficou gravado**, normalizado pelo domínio — dia sem faixa descartado, segundos truncados. *Se a
tela mostrar o digitado, ela mente sobre o que está guardado, e a primeira pessoa a recarregar a
página descobre.* **Depois de salvar, a tela não relê:** a resposta do `PUT` já é a semana
normalizada.

**E a leitura ao abrir é o `GET` irmão**, que a E-5 criou depois de a primeira versão desta
rodada parar por não haver nenhum. ⚠ **Loja que nunca abre por horário responde 200 com o mapa
vazio** — não 404 —, e é isso que deixa a tela distinguir *"não abre por horário"* de *"não
consegui ler"*.

### 2. O vazio é alcançável pela tela, com a frase antes

**Remover todas as faixas e salvar é como se diz *"esta loja não abre por horário"***. Não há
chave, não há botão próprio: **é o mesmo ato, com o corpo vazio.**

⚠ **E a tela diz o que isso significa antes de mandar**, porque é a única coisa nesta tela cuja
consequência não está na tela: **a loja fica fechada por horário, e marcar o que acabou passa a
recusar** (o contexto da ADR-073, e o `LojaSemExpediente` do `catalog`).

**Por que não uma chave *"não abre por horário"*:** seriam **duas representações do mesmo
estado** na mesma tela, e a pergunta *"o que acontece com as faixas digitadas quando a chave
liga?"* não tem resposta boa. *A ADR-071 é sobre isso, e vale para tela como vale para
documento.*

### 3. A tela não valida o que o domínio decide — e **nomeia** o que parece erro

**Fim antes do início é aceito, e a tela escreve que a faixa cruza a meia-noite.** *Aceitar sem
dizer nada faz a pessoa achar que digitou errado; recusar apaga um caso que o domínio escolheu
ter.*

**Sobreposição é aceita, sem aviso de erro.** É redundância.

**E início igual ao fim, que o domínio recusa, a tela manda** — e mostra a recusa que voltar.
⚠ *Validar no cliente duplicaria a invariante, e a cópia divergiria na primeira mudança do
domínio.* **E a regra geral já está escrita**: o `premissas-do-front.md` diz, desde a W-D, *"não
duplicar a regra — chamar quem a tem"*, e, desde a W-E, a estende à validação: *"O formulário
não antecipa recusa de domínio"*. **Esta seção é a aplicação dela ao horário, e não uma regra
nova** (ADR-071) — *e por isso o `premissas-do-front.md` não ganhou parágrafo nesta rodada.*

### 4. A seção nasce sob `CONFIGURAR_LOJA`

A E-4 criou a permissão e deixou a entrada do painel **sem seção**, de propósito — o javadoc do
`MenuDoPainel` diz por quê: *"um item de menu que não leva a lugar nenhum é pior do que a
ausência dele"*. **Esta rodada lhe dá a seção**, e com ela o item de menu.

**O item do menu se chama "Horário", e não "Configuração da loja".** A permissão é larga — cobre
o horário, as áreas de entrega, o desconto de retirada e o pedido mínimo —, e **o menu nomeia a
tela, não a permissão**. *Chamar o item de configuração prometeria as outras três, e a pessoa que
clicasse procuraria o desconto ali dentro.* **O dia em que a segunda tela desta permissão
existir, o item passa a ser a configuração e o horário vira uma coisa dentro dela** — está
escrito abaixo, no gatilho.

⚠ **A loja escolhida continua na URL** (ADR-061), e a seção vive dentro dela.

### 5. O que esta tela **não** faz

**Não copia o horário de um dia para os outros.** É conveniência real, e o corte é uma tela, um
ato. *Fica no gatilho.*

**Não faz a pausa manual**, que a H1.2 lista junto com o horário.

**Não faz as áreas de entrega, o desconto de retirada nem o pedido mínimo**, ainda que a
permissão os cubra.

**Não acrescenta a permissão à tela do convite.** A `PermissaoDoConvite` do front exclui o
`CONFIGURAR_LOJA` desde a E-4, e esta rodada deixa a exclusão onde está. *Dar a tela a quem já
está na loja e oferecer a permissão a quem ainda vai entrar são dois atos; o segundo é uma
decisão da tela do convite, e tem gatilho.*

**Não diz que a coluna está cumprida.** ⚠ *Nem do marco 1, nem do marco 2 — a passagem é de uma
pessoa num navegador, e esta rodada só torna o caminho possível.*

**E não valida dinheiro, nem recalcula regra nenhuma** — o front não faz isso (`premissas-do-front.md`).

## Consequências

**A rota do horário ganha chamador**, e a figura da rota sem tela, na sua quarta
aparição, ganha a tela.

**A cadeia inteira passa a ser percorrível num navegador**, sem semeador e sem `curl`: criar a
loja, dar horário, esperar a varredura, e marcar o que acabou. ⚠ *Percorrível não é percorrida.*

**E a pessoa passa a poder fechar a loja por engano**, salvando sem faixas. *É o preço de o
estado nomeado ser alcançável — e é por isso que a frase vem antes.*

## Alternativas consideradas

**Uma chave *"não abre por horário"*.** Mais explícita. **Recusada pela §2:** duas
representações do mesmo estado.

**Exigir ao menos uma faixa para salvar.** Mais simples. **Recusada:** deixaria o estado nomeado
inalcançável pela tela, e quem definisse horário por engano só desfaria pela rota.

**Validar fim depois do início no cliente.** **Recusada pela §3:** apagaria a faixa que cruza a
meia-noite, que o domínio escolheu aceitar.

**Validar início igual ao fim no cliente**, para poupar uma ida ao servidor. **Recusada pela
§3:** duplica a invariante por uma economia de uma requisição.

**Salvar dia por dia.** **Recusada pela §1:** o corpo é a semana de todo jeito.

**Chamar o item de menu de "Configuração da loja"**, que é o rótulo que a E-4 deixou no mapa.
**Recusada pela §4:** nomeia a permissão, e promete três telas que a rodada não faz.

**Copiar o horário de um dia para os outros.** **Recusada pelo corte**, e está no gatilho.

## Gatilho escrito

**A primeira pessoa que pedir para repetir o horário de um dia nos outros.** Aí nasce a ajuda de
copiar.

**A primeira pessoa que pedir para fechar a loja agora, sem mexer no horário.** Aí nasce a pausa
manual — é o mesmo gatilho que a ADR-073 escreveu, e esta tela é onde ele vai aparecer.

**A segunda tela sob o `CONFIGURAR_LOJA`** — o desconto, o pedido mínimo ou as áreas. Aí o item
do menu deixa de se chamar "Horário" e passa a nomear a permissão, com o horário dentro.

**A primeira pessoa que quiser convidar alguém já com acesso à configuração da loja.** Aí a
`PermissaoDoConvite` perde a exclusão do `CONFIGURAR_LOJA`, e a tela do convite passa a oferecer
uma permissão cujo recorte é maior do que a tela que esta decisão cria.

## Emenda de 19/10/2026 — marcar o que acabou não espera a varredura

A Consequências diz que a cadeia passa a ser percorrível como *"criar a loja, dar horário,
esperar a varredura, e marcar o que acabou"*. **A ordem está errada desde que foi escrita**, e o
corpo fica como está.

**Marcar o que acabou não depende da varredura.** `Estabelecimento.expedienteParaCarimbo` usa a
faixa corrente **ou a próxima abertura**, então a marcação funciona sempre que a loja tenha
alguma faixa cadastrada, dentro ou fora do expediente. **A recusa aparece apenas quando não
existe faixa nenhuma**, e foi vista assim na passagem de 19/10/2026, com a semana salva vazia.

**O que a varredura faz é publicar a abertura**, e é a abertura que devolve à venda o que foi
marcado num expediente anterior. Na mesma passagem, com uma faixa das 04:41 às 04:43, o item foi
marcado dentro dela, a varredura publicou a abertura daquele expediente — a marca d'água da loja
registra o expediente `2026-10-19` publicado às 07:41:33 UTC — e, recarregada a lista, o item
permaneceu marcado, com o selo de não vendável. **Isso exerceu a abertura do mesmo expediente
não desfazendo a marcação.** A devolução na abertura seguinte não foi exercida nessa passagem.

O registro da passagem está em `docs/operacao/passagem-humana.md`.

## Emenda de 13/11/2026 — a segunda tela chegou

**A W-K deu tela ao pedido mínimo, ao desconto de retirada e às áreas de entrega**, e o item do
menu passou a se chamar **"Configuração da loja"**, em `/painel/lojas/:id/configuracao`, com o
horário como o primeiro de quatro blocos — o que a §4 e o terceiro gatilho acima escreveram. O
corpo fica como está. O endereço `/horario` leva à configuração. A decisão está na ADR-082.
