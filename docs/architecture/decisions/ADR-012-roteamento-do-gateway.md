# ADR-012 — Roteamento do gateway por recurso, não por serviço

**Status:** Aceita — 23/08/2026
**Relacionada:** ADR-011 (autorização), ADR-015 (JWT), ADR-021 (catálogo de serviços), ADR-023 (fronteira com o PSP)
**Invariantes do `CLAUDE.md`:** 9 (identificador da URL não é confiável)
**Muda o esqueleto:** `backend/infra/gateway/src/main/resources/application.yml`

## Contexto

O gateway hoje roteia por nome de serviço:

```
/api/v1/identity/**   → identity-service
/api/v1/catalog/**    → catalog-service
/api/v1/order/**      → order-service
```

É a configuração mais simples possível, e tem um defeito que só aparece tarde:
**a decomposição interna vaza para a URL pública**. O cliente passa a saber, e a
depender, de quais serviços existem.

Este repositório já produziu a prova disso. A ADR-021 apagou três serviços e
criou dois. Se aquelas URLs tivessem cliente, `/api/v1/inventory/**` e
`/api/v1/notification/**` seriam quebra de contrato público — e a refatoração de
escopo, que foi a coisa mais saudável que aconteceu no projeto, teria custado uma
migração de API. Só não custou porque não há cliente ainda.

Ao mesmo tempo, `docs/dominio/estabelecimento.md` §3 já pressupõe outra coisa:
rotas sob `/merchants/{estabelecimentoId}/...`, porque é ali que a invariante 9
se materializa — o identificador do estabelecimento vem da URL e precisa ser
confrontado com o usuário autenticado, sempre no mesmo lugar.

Os dois documentos discordam desde o alinhamento da v1.1. Esta ADR decide.

## Decisão

### Rota por recurso

```
/api/v1/auth/**                              → identity      (sem estabelecimento)
/api/v1/me/**                                → identity      (perfil, meus vínculos)

/api/v1/merchants/{merchantId}/settings/**   → merchant
/api/v1/merchants/{merchantId}/team/**       → merchant
/api/v1/merchants/{merchantId}/areas/**      → merchant
/api/v1/merchants/{merchantId}/couriers/**   → merchant      (vínculo e remuneração)
/api/v1/merchants/{merchantId}/catalog/**    → catalog
/api/v1/merchants/{merchantId}/orders/**     → order
/api/v1/merchants/{merchantId}/deliveries/** → delivery
/api/v1/merchants/{merchantId}/shifts/**     → settlement    (jornadas e fechamento)
/api/v1/merchants/{merchantId}/payments/**   → payment

/api/v1/couriers/me/**                       → delivery      (visão do entregador)

/api/v1/webhooks/psp/**                      → payment       (público, assinado)
/api/v1/webhooks/channel/**                  → conversation  (público, assinado)
```

O cliente não sabe que existe `order-service`. Sabe que existe pedido, e que
pedido pertence a um estabelecimento. **Mudar o dono de um recurso vira alteração
de uma linha no gateway, não quebra de contrato.**

Se `shifts` sair de `settlement` um dia, ou se `couriers` mudar de dono entre
`merchant` e `delivery`, nenhum cliente percebe.

### `merchantId` sempre na mesma posição

Todo recurso com escopo de estabelecimento tem o identificador **no segundo e
terceiro segmentos**, sem exceção. Isso não é estética:

- há **um** lugar de onde extrair o identificador, e um filtro no gateway pode
  colocá-lo no contexto de forma uniforme;
- a invariante 9 fica verificável do mesmo jeito em todo endpoint — se o
  identificador estivesse ora no path, ora na query, ora no corpo, cada endpoint
  teria seu próprio jeito de esquecer de confrontá-lo.

### O gateway autentica; quem autoriza é o serviço

Divisão que precisa estar escrita, porque a tentação de mover autorização para o
gateway é forte e o erro é caro.

| | Gateway | Serviço |
|---|---|---|
| Validar assinatura e validade do JWT | **sim** | sim, de novo |
| Rejeitar requisição sem token onde token é exigido | **sim** | — |
| Saber qual permissão o endpoint exige | **não** | **sim** |
| Confrontar `merchantId` com o vínculo do usuário | **não** | **sim** (ADR-011) |
| CORS, limite de taxa, `correlationId` | **sim** | — |

O gateway **não pode** autorizar porque não sabe que `POST .../orders` exige
`ALTERAR_STATUS` e `GET .../orders` exige `VER_PEDIDO`. Colocar esse mapa no
gateway é duplicar regra de domínio na borda, onde ela envelhece sem ninguém ver.

**O serviço revalida o token.** Não é redundância desperdiçada: é o que impede
que alcançar a rede interna, por qualquer caminho, equivalha a estar autorizado.

### Webhooks são públicos, e isso precisa ser explícito

`/api/v1/webhooks/**` não exige JWT — o PSP e a Meta não têm token nosso. A
autenticação deles é **assinatura no corpo**, validada dentro do serviço
(ADR-023, `conversa.md` §11).

Isso é o erro clássico de configuração de gateway, nas duas direções: ou o filtro
de autenticação bloqueia o webhook e as notificações somem em silêncio, ou a
exceção é escrita larga demais e abre mais do que devia. Por isso o caminho é
`/webhooks/` explícito, prefixo próprio, e **nenhuma outra rota** mora sob ele.

### Versão no caminho

`/api/v1` no path, não em cabeçalho. Menos "puro" que negociação de conteúdo, e
muito mais utilizável: aparece no log, funciona com `curl`, dá para abrir no
navegador, e um erro de versão é visível em vez de silencioso.

### O gateway não agrega

Uma requisição, um serviço. O gateway roteia, autentica, limita taxa e propaga
`correlationId` — não compõe resposta de dois serviços.

Agregação transforma o gateway em quem conhece todos os domínios, e é assim que
se constrói um monólito distribuído com um proxy no meio. Se o painel precisar de
dados de três serviços, ele faz três chamadas — e se isso doer, a resposta é um
BFF próprio, decidido em ADR própria, não uma regra escondida no roteador.

## Consequências

**Positivas**

- A URL pública para de depender da decomposição interna. Refatoração de serviço
  deixa de ser quebra de contrato.
- A invariante 9 fica uniforme e verificável: um lugar, um filtro, uma regra.
- A URL vira documentação do domínio — `merchants/{id}/shifts` diz o que é sem
  precisar saber qual serviço responde.
- Autorização fica onde a regra mora, e não duplicada na borda.

**Negativas**

- **Configuração do gateway mais complexa.** Vários serviços sob o mesmo prefixo,
  distinguidos pelo segmento seguinte. Ordem de predicado passa a importar, e uma
  rota mal ordenada captura o que era de outra.
- **O mapa recurso → serviço vira conhecimento concentrado no gateway.** É o
  ponto da decisão, mas exige disciplina: esse mapa e o `contracts/openapi/`
  precisam concordar, e nada os obriga a isso automaticamente.
- **Depurar fica um passo mais indireto.** Ver `/merchants/x/orders` no log não
  diz qual serviço respondeu. Mitigação: `correlationId` e o nome do serviço na
  resposta do actuator.
- **Muda o esqueleto agora.** O `application.yml` do gateway precisa ser
  reescrito, e o `estabelecimento.md` §3 deixa de ser aspiração.

## Alternativas consideradas

- **Manter rota por serviço.** Mais simples de configurar, e é o que está lá.
  Rejeitada pelo argumento do contexto: este projeto já refatorou serviços uma
  vez em três dias, e a próxima vez custaria contrato público. A simplicidade é
  real, mas é a do gateway — paga pelo cliente.
- **Autorização no gateway.** Rejeitada: o gateway teria que conhecer o mapa
  endpoint → permissão, que é regra de domínio. Duplicada na borda, ela envelhece
  em silêncio, e o dia em que divergir do serviço é o dia em que alguém acessa o
  que não devia.
- **Versão em cabeçalho** (`Accept: application/vnd.delivery.v1+json`).
  Rejeitada: mais correta na teoria de REST, pior em tudo que é operação —
  invisível no log, chata de testar, e um cliente que esquece o cabeçalho recebe
  uma versão por omissão sem perceber.
- **Gateway como BFF, agregando.** Rejeitada aqui e adiável: se o painel provar
  que precisa, vira ADR própria com um BFF explícito. Não vira exceção discreta
  na configuração do roteador.
- **`/api/v1/estabelecimentos/{id}/...` em português**, coerente com os
  documentos de domínio. Considerada de verdade. Rejeitada porque URL pública é
  interface técnica, e o vocabulário de interface HTTP é inglês por convenção
  ampla — mesmo argumento que mantém `GET`, `Content-Type` e `orders`. O domínio
  continua em português onde o negócio fala.
