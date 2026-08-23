# ADR-011 — Autorização comercial: cache em processo, invalidação por evento e fail-closed

**Status:** Aceita — 23/08/2026
**Relacionada:** ADR-012 (roteamento do gateway), ADR-015 (JWT com `NimbusJwtEncoder`)
**Detalha:** `docs/dominio/estabelecimento.md` §2 e §3
**Invariantes do `CLAUDE.md`:** 8 (nenhum serviço lê o banco de outro), 9 (identificador da URL não é confiável)
**Precisa existir antes do marco 1** — o `identity-service` é o primeiro consumidor da porta

## Contexto

Permissão neste sistema pertence ao **vínculo** entre usuário e estabelecimento,
não ao usuário. O mesmo Jorge é administrador na pizzaria e não enxerga nada no
mercadinho. A pergunta que todo serviço faz, em toda requisição, é "o que este
usuário pode fazer **nesta loja**".

Isso cria o objeto mais lido do sistema. E cria três problemas que precisam ser
decididos juntos, porque a resposta de cada um depende das outras:

1. **Onde a resposta é cacheada**, já que consultar o `merchant-service` a cada
   requisição de cada serviço é inviável.
2. **Como a revogação chega rápido**, já que cache e revogação são inimigos
   naturais — a Marli demite o Rafa às 20h e o acesso precisa cair agora.
3. **O que acontece quando o `merchant-service` está fora**, que é a pergunta de
   segurança de verdade.

A ADR-015 já decidiu que o JWT **não carrega permissão nem papel**. Isso não é
revisitado aqui: token com permissão dentro envelhece, e cresce com o número de
vínculos do usuário.

## Decisão

### O cache é local ao consumidor e vive em processo

Cada serviço mantém sua própria cache **em memória**, com Caffeine, atrás do
`AutorizacaoComercialPort`. Não há Redis nesse caminho.

```
Serviço X ──▶ AutorizacaoComercialPort
                  │
                  ├─ acerto na cache em processo  → responde em microssegundos
                  └─ falha → HTTP ao merchant-service → grava na cache
```

**Por que não Redis.** A cache existe para evitar uma ida à rede. Colocá-la no
Redis troca uma ida à rede por outra — mais barata, mas ainda assim uma. E
obrigaria `settlement`, `payment` e `conversation`, que hoje não têm Redis, a
ganhar uma dependência de infraestrutura para resolver um problema que 200 KB de
memória resolvem.

**Por que local e não compartilhada.** Uma chave única no Redis, escrita pelo
`merchant-service` e lida por todos, seria mais eficiente e tornaria a
invalidação um `DELETE` só. Foi rejeitada: transformaria o Redis num canal por
onde um serviço lê dado de outro sem passar pela porta dele, que é exatamente o
que a invariante 8 impede. A cache pertence a quem consulta.

| Parâmetro | Valor | Motivo |
|---|---|---|
| Chave | `(usuarioId, estabelecimentoId)` | A pergunta é sempre esse par |
| TTL de resposta positiva | **60 s** | Ver abaixo |
| TTL de resposta negativa | **10 s** | Também se cacheia "não tem vínculo", senão uma varredura bate direto no `merchant-service`. Curto porque acesso recém-concedido não pode demorar |
| Tamanho máximo | 10 000 entradas por serviço | Teto de memória previsível; descarte por menos-recentemente-usado |
| Timeout da chamada | **300 ms** | Além disso, a requisição do usuário já está lenta |
| Disjuntor | Abre após falhas consecutivas | Evita fila de requisições esperando um serviço morto |

### O TTL de 60 segundos é a janela de tolerância

Escolha deliberada, e o número tem raciocínio:

- **Segurança:** 60 s é o pior caso de acesso indevido depois de uma revogação
  cujo evento se perdeu. Para um sistema onde ninguém movimenta dinheiro sem
  registro, é aceitável.
- **Carga:** um atendente no pico faz alguns cliques por segundo. Com 60 s, cada
  serviço consulta o `merchant-service` uma vez por minuto por usuário, em vez de
  uma vez por clique. Reduz a carga em mais de 99%.
- **Disponibilidade:** e aqui está o ponto que costuma passar batido — **o TTL
  também é a tolerância a queda**. Se o `merchant-service` cair por 40 segundos,
  a maioria das requisições nem percebe, porque a cache ainda responde. Não é
  preciso inventar um "modo degradado" separado: ele já existe, e dura exatamente
  o TTL.

### Invalidação por evento é o caminho rápido; o TTL é a rede de segurança

O `merchant-service` publica `VinculoAlteradoV1` a cada criação, alteração,
suspensão ou remoção de membro. Cada serviço consome e **remove a entrada
correspondente** da sua cache.

```
Marli remove o Rafa
        │
merchant-service  ──▶ VinculoAlteradoV1 (fanout)
        │
        ├──▶ order-service     evict(rafa, pizzaria)
        ├──▶ catalog-service   evict(rafa, pizzaria)
        └──▶ …                 cada um na sua cache
```

Efeito em segundos. O TTL cobre o caso do evento perdido — e evento **se perde**,
por isso as duas coisas existem.

**Cada instância precisa receber o evento.** Cache em processo com N instâncias
significa N caches. O evento vai para um *exchange* fanout, e cada instância se
liga a ele com uma **fila exclusiva e temporária**, não a uma fila compartilhada
— fila compartilhada entrega para uma instância só, e as outras N−1 ficam com
dado velho até o TTL.

No MVP há uma instância por serviço e isso é invisível. Está escrito aqui porque
é a primeira coisa a quebrar quando houver duas, e a falha é silenciosa.

### Indisponível é negado. Sem exceção, sem janela de graça

Cache expirada e `merchant-service` fora → **403**. Não se serve entrada vencida.

Considerei uma janela de graça — servir dado velho por alguns minutos durante uma
queda — e rejeitei. Ela inverte o modo de falha: em vez de "ninguém entra", vira
"todo mundo continua entrando com as permissões antigas", e a revogação para de
funcionar exatamente quando o sistema está instável. Fail-closed que abre sob
pressão não é fail-closed.

**O custo é real e assumido:** se o `merchant-service` cair por mais de um
minuto, a plataforma inteira para de autorizar. A resposta a isso é
disponibilidade — réplicas, health check, alerta — não relaxar a regra.

### O 403 é indistinguível de "não existe"

Sem vínculo ativo, com vínculo mas sem a permissão, ou estabelecimento
inexistente: **a mesma resposta**. Código diferente por caso transforma o erro
num scanner de estabelecimentos (invariante 9 e M7 de `estabelecimento.md`).

O corpo é `ProblemDetail` sem detalhe: "sem acesso a este estabelecimento".

### O que fica do lado de quem chama

```java
public interface AutorizacaoComercialPort {
    /** Vazio quando não há vínculo ativo — nunca nulo, nunca exceção de "não achei". */
    Optional<ContextoDeAcesso> contexto(UUID usuarioId, UUID estabelecimentoId);
}
```

O adaptador é quem cacheia. O caso de uso não sabe que existe cache, não sabe que
existe HTTP, e testa contra um duplo em memória.

## Consequências

**Positivas**

- Zero infraestrutura nova. Nenhum serviço ganha Redis por causa disto.
- O caminho quente não sai do processo: acerto de cache é microssegundos.
- Revogação vale em segundos pelo evento, e no pior caso em 60 s.
- A tolerância a queda curta sai de graça, sem modo degradado inventado.
- A cache pertence a quem consulta — nenhum serviço lê dado de outro por fora da
  porta.

**Negativas**

- **N caches significam N consultas a frio.** Oito serviços aquecendo
  independentemente batem mais no `merchant-service` do que uma cache
  compartilhada bateria. Aceito: acontece uma vez por minuto por par.
- **Fila exclusiva por instância é uma armadilha esperando.** Quem escalar para
  duas instâncias e usar fila compartilhada terá metade do sistema com permissão
  velha, sem erro nenhum aparecendo. Precisa de teste e de linha no `CLAUDE.md`.
- **Queda do `merchant-service` para tudo** depois de 60 s. É o preço do
  fail-closed, e está dito em voz alta.
- Cache em memória some no restart. Irrelevante: reaquece em segundos.
- **A ADR-015 fica com uma consequência que não estava explícita:** como o token
  não carrega permissão, *toda* requisição paga uma checagem. Esta ADR é o que
  torna esse custo aceitável.

## Alternativas consideradas

- **Permissão dentro do JWT.** Elimina a checagem inteira. Rejeitada na ADR-015 e
  não reaberta: revogação passaria a esperar a expiração do token, e o token
  cresceria com o número de vínculos, vazando a lista de lojas do usuário em toda
  requisição.
- **Cache compartilhada no Redis, escrita pelo `merchant-service`.** Mais
  eficiente, invalidação num `DELETE` só. Rejeitada por criar um caminho de
  leitura de dado alheio fora da porta — e porque um defeito de escrita passaria a
  contaminar os oito serviços de uma vez, em vez de um.
- **Janela de graça servindo cache vencida durante queda.** Rejeitada: inverte o
  modo de falha justamente sob instabilidade.
- **Sem cache, consultando sempre.** Rejeitada pelo óbvio, mas vale registrar o
  que ela teria de bom: revogação instantânea e nenhuma das armadilhas de
  invalidação acima. É o desenho correto se um dia a carga permitir.
- **TTL de 5 minutos.** Reduziria mais a carga. Rejeitada: cinco minutos de
  acesso indevido depois de uma demissão é longo demais para um sistema que
  registra dinheiro.

## Emenda que esta decisão provoca

`docs/dominio/estabelecimento.md` §3 e a tabela de armadilhas do `CLAUDE.md`
dizem "cache curto **no Redis**". Estava errado — ou melhor, estava indeciso, e
esta ADR decide. Os dois textos precisam passar a dizer **cache em processo**.
