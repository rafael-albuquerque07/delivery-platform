# ADR-085 — As ideias de plataforma esperam pelo gatilho

- **Status:** aceita · **emendada em 17/11/2026**: a conversa fiscal entra no repositório (R-15)
- **Data:** 17/11/2026
- **Rodada:** R-14
- **Relacionada:** ADR-027 (compatibilidade de eventos), ADR-056 (recurso de outra loja é 404),
  ADR-072 (a seção que o comando colhe), ADR-075 (o canal, e o número próprio por
  estabelecimento), ADR-083 (a ordem de construção), PRD §4.1 (princípio 3) e §9

## Contexto

**Em 17/11/2026 o Rafael perguntou que ideias de engenharia do Salesforce servem a este
projeto.** A resposta separou o que o projeto já faz, o que cabe com o problema já presente, o que
entra com o marco 3 e o que só faz sentido com lojista real. A lista existia só na conversa. *A
conversa sobre emissão fiscal mostrou o custo disso: uma compactação a levou, e uma resposta foi
dada por cima dela, contra o §9 do PRD.*

**O que o projeto já faz**, para ninguém propor de novo:

| Ideia | Aqui |
|---|---|
| Versão de API | `/api/v1/` nas rotas, eventos com sufixo `V1`, e a ADR-027 |
| Regra de validação | As invariantes do domínio, com teste |
| Cobertura mínima para publicar | Mutação com previsão escrita antes |
| Eventos de plataforma | Outbox, RabbitMQ e eventos de domínio |
| Ordem de execução ao gravar | A fronteira transacional do `pedido.md` |
| Modelo de compartilhamento | Permissão por vínculo; recurso de outra loja é 404, com o corpo do inexistente (ADR-056) |

## Decisão

> **Nenhuma das ideias abaixo se constrói antes do próprio gatilho.** Cada uma está na seção de
> gatilhos desta ADR, no fim, e por isso no razão.

**Três são recusadas:**

- **Objetos e campos definidos pelo comerciante.** Viram tabela genérica, sem invariante e sem
  teste.
- **Configuração para tudo.** Cada opção é uma tela a mais para quem tem a loja aberta e o
  telefone tocando.
- **Licença por usuário.** O §9 do PRD cobra assinatura por estabelecimento, e o princípio 3
  recusa preço que cresça com a loja.

## Consequências

**Nada é construído agora.** O razão ganha uma linha por ideia.

**Uma ideia que chegar antes do gatilho volta para esta ADR antes de virar código.**

## Alternativas consideradas

**Construir as do primeiro grupo agora.** **Recusada:** o problema de cada uma começa num marco que
não começou.

**Deixar a lista na conversa.** **Recusada:** é o caso da conversa fiscal.

## Gatilho escrito

**A primeira rodada que guardar o token do canal de uma loja — antes do 7a.** Aí nasce o segredo
por estabelecimento, referenciado por nome: o token mora num cofre e é citado pelo nome, nunca
copiado para código ou configuração, e uma ADR decide o cofre e o nome antes daquela rodada.
*Medido na R-14:* a ADR-075 pede *"número próprio por estabelecimento"*; nenhuma migration guarda
segredo de loja — as três que falam de canal tratam do código de verificação e do token do convite
—, e das 15 variáveis do `.env.example` nenhuma tem token, secret ou segredo no nome.

**A T01 no `order-service`, que congela o preço no pedido.** Aí nasce o histórico de alteração —
quem, quando, antes e depois —, para a pergunta *"quem mudou o preço ontem?"*, com a decisão de o
que se registra, e onde. *Medido na R-14:* nenhuma gravação de configuração nem de cardápio guarda
autor ou valor anterior — o `membro` tem `alterado_em`, sem autor, e o convite tem
`convidado_por`; dos seis eventos `…AlteradoV1` declarados no `contracts/eventos.md`, dois são
produzidos em código, o `ExpedienteAlterado` e o `VinculoAlterado`.

**A primeira rodada de código do `order-service`, onde moram as invariantes de valor.** Aí nasce a
mutação no CI — PIT no back e Stryker no front, no lugar do script de mutação escrito em cada
rodada —, com um job de limite mínimo e a ADR que o fixa.

**A T01 no `order-service`, para o limite por estabelecimento.** Aí nasce um teto por
`estabelecimentoId` no gateway, para que um inquilino não derrube os outros no Postgres
compartilhado, generalizando o teto de custo da conversa (H8.3, V12): o teto, onde ele mora, e o
teste que o prova.

**O primeiro consumidor de evento do `order-service`.** Aí nasce o processamento em lote: a
varredura de expediente e os consumidores de evento funcionam para centenas de lojas sem uma
consulta por item, e um teste com centenas de lojas conta as consultas.

**O primeiro lojista pagante, para os papéis prontos.** Aí o convite oferece papéis prontos —
atendente, cozinha, entregador — como conjuntos de `Permissao`, no lugar de escolher permissão por
permissão.

**O primeiro lojista pagante, para a observabilidade.** Aí nasce a observabilidade por
estabelecimento — uso, erro e custo, por loja —, com a decisão de o que se mede e onde se lê.

**O primeiro lojista pagante, para o acesso de suporte.** Aí nasce o acesso de suporte auditado,
com consentimento da loja e registro de cada acesso, pela LGPD — decidido numa ADR antes do
primeiro acesso.

**O primeiro lojista pagante, para as funcionalidades por loja.** Aí nasce a funcionalidade ligada
por estabelecimento antes de ligar para todos: a decisão do mecanismo, e a primeira funcionalidade
que o usa.

**A primeira rota `/api/v2/`.** Aí nasce a política de depreciação de versão de API, escrita antes
da rota.

**O primeiro comerciante que pedir para integrar outro sistema.** Aí uma ADR decide se o produto
vira plataforma — API pública, webhooks de saída para terceiros, automações montadas pelo
comerciante.

## Emenda de 17/11/2026, R-15 — a conversa fiscal entra no repositório

A conversa sobre emissão fiscal citada no Contexto está registrada na proposta `docs/propostas/emissao-fiscal-onde-mora-o-certificado.md`, que
entrou no repositório nesta rodada. O corpo fica como está.
