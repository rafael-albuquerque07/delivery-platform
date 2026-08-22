# ADR-021 — Catálogo de serviços do MVP

**Status:** Aceita — 21/08/2026
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

| Serviço | Porta | Persistência | O que é dele |
|---|---:|---|---|
| `gateway` | 8080 | — | Roteamento, CORS, limite de taxa |
| `identity` | 8081 | PostgreSQL | Conta, autenticação, emissão de JWT (ADR-015) |
| `merchant` | 8082 | PostgreSQL + Redis | Estabelecimento, equipe, permissões, áreas e taxas, vínculo de entregador |
| `catalog` | 8083 | MongoDB + Redis | Produto, opções, disponibilidade qualitativa, cotação |
| `settlement` | 8084 | PostgreSQL | Jornada, custódia, divergência, extrato, fechamento |
| `order` | 8085 | PostgreSQL + Redis | Pedido, valores, liquidação registrada, Saga |
| `payment` | 8086 | PostgreSQL | Fronteira com o PSP: Pix com `txid`, webhook |
| `delivery` | 8087 | PostgreSQL + Redis | Atribuição, rodízio, posição do entregador, retorno |
| `conversation` | 8088 | MongoDB | Canal, conversa, interpretação. Absorve a notificação ao cliente |

Seis bancos PostgreSQL e dois MongoDB. **Nenhum serviço acessa o banco de
outro.**

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
prosa: `stockControlledSnapshot` continua congelado no item do pedido (ADR-018)
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

**Precisa virar ADR própria antes do marco 4**, que é quando o `payment` ganha
código. Até lá o módulo existe no esqueleto sem que a fronteira esteja decidida.

## Alternativas consideradas

- **Manter os três módulos vazios "já que não atrapalham".** Rejeitada, e é a
  alternativa mais tentadora. Eles atrapalham: aparecem no README, no
  `settings.gradle.kts`, no Compose e no CI, e é dessas fontes que humanos e
  agentes tiram decisão. O incidente do `messaging-conventions` no
  `identity-service` é a prova de que o custo é real e já foi cobrado.
- **Manter só o `inventory`, porque é barato e volta cedo.** Rejeitada: o marco
  10 é o penúltimo, e "volta cedo" é otimismo. O que precisava sobreviver —
  `stockControlledSnapshot` e o estado dormente — sobreviveu.
- **Renumerar todas as portas contiguamente.** Rejeitada: obrigaria a revisar
  Compose, Prometheus, gateway e documentação de serviços que não mudaram, em
  troca de estética.
- **Criar `conversation` só no marco 7, quando tiver código.** Rejeitada: é
  justamente o intervalo em que a estrutura mente. O esqueleto existe para
  dizer qual é o sistema, não para refletir o que já foi escrito.
