# ADR-054 — O gatilho da identidade de serviço não disparou

- **Status:** aceita
- **Data:** 07/10/2026
- **Rodada:** G-D
- **Emenda a:** ADR-045 (a credencial entre serviços é o token de quem pediu)
- **Relacionada:** ADR-015, ADR-018, ADR-037, ADR-046, ADR-048

## Contexto

A ADR-045 deixou uma pendência com gatilho:

> *"Não serve para autorização fora de uma requisição de usuário. Reagir a um
> evento, rodar uma varredura, executar um procedimento operacional — nada disso
> tem token. (…) **Gatilho escrito:** o primeiro serviço que precisar autorizar sem
> uma pessoa do outro lado. Aí a identidade de serviço é o assunto da rodada, com
> um caso real em cima da mesa em vez de uma previsão."*

Em 01/10/2026 ficou decidido tomar essa decisão antes da cotação, porque parecia
que a cotação a disparava. **Parecia errado.** O reconhecimento desta rodada foi
procurar o caso real no repositório, e não achou nenhum:

- **a varredura do `merchant`** publica a abertura de expediente sem ninguém ter
  pedido, e portanto sem token — e a ADR-046 já escreve, em texto, que *"aqui não
  há problema, porque a varredura não autoriza nada: ela observa e publica um
  fato do próprio serviço"*;
- **o consumidor da G-B4** reage ao `VinculoAlteradoV1` e só mexe na memória do
  próprio processo. Não chama ninguém;
- **a reativação da G-C3a** recebe o expediente que abriu **dentro do evento**. O
  dado que ela precisaria pedir ao `merchant` já veio. Não chama ninguém;
- **todas as rotas `/internal/` de hoje exigem o token do portador.** Não existe
  rota que exponha dado de uma loja a um chamador sem vínculo com ela.

E a cotação, que motivou a antecipação, **não tem chamador**: o `order` é do marco
3 e o `conversation` não tem código.

## Decisão

**Esta ADR decide não decidir, e rearma o gatilho com palavras melhores.**

O gatilho da ADR-045 está escrito de um jeito que **dispara falso**, e disparou
nesta conversa. *"Precisar autorizar sem uma pessoa do outro lado"* descreve a
varredura do `merchant` tão bem quanto descreve o caso de verdade — e a varredura
não precisa de nada. O que faltava distinguir é **quem pergunta** de **quem é
perguntado**.

> **Gatilho novo, e são dois:**
>
> 1. **a primeira chamada de serviço para serviço em que o serviço chamado precise
>    saber quem chamou** para decidir o que responder. Não é "agir sem pessoa": é
>    *ser interrogado por um programa*;
> 2. **a primeira rota `/internal/` que não receba o token de um portador.** A
>    emenda de 28/09 da ADR-045 diz que `/internal/` responde **sobre o
>    portador**; uma rota ali sem portador é um buraco nessa regra, e ela é o
>    outro jeito de a decisão chegar.

E fica escrita a regra que torna o primeiro gatilho utilizável, porque ela é a que
responde "não" na maioria dos casos:

> **Consumidor que recebe no evento o dado de que precisa não autoriza nada, e
> portanto não precisa de identidade de serviço.** A pergunta a fazer de um
> consumidor novo é se ele **chama** alguém — não se há gente do outro lado.

### O caso real, quando chegar, não é a cotação

É o **`criarPedido`**. A `OperacaoPort` do `conversa.md` tem `cardapio()`,
`cotar()` e `criarPedido()`, e a cadeia inteira nasce de um webhook do WhatsApp. O
consumidor é um `Contato` identificado por `(telefone, estabelecimentoId)`, sem
sessão e sem token. O `criarPedido` é uma **escrita** do `conversation` no `order`
em nome de alguém que não tem credencial nenhuma.

**E a pergunta de lá não é de autorização.** Não é *"este chamador tem
permissão?"*; é *"o `order` pode acreditar que quem chamou é o `conversation`?"*.
É **autenticidade do chamador**, e isso muda qual mecanismo serve: um token que
diz *"sou o conversation"* responde; um esquema de permissões não.

**A borda de autenticação real daquela cadeia é a assinatura do Meta no webhook.**
É a única prova de origem que existe, e a ADR-037 já registra que a entrada
`/api/v1/webhooks/**` — pública, autenticada por assinatura no corpo — falta nas
cadeias dos oito serviços. A pergunta que a rodada de verdade vai ter de responder
é se essa prova **se propaga** para dentro ou **termina** no `conversation`.

### O que esta ADR não reabre

A ADR-045 já recusou, nas alternativas dela, três dos mecanismos que estariam na
mesa: **client credentials no `identity`**, **mTLS** e **segredo compartilhado por
serviço**. A rodada que decidir parte de lá, não do zero.

Em especial, escolher o token de serviço emitido pelo `identity` exige **reabrir o
argumento do `sub`**: a ADR-015 emendada reduziu o token a seis claims, sem
`scope`, e por isso *"um token de serviço e um de gente diferem só por convenção
no `sub`, que é uma fronteira de segurança sustentada por disciplina de
nomenclatura"*. Reabrir é legítimo; fingir que o argumento não existe, não.

## Consequências

**A cotação não espera uma decisão: espera um chamador** — o `order`, no marco
3. A decisão que parecia bloqueá-la não é dela.

**⚠ Em aberto, e não decidido aqui: em que marco a cotação é escrita.** A versão
desta ADR que chegou ao repositório dizia que ela "continua fora do marco 2". O
repositório diz outra coisa: a ADR-024 escreve *"decida antes de escrever o
`cotar`, e o `cotar` é marco 2"*, e o PRD §10 define o marco 2 como "Cardápio com
opções e disponibilidade qualitativa", sem nomear a cotação. Tirá-la do marco 2 —
ou escrevê-la nele sem chamador — é decisão de planejamento, e ela tem de emendar
a ADR-024 quando for tomada.

> **Respondido em 07/10/2026:** marco 3, com o `order-service`. A ADR-024 foi
> emendada, e com ela o prazo da decisão do preço por modalidade.

**A G-C3b pode escrever o consumidor citando esta ADR** em vez de um raciocínio
que só existia numa conversa.

**Fica registrada uma contradição latente, e ela não é resolvida aqui.** A ADR-018
desenha a cotação como `POST /internal/catalog/quote`. A emenda de 28/09 da
ADR-045 diz que rota `/internal/` responde **sobre o portador do token**. A
cotação não terá portador: quem a chamará age por um consumidor de WhatsApp.
**Uma das duas tem de ceder** — ou a cotação não vive sob `/internal/`, ou
`/internal/` passa a ter duas espécies de rota. É o segundo gatilho acima, e é a
rodada da cotação que paga.

**O `CLAUDE.md` deixa de mandar parar.** A armadilha dizia *"precisa autorizar sem
pessoa do outro lado? Aí a decisão é outra, e ela ainda não foi tomada"*, e com
isso qualquer consumidor novo parecia bloqueado. Passa a apontar para a regra: se
o dado vem no evento, siga.

**Nada de código muda.** Esta rodada não cria rota, não muda contrato, não toca
domínio. O produto dela é que a próxima pessoa a chegar aqui — inclusive eu, em
duas semanas — não refaça o raciocínio e não antecipe a decisão de novo.

## Alternativas consideradas

**Decidir agora, no mínimo que resolva o primeiro caso.** Um token de serviço
emitido pelo `identity`, com uma claim que diga "sou serviço". **Recusada:** o
primeiro caso não existe em código, e decidir sobre ele exigiria reabrir o
argumento do `sub` contra uma **previsão** em vez de contra um caso. É o que a
ADR-045 evitou de propósito ao escrever um gatilho.

**Declarar `/internal/` confiável por estar na rede do compose.** Seria a opção
mais barata e é **falsa hoje**: as rotas `/internal/` exigem o token do portador,
e a cadeia de segurança do `merchant` só libera `/actuator/health/**` e a
documentação sob flag. Adotá-la não seria registrar o estado atual — seria
**remover** uma proteção que existe, numa ADR que diz estar só descrevendo.

**Não escrever nada, e deixar o gatilho da ADR-045 como está.** **Recusada pelo
fato que motivou esta ADR:** aquele gatilho já disparou falso uma vez, e a
consequência foi reordenar a fila de rodadas por uma decisão que não era
necessária. Um gatilho que dispara falso é pior que nenhum, porque ele tem a
autoridade de estar escrito.

**Esperar o `conversation` ganhar código e decidir junto.** É o que vai acontecer
de fato, e não dispensa esta ADR: sem ela, o caminho entre aqui e lá fica
guardado só numa conversa — e foi exatamente isso que o décimo defeito deste
projeto nomeou.
