# Proposta — o que falta para operar como SaaS

- **Status:** **proposta. Não é ADR, não está no índice, não vale como decisão.**
- **Data:** <<DATA>> · **entrou no repositório na R-17**
- **Nasce de:** uma análise de 18/11/2026, trazida pelo Rafael, das propriedades que mudam como se
  constrói um SaaS — conferida contra o repositório em `b275220`
- **Só pode virar ADR depois de:** cada gatilho do fim disparar; esta proposta não decide nada antes

> ⚠ **Quem escreveu esta proposta não é advogado.** O que ela diz sobre a LGPD é informação para
> decidir e para levar a quem é; o lugar das perguntas jurídicas é `docs/operacao/revisao-juridica.md`.

---

## 1. As seis propriedades que mudam a construção, medidas

| # | A propriedade | O estado | Onde |
|---|---|---|---|
| 1 | **Um sistema, muitos donos de dados** | cumprida | a loja da URL autoriza (invariante 9 do `CLAUDE.md`); sem vínculo é 403 com o mesmo corpo para loja alheia e loja inexistente; recurso de outra loja é 404 com o mesmo corpo do inexistente (ADR-056) |
| 2 | **Quem escreve opera** | **desenhada, sem uso** | migrations por Flyway e Mongock, healthcheck, a subida em grupos (ADR-058) — e nenhum workflow implanta: a linha 16 do razão mede isso |
| 3 | **Uma versão só no ar** | cumprida no padrão | o que muda de uma loja para outra é dado da loja — modalidades, mínimo, desconto, troco, áreas —, e as chaves de ambiente (`DELIVERY_SEMEADURA`, `DELIVERY_DOCS_ABERTAS`) valem para o ambiente inteiro; a funcionalidade ligada por loja está armada na ADR-085 (linha 126 do razão) |
| 4 | **O dado do cliente é passivo** | **metade** | o que não se guarda está cumprido — coordenada, áudio, cartão, log sem documento nem token; os direitos do titular são esqueleto: a ADR-013 fixa retenção e backup de 30 dias, o procedimento é manual e serve a todos os direitos — o mesmo canal, o mesmo registro, o mesmo prazo de 15 dias (`docs/operacao/exclusao-de-titular.md`, *"status: esqueleto"*) —, mas acesso e portabilidade cabem numa frase, *"a varredura deste procedimento no sentido inverso"*, sem os passos |
| 5 | **Entrada sem vendedor** | cumprida | quem cria a loja nasce fundadora dela, `ADMINISTRADOR` com todas as permissões (`estabelecimento.md`:487, ADR-060) |
| 6 | **Segredo por loja** | **armada** | o primeiro gatilho da ADR-085 (o token do canal, antes do 7a; linha 118 do razão); e a proposta da emissão fiscal, que mantém o certificado A1 fora da plataforma enquanto o projeto for de uma pessoa só (R3) |

**O negócio já está decidido no PRD:** assinatura mensal por estabelecimento, sem percentual sobre a
venda, cobrada fora do produto (`PRD.md` §4.1, princípio 3; §7, a linha da cobrança da assinatura;
§9). O que falta é o primeiro lojista pagante — o segundo gatilho da proposta fiscal, linha 130 do
razão.

## 2. Além das seis

| O assunto | O dono |
|---|---|
| **o ciclo da assinatura** — a loja que não pagou, a que cancelou, e o dado dela | **esta proposta**, quarto gatilho |
| **voltar uma loja só ao estado de antes**, sem voltar as outras no tempo | **esta proposta**, quinto gatilho |
| atualizar sem parar no horário de pico | **esta proposta**, primeiro gatilho |
| o acesso de suporte auditado, com consentimento da loja | ADR-085, linha 125 do razão |
| a observabilidade por loja — uso, erro e custo | ADR-085, linha 124 do razão |
| a funcionalidade ligada por loja | ADR-085, linha 126 do razão |
| o limite por estabelecimento, contra a loja que derruba as outras | ADR-085 e ADR-086, linha 121 do razão; e o limite de taxa do gateway, linha 16 |
| a versão nova da API sem quebrar quem usa | ADR-085, linha 127 do razão |
| o custo do canal por conversa, e o repasse por faixa | `PRD.md` §9 |
| o fuso e o dia de cada loja | ADR-025 |
| o sinal de adoção | `PRD.md` §8.2 |

## 3. O que já está armado em outro lugar, sobre as seis

| O assunto | O dono |
|---|---|
| limite de taxa e `correlationId` no primeiro ambiente exposto | ADR-012, linha 16 do razão |
| o cofre e o segredo por estabelecimento, citado pelo nome | ADR-085, linha 118 do razão |
| o certificado A1 e os portões fiscais | `docs/propostas/emissao-fiscal-onde-mora-o-certificado.md`, linhas 129 e 130 do razão |
| retenção, anonimização, backup de 30 dias e a reaplicação das exclusões depois de restaurar | ADR-013 |
| o prazo de cada direito do titular | `docs/operacao/revisao-juridica.md`, pergunta 3 |

## 4. O que esta proposta arma, e por quê

**Onde o produto roda.** A linha 16 dispara no primeiro ambiente exposto e pede duas defesas do
gateway; ninguém decidiu o ambiente em si — onde, como a release chega sem parar a sexta à noite,
como volta atrás, onde ficam os backups que a ADR-013 já dimensionou. *Uma release ruim atinge todas
as lojas de uma vez: a volta atrás tem de existir antes da primeira.*

**Os segredos de loja, num cofre só.** A ADR-085 arma o cofre pelo primeiro segredo, o token do
canal, antes do 7a. Os outros dois candidatos não estão decididos: o certificado A1 **não** fica com a
plataforma enquanto valer a R3 da proposta fiscal, e, se ela cair, o advogado vem antes (o segundo
dos três portões da mesma proposta); e a credencial do PSP aparece em dois documentos que não dizem a
mesma coisa — o `pagamento.md` põe a cobrança Pix com QR dinâmico e webhook no marco 4, e a R5 da
proposta fiscal diz que a plataforma não inicia cobrança enquanto valer a mesma condição, com o
terceiro portão pedindo advogado antes de iniciar. *Um cofre decidido
só para o token, sem dizer o que acontece com os outros dois, obriga a decidir de novo.*

**Os passos de acesso e portabilidade.** O `exclusao-de-titular.md` já serve a todos os direitos do
art. 18 — mesmo canal, mesmo registro, mesmo prazo —, e diz que acesso e portabilidade são *"a
varredura deste procedimento no sentido inverso: os mesmos oito serviços, lendo em vez de apagar"*.
A exclusão tem a execução esboçada serviço por serviço, e o próprio documento lista o que falta
escrever antes do primeiro cliente; a leitura não tem nem o esboço — nem o que se entrega, nem em
que formato.
A saga automática da exclusão já espera o primeiro titular na linha 84 do razão. *Com zero titulares,
os passos são cerimônia; com o primeiro, o prazo de 15 dias corre do registro.*

**O ciclo da assinatura.** O PRD decidiu cobrar fora do produto, e nada decide o que o produto faz
com o resultado da cobrança. *Cobrar é a parte fácil; a loja inadimplente no meio do expediente,
com pedidos na fila, é a difícil.*

**Uma loja só, de volta.** O backup da ADR-013 é do banco inteiro. *Voltá-lo para desfazer o engano
de uma loja desfaz o dia de todas as outras.*

## 5. Candidatos que cada ADR deve pesar — nenhum decidido

Vêm de uma segunda análise, de 19/11/2026, trazida pelo Rafael, conferida contra o repositório no
mesmo dia. **São opções, não decisões:** cada uma é lida pela ADR do gatilho que a cita, contra o
repositório daquele dia, e entra, muda ou é recusada lá.

### 5.1 · O cofre — criptografia em envelope

**O que é:** cada segredo de loja é cifrado com uma chave própria daquela loja; essa chave é cifrada
por uma chave mestra que mora num serviço de gestão de chaves, e nunca sai dele. O segredo só é
decifrado em memória, no instante do uso — a assinatura de um documento fiscal, a chamada ao PSP, o
envio pelo canal —, e nunca vai a disco, log ou resposta.

**Por que pesa:** um vazamento do banco entrega segredos cifrados, sem a chave mestra; e a troca da
chave de uma loja não toca as outras.

**Os cuidados:**

- enquanto valer a R3 da proposta fiscal, **o certificado A1 não entra no cofre**: ou um provedor
  por API o guarda, ou ele fica na loja. O cofre começa pelo token do canal, e a credencial do PSP
  depende de como o marco 4 e a R5 se acertarem;
- o serviço de gestão de chaves depende de onde o produto roda — o primeiro gatilho desta proposta;
- a regra do repositório continua: segredo citado pelo nome, nunca copiado para código ou
  configuração (ADR-085).

### 5.2 · O ciclo da assinatura — estados com carência

**O que é:** uma máquina de estados da loja como assinante — por exemplo, `TRIAL`, `ATIVA`,
`EM_AVISO`, `SO_LEITURA`, `CANCELADA` —, com uma carência e um aviso no painel antes de qualquer
bloqueio, e o bloqueio em degraus, nunca de uma vez.

**Por que pesa:** um cartão recusado não pode parar a loja no meio da sexta à noite, com pedidos na
fila.

**Os cuidados:**

- **"Bloquear só os pedidos novos" é recusar o consumidor que está no canal**, e não só o lojista.
  Quem paga a conta do bloqueio é o cliente da loja: **é decisão de produto, do Rafael**;
- a retenção do documento fiscal emitido **já está decidida** — intocado, art. 16, I (ADR-013) —, e
  vale em qualquer estado, inclusive `CANCELADA`;
- o produto não guarda cartão nem boleto (`PRD.md` §7): o estado muda por aviso do provedor de
  cobrança, e não por cobrança feita aqui;
- o tempo que o dado da loja cancelada fica, e como ele sai, é da ADR-013 e da revisão jurídica.

### 5.3 · O acesso de suporte — prazo, motivo e registro

**O que é:** uma sessão de suporte com prazo curto, motivo obrigatório, e cada acesso registrado
numa trilha que a própria loja vê.

**Por que pesa:** o *"pedido que sumiu"* se investiga sem pedir senha a ninguém e sem rota que fure a
invariante 9.

**Os cuidados:**

- **a ADR-085 exige consentimento da loja** (linha 125 do razão); uma sessão aberta sem ele não
  cumpre o gatilho que já existe;
- **ver não é agir.** *Impersonation* — o suporte agindo como o usuário — vai além de ler os dados
  dele, e cada ação feita assim teria de carregar os dois autores. Ler, sem agir, é o recorte menor e
  mais seguro;
- a trilha de acesso é ela mesma dado pessoal de quem acessou: retenção pela ADR-013.

### 5.4 · Voltar uma loja só — o que já existe

**O que é:** desfazer o engano de uma loja sem voltar o banco inteiro.

**O que já existe e reduz o problema:** produto não se apaga, fica `INATIVO` (C9 do `catalogo.md`).
O que uma loja perde de verdade por engano é o que se substitui — os grupos e as opções de um
produto, um preço, **e a lista de áreas de entrega**: o `PUT` dela substitui a lista inteira, e a área
que fica de fora some (ADR-081 §1). *A área desativada fica, com `ativa` falso; a omitida, não.*

**As opções que a ADR do ambiente pesa:** histórico das versões do que se substitui, por loja; ou uma
exportação lógica por `estabelecimentoId`, restaurável sozinha. Nas duas, a reaplicação das exclusões
da ADR-013 vale.

## Gatilho escrito

**A primeira rodada que preparar um ambiente fora do computador do Rafael.** Aí uma ADR decide onde o
produto roda, como a release chega lá sem parar o horário de pico, como volta atrás, e onde ficam os
backups de 30 dias da ADR-013 — antes do primeiro dado de loja nesse ambiente.

**A ADR que decidir o cofre, pelo primeiro gatilho da ADR-085.** Aí ela decide o cofre para o token
do canal e diz o que acontece com os outros dois candidatos — o certificado A1, que a R3 da proposta
fiscal deixa fora enquanto vale, e a credencial do PSP, que o `pagamento.md` (marco 4) e a R5 da
mesma proposta precisam acertar antes.

**O primeiro titular real: o primeiro lojista pagante, ou o primeiro consumidor que conversar com o
canal do 7a.** Aí o `exclusao-de-titular.md` ganha os passos da varredura no sentido inverso — o que
cada um dos oito serviços entrega no acesso, e em que formato na portabilidade —, manual e auditado
como a exclusão, depois da resposta à pergunta 3 da revisão jurídica.

**O primeiro lojista que aceitar pagar a assinatura.** Aí uma ADR decide o que o produto faz com
a loja que não pagou e com a que cancelou — se suspende e a partir de quando, o que continua
acessível com pedidos na fila, por quanto tempo o dado dela fica (ADR-013) e como ele sai —, antes
da primeira cobrança.

**A ADR que decidir onde o produto roda, pelo primeiro gatilho desta proposta.** Aí ela decide também
como uma loja só volta ao estado de antes — os grupos e as opções de um produto e a lista de áreas
de entrega trocados por engano, já que produto não se apaga (C9 do `catalogo.md`) — sem voltar as outras
lojas no tempo, e a reaplicação das exclusões da ADR-013 vale para esse caminho também.
