# ADR-021 — Catálogo de serviços do MVP

**Status:** Aceita — 21/08/2026 · **emendada em 24/09/2026**: o `merchant` não
tem Redis, e a persistência além do banco principal passa a apontar a decisão
que a pôs ali (ver "Emenda de 24/09/2026")
**Relacionada:** ADR-004 (um estabelecimento por pedido), ADR-020 (taxa por área), ADR-022 (remuneração)
**Fonte:** Resposta ao Adendo Crítico · Arquitetura v1.1, §5.3
**Premissas do PRD que sustentam esta decisão:** P1, P2, P3, P5, P6

## Contexto

O esqueleto do repositório foi criado sob a arquitetura v1.0: nove serviços de
negócio desenhados para um marketplace de delivery. O adendo crítico mostrou que
a premissa estava errada — o cliente é o **comerciante**, a plataforma não
custodia o dinheiro, o entregador é da casa, o pedido nasce numa conversa. A
resposta v1.1 aceitou o diagnóstico e reduziu o catálogo a oito serviços.

Só que **o esqueleto continuou como estava**. Durante três dias o repositório
teve documentação v1.1 e estrutura v1.0 — e isso já custou uma decisão errada:
um agente adicionou `messaging-conventions` ao `identity-service` lendo a coluna
"Publica eventos" de um README que descrevia uma arquitetura superada. O build
ficava verde compilando `geolocation-service` e `notification-service`, dando
confiança a serviços que não deveriam existir.

Estrutura que contradiz a documentação não é dívida cosmética. É uma fonte de
verdade concorrente, e ela vence — porque o código é o que a pessoa abre.

## Decisão

Oito serviços de negócio e um gateway.

| Serviço | Porta | Persistência | Por quê | O que é dele |
|---|---:|---|---|---|
| `gateway` | 8080 | — | | Roteamento, CORS, limite de taxa |
| `identity` | 8081 | PostgreSQL | | Conta, autenticação, emissão de JWT (ADR-015) |
| `merchant` | 8082 | PostgreSQL | | Estabelecimento, equipe, permissões, áreas e taxas, vínculo de entregador |
| `catalog` | 8083 | MongoDB + Redis | Redis: cache do cardápio público — ADR-005, `catalogo.md` §7 | Produto, opções, disponibilidade qualitativa, cotação |
| `settlement` | 8084 | PostgreSQL | | Jornada, custódia, divergência, extrato, fechamento |
| `order` | 8085 | PostgreSQL + Redis | Redis: **sem motivo escrito** — ver "Emenda de 24/09/2026" | Pedido, valores, liquidação registrada, Saga |
| `payment` | 8086 | PostgreSQL | | Fronteira com o PSP: Pix com `txid`, webhook |
| `delivery` | 8087 | PostgreSQL + Redis | Redis: **sem motivo escrito** — ver "Emenda de 24/09/2026" | Atribuição, rodízio, posição do entregador, retorno |
| `conversation` | 8088 | MongoDB | | Canal, conversa, interpretação. Absorve a notificação ao cliente |

Seis bancos PostgreSQL e dois MongoDB. **Nenhum serviço acessa o banco de
outro.**

**Regra da coluna "Por quê":** toda linha que declare persistência além do banco
principal aponta a decisão que a pôs ali. Assim, apagar a razão deixa uma
referência pendurada, que alguém vê, em vez de uma célula órfã, que ninguém vê.

As portas dos serviços que permaneceram **não mudaram**. `settlement` ocupa a
vaga do `inventory` e `conversation` a do `geolocation`, o que mantém o diff
pequeno e não obriga a revisar documentação de serviço que não mudou.

### O que sai, e como

| Serviço | Destino | Volta? |
|---|---|---|
| `inventory` | **Adiado** — marco 10 | Sim, com o controle quantitativo |
| `geolocation` | **Adiado** — marco 11 | Sim, se houver operação que justifique |
| `notification` | **Absorvido** pelo `conversation` | Não |

**Adiado não é cancelado, e a diferença é registrada em código**, não só em
prosa: `estoqueControladoSnapshot` continua congelado no item do pedido (ADR-018)
e o estado `AGUARDANDO_ESTOQUE` continua na máquina de estados, inalcançável e
com teste que prova a inalcançabilidade. Custa um boolean e um caso de teste, e
evita migration de dados no marco 10.

**A absorção da notificação é definitiva.** Sob a premissa nova, o canal com o
cliente é o WhatsApp, que pertence ao `conversation`; o canal com o operador é o
painel, que resolve alerta de pedido novo com consulta periódica; o entregador é
da casa e é avisado pelo operador. Sobram recibos por e-mail — um adaptador de
`CanalPort`, não um serviço. Volta a ser serviço quando houver aplicativo nativo
e push, não antes.

## Consequências

**Positivas**

- A estrutura passa a concordar com o PRD, com as ADRs e com `docs/dominio/`.
  Consultar qualquer um dos quatro dá a mesma resposta.
- Três serviços a menos para compilar, testar, conteinerizar e manter — e três
  pipelines a menos no CI.
- Some do `docker-compose` a infraestrutura que servia ao que saiu: Mosquitto
  (MQTT) e a imagem PostGIS do Postgres.
- O `settlement`, que é a funcionalidade que vende o produto (PRD §1), deixa de
  não ter onde morar.

**Negativas**

- **Apaga-se código que compilava e testava verde.** Nove serviços viravam 86
  tarefas bem-sucedidas; agora são menos. É desconfortável e é o certo — o
  contrário é manter estrutura por custo afundado.
- Reintroduzir `inventory` e `geolocation` nos marcos 10 e 11 custará mais do
  que teria custado mantê-los. Aceito: o custo de manter é pago **todo dia**, em
  build, em leitura e em decisão errada; o de reintroduzir é pago uma vez.
- Dois serviços nascem vazios. `conversation` só ganha código no marco 7 e
  `settlement` no marco 6 — até lá são esqueleto com pipeline. É o preço de a
  estrutura ser honesta antes de ser preenchida.
- **O `delivery-service` fica fino** depois que vínculo e remuneração foram para
  o `merchant` (ADR-022) e a apuração para o `settlement`. A discussão está
  registrada em `docs/dominio/entrega.md` §11.

## Ponto em aberto que esta ADR não fecha

O §5.3 da resposta v1.1 descreve o `payment` como "registro de liquidações". Mas
`docs/dominio/pedido.md` §1 coloca `Liquidacao` **dentro do agregado `Pedido`**,
no `order-service` — porque a invariante 1 do `CLAUDE.md` (nenhuma conclusão sem
liquidação registrada) só é verificável se quem controla a transição também
controla o registro.

As duas leituras não podem estar certas ao mesmo tempo. A proposta é: **o
`order` é dono do registro; o `payment` é a fronteira com o PSP** — gera a
cobrança Pix com `txid`, valida a assinatura do webhook, publica
`LiquidacaoConfirmadaV1`, e no marco 8 trata cartão e Pix online. Isso preserva a
invariante e dá ao `payment` um recorte claro em vez de um espelho do pedido.

**Resolvido pela ADR-023** (23/08/2026): o `order` é dono do registro de
liquidação, o `payment` é a fronteira com o PSP.

## Emenda de 24/09/2026 — o `merchant` não tem Redis

**Por quê.** Esta ADR listava o `merchant` como "PostgreSQL + Redis" sem dizer
para quê. O único motivo escrito no repositório estava no `estabelecimento.md`
v1.1 (22/08): o cache de autorização, "TTL curto no Redis, chave
`usuarioId:estabelecimentoId`". Um dia depois a ADR-011 pôs esse cache em
processo, com Caffeine, e rejeitou por escrito a "cache compartilhada no Redis,
escrita pelo `merchant-service`". O commit que emendou os documentos conforme a
ADR-011 (`751a3ac`) mexeu nesta ADR e esqueceu a coluna. A ADR-043 depois situou
o cache no serviço que pergunta. Desde então, nenhum documento e nenhuma linha de
código usam Redis no `merchant`.

**O que a célula órfã custou.** O starter chegava pelo
`delivery.redis-conventions`, e o indicador de saúde que ele cria fazia o
`/actuator/health` responder `503` sem que ninguém soubesse explicar a
dependência. A C-A desligou o indicador, a C-B precisou justificar a flag, e só
lendo a história se achou que o motivo já tinha morrido. É isso que a coluna
"Por quê" existe para impedir.

**O que muda.** O `merchant` perde o `delivery.redis-conventions` no build, o
bloco `spring.data.redis` e a flag `management.health.redis.enabled: false` no
`application.yml`, e a variável `REDIS_URL` e o `depends_on: redis` no
`docker-compose.yml`. O README acompanha a tabela. O health passa a valer sem
flag nenhuma.

**Pendência que esta emenda não fecha.** O Redis do `order` e do `delivery` também
não tem motivo escrito em lugar nenhum: o `pedido.md` e o `entrega.md` nunca o
citaram, e os caches dos dois são em processo (ADR-033). Os dois vieram do commit
inicial, da arquitetura v1.0 de marketplace. Não são tocados agora. **Gatilho
escrito:** a mesma conferência que tirou o Redis do `merchant` — procurar o motivo
escrito, seguir a história até onde ele morreu ou até onde ele vive —, aplicada a
cada um dos dois.

## Alternativas consideradas

- **Manter os três módulos vazios "já que não atrapalham".** Rejeitada, e é a
  alternativa mais tentadora. Eles atrapalham: aparecem no README, no
  `settings.gradle.kts`, no Compose e no CI, e é dessas fontes que humanos e
  agentes tiram decisão. O incidente do `messaging-conventions` no
  `identity-service` é a prova de que o custo é real e já foi cobrado.
- **Manter só o `inventory`, porque é barato e volta cedo.** Rejeitada: o marco
  10 é o penúltimo, e "volta cedo" é otimismo. O que precisava sobreviver —
  `estoqueControladoSnapshot` e o estado dormente — sobreviveu.
- **Renumerar todas as portas contiguamente.** Rejeitada: obrigaria a revisar
  Compose, Prometheus, gateway e documentação de serviços que não mudaram, em
  troca de estética.
- **Criar `conversation` só no marco 7, quando tiver código.** Rejeitada: é
  justamente o intervalo em que a estrutura mente. O esqueleto existe para
  dizer qual é o sistema, não para refletir o que já foi escrito.
