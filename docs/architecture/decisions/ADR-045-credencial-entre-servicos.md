# ADR-045 — A credencial entre serviços é o token de quem pediu

- **Estado:** aceita · **emendada em 28/09/2026**: a primeira rota interna existe, e o
  adaptador do consumidor tem de separar "negado" de "produtor fora"
- **Data:** 26/09/2026
- **Fecha:** a pendência que a ADR-043 criou — *"a credencial entre serviços nunca foi decidida"*
- **Emenda:** ADR-011 (a assinatura do `AutorizacaoComercialPort`)
- **Relacionadas:** ADR-012, ADR-015 emendada, ADR-037, ADR-044
- **Produtor implementado na G-B2** (28/09/2026): a rota `/internal/` do `merchant`.
  O consumidor — o adaptador com cache e falha fechada — nasce no `catalog`, na G-B3.

## Contexto

A ADR-043 escreveu a pendência e o gatilho:

> *"Uma chamada de serviço para serviço precisa de credencial, e a credencial
> entre serviços nunca foi decidida. (…) **Gatilho escrito:** o primeiro serviço
> com rota protegida. Ele chega precisando das duas coisas ao mesmo tempo, e aí
> a credencial é o assunto da rodada em vez de um detalhe dela."*

O gatilho disparou. O `catalog-service` é esse serviço: ele precisa perguntar ao
`merchant` *"esta pessoa pode criar produto nesta loja?"* — porque o JWT não
carrega permissão (invariante M2) e ele não pode ler o banco do outro
(invariante 8).

E precisa de uma segunda resposta que só o `merchant` tem: **o dia operacional
corrente da loja**, para carimbar o `expedienteDeReferencia` quando alguém marca
um produto como esgotado (`catalogo.md` §3). O cálculo exige o `fusoHorario`,
que é dado do `merchant`.

Duas perguntas, o mesmo problema: com que credencial o `catalog` se apresenta.

## Decisão

**Nenhuma credencial de serviço nasce. O `catalog` encaminha o token que
recebeu.**

```
pessoa ──token──▶ gateway ──token──▶ catalog ──o mesmo token──▶ merchant
                  autentica          autoriza-se perguntando    responde sobre
                                                                 o portador
```

O `merchant` autentica exatamente o mesmo token que autenticaria vindo do
gateway: mesmo emissor, mesma audiência, mesmo JWKS, os mesmos validadores que a
C-A escreveu. **Não há peça nova em lugar nenhum** — nem emissão, nem registro
de clientes, nem segredo em configuração, nem certificado.

Que o cabeçalho chega intacto **não é suposição**: a rodada E-A tem um teste que
afirma isso (`o_token_atravessa_para_o_servico`), e ele existe porque a ADR-012
diz que o serviço revalida o token.

### Por que isto é melhor, e não só menor

**O raio de exposição encolhe.** Com credencial própria, um `catalog`
comprometido pergunta ao `merchant` sobre *qualquer* usuário de *qualquer* loja.
Encaminhando o token, ele só consegue perguntar sobre a pessoa que já estava
falando com ele — e sobre essa pessoa ele já sabia tudo. O atacante não ganha
alcance novo.

**A invariante 9 sai de graça.** A rota responde sobre *quem apresentou o
token*, então **não existe `usuarioId` no caminho** para alguém esquecer de
confrontar com o autenticado. A invariante que a C-A precisou provar com teste
aqui é impossível de violar por construção.

**Não cria o padrão que os oito serviços copiam.** Uma identidade de serviço é
uma decisão que, tomada agora, seria replicada oito vezes antes de alguém
perguntar se estava certa. Esta decisão adia exatamente a parte cara.

### Onde as rotas moram

Prefixo `/internal/`, como a ADR-018 já usa para o `cotar`. **O gateway não
roteia `/internal/**`** — as catorze rotas dele são todas `/api/v1/**` —, então o
prefixo é inalcançável de fora por construção. Desde a E-A há asserção de que
caminho que nenhuma rota cobre responde 404 sem chegar a serviço nenhum
(`caminho_que_nenhuma_rota_cobre_nao_vaza_para_servico_nenhum`), mas ela é
exercida com um caminho de `/api/v1/`; `/internal/` cai na mesma regra e
**não tem teste próprio**.

### Emenda à ADR-011: a assinatura muda

A ADR-011 desenha:

```java
Optional<ContextoDeAcesso> contexto(UUID usuarioId, UUID estabelecimentoId);
```

O `usuarioId` era o chamador **afirmando** quem era o usuário. Com o token
encaminhado, ele não afirma: **prova**. O parâmetro deixa de existir:

```java
Optional<ContextoDeAcesso> contexto(UUID estabelecimentoId);
```

O `ContextoDeAcesso` continua devolvendo o `usuarioId` — como *resposta*, que é
o que ele sempre foi.

Tudo o mais da ADR-011 fica de pé: cache Caffeine em processo, 60 s positivo,
10 s negativo, invalidação por `VinculoAlteradoV1`, e **falha fechada**.

> **A forma final da porta nasce com o primeiro consumidor.** Esta ADR decide o
> mecanismo, não a assinatura definitiva. Se ao construir o `catalog` aparecer
> um detalhe que a contradiga, é esta ADR que se emenda — e é por isso que ela
> foi escrita antes, e não durante.

## Consequências

**Positivas**

- Zero infraestrutura nova, zero segredo novo, zero rota de emissão nova.
- O `merchant` não precisa distinguir chamada de gente de chamada de serviço,
  porque **não há** chamada de serviço: há chamada de gente atravessando um
  serviço.
- A chave da cache continua sendo `(usuarioId, estabelecimentoId)`, e o
  `usuarioId` vem do token que o consumidor já validou.

**Negativas**

- **Não serve para autorização fora de uma requisição de usuário.** Reagir a um
  evento, rodar uma varredura, executar um procedimento operacional — nada disso
  tem token. No marco 2 não existe nenhum caso: criar, alterar, despublicar
  produto e marcar disponibilidade são todos atos de gente.
  **Gatilho escrito:** o primeiro serviço que precisar autorizar sem uma pessoa
  do outro lado. Aí a identidade de serviço é o assunto da rodada, com um caso
  real em cima da mesa em vez de uma previsão.
- **O token vence em 30 minutos** (ADR-037). Uma chamada encadeada longa pode
  esbarrar nisso. Hoje as cadeias têm um salto.
- **O `catalog` passa a depender do `merchant` para responder qualquer coisa**,
  e isso é a falha fechada da ADR-011 se propagando. Era consequência já
  assumida lá; aqui ela ganha o segundo serviço.

## Alternativas consideradas

- **Client credentials no `identity`.** Um emissor só, mesmo JWKS, mesmos
  validadores. Rejeitada por custo e por forma: exige registro de clientes com
  segredo, e a ADR-015 emendada reduziu o token a seis claims — sem `scope`, um
  token de serviço e um de gente diferem **só por convenção no `sub`**, que é
  uma fronteira de segurança sustentada por disciplina de nomenclatura.
- **mTLS.** Correta e pesada: a identidade é a rede, não há segredo em
  configuração. Rejeitada porque consome a rodada inteira em gestão de
  certificado, Compose e CI, e não entrega uma linha de domínio. Continua sendo
  a resposta certa quando houver malha de serviço.
- **Segredo compartilhado por serviço.** O mais fácil de escrever e o pior de
  sustentar: sem expiração, sem rotação, e não diz *quem* chamou — só *qual
  serviço*. Viraria o padrão copiado oito vezes.

## Emenda de 28/09/2026 — o primeiro caminho `/internal/`, e o que ele exige do consumidor

Esta ADR fixava o **prefixo** e não o caminho. O primeiro nasceu na G-B2:

```
GET /internal/merchants/{estabelecimentoId}/me/contexto-de-acesso
```

Três escolhas, cada uma com a regra que a sustenta:

- **`{estabelecimentoId}`**, e não `{merchantId}`. A ADR-035 e o `CLAUDE.md`
  mandam identificador e recurso em português, e o `EquipeController` já usa
  esse nome. O `merchantId` existe só como variável de predicado no gateway —
  que não vê `/internal/`.
- **`/me/`**, com o sentido que o gateway já lhe dá em `/api/v1/me/**` e
  `/api/v1/couriers/me/**`: o portador do token. Ele **fecha uma porta**: com
  `me` no caminho, ninguém acrescenta depois um `/{usuarioId}/contexto-de-acesso`
  sem perceber que está criando outro recurso — e é justamente essa porta que a
  seção "Por que isto é melhor, e não só menor" desta ADR quer fechada: *"não
  existe `usuarioId` no caminho"*.
- **`contexto-de-acesso`**, em português, pelo mesmo motivo do primeiro item. O
  `/team` da C-A diverge dessa regra e está congelado em contrato; corrigi-lo é
  quebra de contrato e assunto de outra rodada.

A rota entra no contrato congelado (`contracts/openapi/merchant-service.json`,
ADR-039) com a tag `interno`, e com o requisito `bearerAuth` que o contrato do
`merchant` declara na raiz. A diferença de `/api/v1/` fica marcada no contrato,
e não por exclusão dele — o precedente é o `/.well-known/jwks.json` do
contrato do `identity`, também consumido por serviço e também fora do gateway.

### A rota não exige permissão, e é por isso que ela não pode receber `usuarioId`

Pedir permissão para descobrir permissão é circular: qualquer requisito aqui
teria de ser resolvido por esta mesma consulta. O que torna a ausência de
requisito segura é a outra metade: **não existe `usuarioId` na entrada** — nem
no caminho, nem em parâmetro, nem em cabeçalho. A rota responde sobre o portador
e sobre mais ninguém, e o portador já descobre o próprio vínculo tentando usar a
loja.

**As duas metades são uma decisão só.** Quem algum dia acrescentar uma forma de
perguntar pelo contexto de *outro* usuário transforma esta rota, que hoje não
exige nada, num vazamento das permissões de todo mundo. É por isso que o `me`
está no caminho e não implícito.

### Quatro recusas, uma resposta

Loja inexistente, sem vínculo, vínculo `SUSPENSO` e vínculo `REMOVIDO` dão o
mesmo **403** com o mesmo corpo (M7). Os dois últimos exigem filtro explícito no
caso de uso: o repositório do `merchant` devolve o vínculo em qualquer estado.

"Mesmo corpo" tem uma exceção medida, e ela não vaza nada: o `instance` do
`ProblemDetail` é o caminho da requisição, que o Spring preenche sozinho — cada
recusa ecoa o caminho que o próprio chamador pediu. O
`ContextoDeAcessoControllerIT` confere o `instance` contra o caminho e compara o
resto do corpo.

### O que o consumidor tem de distinguir, e esta ADR não dizia

A ADR-011 descreve dois comportamentos diferentes e não diz como separá-los:

| o que aconteceu | o consumidor faz | por quê |
| --- | --- | --- |
| **403** — não há vínculo ativo | nega, e **pode cachear** por 10 s | é uma resposta: o produtor sabe e disse |
| **timeout, 5xx, conexão recusada** | nega, e **não cacheia** | não é resposta: é ausência de resposta, e o TTL de 60 s já é a janela de tolerância a queda |

Cachear a indisponibilidade como se fosse negação estenderia uma queda de dois
segundos em dez segundos de recusa para todo mundo. E cachear positivo depois de
um erro é o contrário disso, e pior.

**Falha fechada nos dois casos** — o que muda é só o que se guarda.

## Emenda de 07/10/2026 — o gatilho estava escrito de um jeito que dispara falso

O gatilho desta ADR dizia *"o primeiro serviço que precisar autorizar sem uma
pessoa do outro lado"*. Em 01/10/2026 ele foi lido como tendo disparado, e a fila
de rodadas foi reordenada para tomar a decisão antes da cotação.

**Não havia disparado.** A ADR-054 procurou o caso no repositório e não achou
nenhum: a varredura do `merchant` publica e não autoriza (as Consequências da
ADR-046 já diziam isso), o consumidor da G-B4 só mexe em memória, e a reativação
recebe o expediente dentro do evento.

O defeito é da redação: *"agir sem pessoa do outro lado"* descreve a varredura tão
bem quanto descreve o caso de verdade. **O gatilho é substituído pelos dois da
ADR-054**, que distinguem quem pergunta de quem é perguntado — e aquela ADR também
escreve a regra que responde "não" na maioria dos casos: *consumidor que recebe no
evento o dado de que precisa não autoriza nada*.

As **alternativas consideradas** desta ADR continuam valendo inteiras, e a rodada
que um dia decidir parte delas.
