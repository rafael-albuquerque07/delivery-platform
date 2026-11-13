# ADR-082 — A segunda tela da CONFIGURAR_LOJA, e o zero que não é ausência

- **Status:** aceita
- **Data:** 13/11/2026
- **Rodada:** W-K
- **Relacionada:** ADR-074 (a tela do horário — o gatilho que esta dispara, emendada por esta),
  ADR-073, ADR-080 e ADR-081 (os quatro sub-recursos de `/settings`), ADR-055 (a tela mostra o
  `detail` como veio), ADR-061 (a seção mora na URL) e o `docs/front/premissas-do-front.md` §3
  (emendado por esta)

## Contexto

**A borda do `merchant` tinha quatro escritas sob `CONFIGURAR_LOJA`, e uma tela.** O horário
ganhou tela na W-H (ADR-074); o pedido mínimo, o desconto de retirada e as áreas de entrega
ganharam rota na E-9 e na E-10, e nenhuma tela as chamava.

**A ADR-074 escreveu o que acontece aqui.** O item do menu se chamava "Horário" porque *"o menu
nomeia a tela, não a permissão"*, e o gatilho dizia: *"A segunda tela sob o `CONFIGURAR_LOJA`
— o desconto, o pedido mínimo ou as áreas. Aí o item do menu deixa de se chamar "Horário" e
passa a nomear a permissão, com o horário dentro."*

**O horário já era componente.** O `HorarioDaSemana` recebe o `estabelecimentoId` e não sabe
de rota; a rota `/horario` só o montava.

**E duas regras do servidor dependem de como a tela escreve o zero.** O mínimo zero é *"sem
mínimo"* (M17); a área que não existe **não** é taxa zero (M11). A E-9 e a E-10 as prenderam
no servidor por mutação. O `premissas-do-front.md` §3 manda formatar com
`Intl.NumberFormat`, que escreve "R$ 0,00" — e não dizia nada sobre o zero.

## Decisão

### 1. Uma seção, quatro blocos

O item do menu passa a ser **"Configuração da loja"**, em `/painel/lojas/:id/configuracao`.
Dentro, quatro blocos: **o horário** (o `HorarioDaSemana`, sem mudança), **o pedido mínimo**,
**o desconto de retirada** e **as áreas de entrega**. **Cada bloco lê e salva o seu
sub-recurso** — um `PUT` por bloco, porque é assim que as rotas são (ADR-073 §1) —, e mostra o
que a resposta devolveu, como o horário já fazia (ADR-074 §1).

O endereço `/horario` da W-H **leva à configuração**: link antigo não cai na rota `*`.

### 2. Zero é um valor, e ausência não é zero — dito na tela

Cada zero tem a sua frase, e **nenhum se lê como preço**:

| Campo | Zero na tela | Por quê |
|---|---|---|
| pedido mínimo | *"sem mínimo"* | M17 — zero é ausência de mínimo |
| desconto de retirada | *"sem desconto"* | zero é valor, e entra no `PUT` |
| taxa de uma área | *"entrega grátis"* | taxa zero existe porque a área existe |

**E a ausência não aparece.** A lista das áreas mostra só as que o servidor devolveu; a loja sem
área diz *"Esta loja não entrega em nenhum bairro."* **Mostrar a ausência como taxa zero diria
"entrego de graça" onde o sistema diz "não entrego".** A regra é geral para o front, e por isso
mora no `premissas-do-front.md` §3, emendado nesta rodada (ADR-071): esta seção é a aplicação
dela.

### 3. A tela não valida o que o domínio decide

M15, M17, M9, M10 e M11 são do servidor. A tela lê o texto do campo como número e não faz conta
(`premissas-do-front.md` §3), e a recusa volta com o `detail` dele, mostrado como veio
(ADR-055). **A exceção da W-J (ADR-079 §3) não alcança esta tela:** as quatro escritas são
aceitas sempre que a pessoa tem a permissão, e o que o servidor recusa vem do que ela digita.

### 4. As modalidades são as que a leitura devolve

O mapa do pedido mínimo é de chave `string`; a tela mostra as chaves que o `GET` devolveu, e
nenhuma outra. A desconhecida aparece com o nome que veio, sem conceder nada
(`premissas-do-front.md` §4.1). **Os rótulos "Entrega" e "Retirada" ficam em duas telas** — o
cadastro da loja e esta.

### 5. Os dois testes que descreviam o estado transitório

Os testes 17 e 18 do `PainelPage.test.tsx` afirmavam o botão "Horário" e a URL `/horario` —
o estado que a ADR-074 escreveu como transitório. **Mudaram o nome do botão e a URL, por decisão
do dono do produto**, e continuam afirmando a mesma regra: a seção só aparece com
`CONFIGURAR_LOJA`, e lê o horário da loja escolhida.

### 6. O que esta ADR não faz

**Não cria rota, contrato, migração nem regra de domínio.** **Não faz a pausa manual nem a
ajuda de copiar o horário** (gatilhos da ADR-074). **Não diz que coluna nenhuma está
cumprida:** a passagem é de uma pessoa num navegador.

## Consequências

**As rotas do pedido mínimo, do desconto e das áreas têm chamador**, e o item do menu nomeia a
permissão. As linhas 103, 112 e 115 do razão disparam no mesmo gesto.

**A tela tem quatro botões de salvar.** Cada um grava só o seu bloco, e a mensagem de salvo diz
qual.

**Os rótulos das modalidades estão escritos duas vezes.**

## Alternativas consideradas

**Um salvar só, para os quatro.** Quatro `PUT` em sequência, e uma recusa no terceiro deixaria
dois gravados e dois não, com uma mensagem só. **Recusada:** a tela esconderia o que as rotas
são.

**Manter "Horário" e acrescentar um segundo item de menu.** Nenhum teste mudaria. **Recusada:**
contraria a ADR-074, e a tabela do `secoes.ts` passaria a dar dois itens a uma permissão.

**Mostrar o zero formatado, como o resto do dinheiro.** **Recusada pela §2.**

## Gatilho escrito

**A terceira tela que rotule uma modalidade.** Aí os rótulos "Entrega" e "Retirada" saem das
telas para um módulo só, e a lista das modalidades deixa de ter três cópias.

**A primeira pessoa que salvar um bloco achando que salvou a configuração inteira.** Aí a tela
ganha o aviso de bloco com alteração não salva, e a decisão do salvar único volta à mesa.
