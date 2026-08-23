# ADR-002 — Um banco por serviço, sem exceção

**Status:** Aceita — 16/08/2026 · **formalizada em 23/08/2026**
**Relacionada:** ADR-001 (monorepo), ADR-010 (Saga), ADR-014 (sem H2), ADR-021 (catálogo de serviços)
**Invariantes do `CLAUDE.md`:** 7 (outbox e idempotência), 8 (nenhum serviço lê o banco de outro)
**Em vigor desde o primeiro commit** — esta ADR registra o porquê, que faltava

## Contexto

O padrão acidental de todo sistema que se divide em serviços é manter um banco só
com vários esquemas. Ninguém decide isso: é o que sobra quando não se decide.

E funciona, por um tempo. Até o dia em que um relatório precisa juntar pedido com
cardápio e alguém escreve um `JOIN` entre esquemas. A partir daí os dois serviços
compartilham modelo de dados, publicação vira ato coordenado, e o que existe é um
monólito distribuído — com a latência de rede da arquitetura de microsserviços e
nenhuma das vantagens.

O `JOIN` entre fronteiras é o momento exato em que a arquitetura morre, e ele
sempre parece razoável quando é escrito.

## Decisão

**Um banco lógico por serviço. Nenhum serviço acessa o banco de outro. Nunca.**

Integração é por **API** (síncrona, com timeout) ou **evento** (assíncrono, com
outbox e idempotência).

Seis PostgreSQL e dois MongoDB (ADR-021):

| Serviço | Banco |
|---|---|
| `identity` | `identity_db` |
| `merchant` | `merchant_db` |
| `settlement` | `settlement_db` |
| `order` | `order_db` |
| `payment` | `payment_db` |
| `delivery` | `delivery_db` |
| `catalog` | `catalog_db` (MongoDB) |
| `conversation` | `conversation_db` (MongoDB) |

Em desenvolvimento, uma instância do PostgreSQL com seis bancos lógicos — não
seis instâncias. O isolamento é lógico, e é suficiente: o que se está protegendo
é a fronteira de modelo, não a de máquina. Em produção a topologia pode separar
fisicamente sem que nada no código mude.

### O que isto obriga, e que é a maior parte da complexidade deste sistema

Metade do desenho existe por causa desta decisão. Vale dizer em voz alta, porque
quem lê a Saga e o Outbox sem esta ADR acha que é sofisticação gratuita:

| Consequência | Onde vive |
|---|---|
| Sem transação distribuída → **Saga** com compensação e pivô | ADR-010 |
| Sem gravar dado e publicar evento atomicamente → **Outbox** | Invariante 7 |
| Mensagem se repete → **`processed_messages`** | Invariante 7 |
| Sem `JOIN` entre serviços → **duplicação deliberada** | `liquidacao.md` §2 |
| Permissão em outro serviço → **porta com cache e fail-closed** | ADR-011 |

**A duplicação é intencional e tem direção.** O `settlement` guarda sua própria
cópia da liquidação, alimentada por evento, e nunca lê a tabela do `order`. Uma
cópia com um dono e um sentido é dado replicado; duas cópias que se escrevem
mutuamente é corrupção esperando data.

## Consequências

**Positivas**

- Cada serviço evolui o próprio esquema sem coordenar migração com ninguém.
- Escolha de tecnologia por serviço: MongoDB onde a forma ajuda, PostgreSQL onde
  a transação manda (ADR-017).
- A fronteira é **física**, não convencional. Não existe `JOIN` para escrever
  errado, porque não existe conexão.
- Falha de banco fica contida num serviço.

**Negativas**

- **Não há `JOIN` entre serviços, e isso dói.** Toda tela que mostra dados de dois
  domínios — o painel do comerciante é o caso — precisa compor na aplicação ou
  manter um modelo de leitura. É o custo mais alto desta decisão e o mais
  frequente.
- **Consistência é eventual.** O `settlement` sabe da entrega quando o evento
  chega, não quando ela acontece. Aceito e visível: o fechamento apura o que
  chegou, e mensagem perdida vira divergência, não silêncio.
- **Metade da complexidade do sistema vem daqui.** Saga, outbox, idempotência,
  duplicação — nada disso existiria com um banco só. Quem revisar esta ADR e
  discordar dela está discordando de todo esse conjunto, e é bom que a conta
  esteja num lugar só.
- **Em desenvolvimento o isolamento é lógico.** Uma credencial só, seis bancos.
  Nada impede tecnicamente um `JOIN` entre bancos da mesma instância — só a
  invariante 8 e a revisão impedem. Em produção, credencial por serviço fecha
  isso de verdade, e essa divergência entre dev e produção está aqui declarada.
- **Seis pools de conexão numa instância.** Por isso `maximum-pool-size: 5` por
  serviço (ADR-014) — o padrão de 10 estoura o `max_connections`.

## Alternativas consideradas

- **Um banco, um esquema por serviço.** A alternativa realista, e a que quase
  todo projeto acaba adotando. Mais simples de operar, permite `JOIN` quando
  aperta — e é exatamente por isso que foi rejeitada. A fronteira vira convenção,
  e o primeiro `JOIN` entre esquemas ganha a discussão porque resolve o problema
  daquele dia. Não existe meio-termo estável aqui.
- **Um banco compartilhado, sem separação.** Rejeitada: é o monólito, e se for
  para ser monólito é melhor ser um monólito modular, sem a latência de rede.
- **Banco por serviço com um esquema de leitura compartilhado** para relatório.
  Tentadora, e é praticamente a ideia de CQRS mal aplicada. Rejeitada: quem
  escreve nesse esquema? Se for cada serviço, voltou o acoplamento; se for um
  processo à parte, é um serviço novo que ninguém decidiu criar. Se o relatório
  doer de verdade, a resposta é um modelo de leitura com dono explícito, em ADR
  própria.
- **Instâncias fisicamente separadas em desenvolvimento.** Rejeitada: seis vezes
  a memória para reproduzir um isolamento que o código não enxerga.
