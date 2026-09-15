# Domínio — Estabelecimento, equipe e áreas

**Serviço:** `merchant-service` · **Status:** vigente (v1.4, 14/09/2026)
**Fontes:** PRD §5 (P2, P3, P5), PRD §6 E1, E2 e E6.1, ADR-004, ADR-011, ADR-012, ADR-020, ADR-022
**Invariantes do `CLAUDE.md` que este documento detalha:** 2, 8, 9

Este é o serviço do qual todos os outros dependem para saber **se aquela pessoa
pode fazer aquilo naquela loja**. É também onde mora a configuração que
determina o comportamento do sistema inteiro: o que a loja aceita, quando
atende, onde entrega e por quanto.

---

## 1. Agregados

Três raízes, não uma. A separação não é estética — é sobre o que muda junto e o
que é lido em que frequência.

```
Estabelecimento  (raiz)
├── identificacao       id, nome, documento, telefone, enderecoTextual, bairro, fusoHorario
├── operacao            tipoDeOperacao, metodosPorModalidade,
│                       descontoDeRetirada, pedidoMinimoPorModalidade
├── politicaDeTroco     fundoMaximoDeTroco, aceitaPedidoSemTrocoDisponivel
├── disponibilidade     horarioDeFuncionamento, pausa
├── politicas           responsabilizaEntregadorPorNaoLiquidado
└── AreaDeEntrega  [n]  bairro/CEP, taxa, ativa

Membro  (raiz)          vínculo usuário ↔ estabelecimento
├── usuarioId, estabelecimentoId
├── papel               ADMINISTRADOR | COLABORADOR
├── permissoes    [n]   concedidas item a item
└── estado              ATIVO | SUSPENSO | REMOVIDO

VinculoEntregador  (raiz)
├── entregadorId, estabelecimentoId
├── modeloDeRemuneracao, valorDiaria, comissaoPorEntrega, taxaFixaPorEntrega
└── ativo
```

**O endereço da loja é texto, não estrutura.** `enderecoTextual` e `bairro`, e
nada mais — é a representação que a ADR-013 escolheu para o sistema inteiro,
"endereço textual e nome de bairro" no lugar de coordenada. Nenhuma regra deste
serviço lê pedaço de endereço: as faixas de CEP da ADR-020 casam com o endereço
do **cliente**, e vivem na `AreaDeEntrega`. O bairro fica separado porque é a
unidade do modelo de entrega, e o da própria loja é o ponto de partida natural
do cadastro das áreas.

**Por que `AreaDeEntrega` fica dentro do `Estabelecimento`.** São dezenas de
linhas, editadas juntas na mesma tela, e a invariante de não-sobreposição de CEP
(§5) precisa enxergar todas ao mesmo tempo. Fora do agregado, essa checagem vira
consulta concorrente e a sobreposição entra pelo vão.

**Por que `Membro` é raiz própria.** É o objeto mais lido do sistema — toda
requisição de todo serviço passa por ele. Como parte do agregado
`Estabelecimento`, cada checagem de permissão carregaria a loja inteira, áreas
inclusive. E o ciclo de vida é outro: um membro é convidado, aceita, é suspenso,
sem que o estabelecimento mude.

**Por que `VinculoEntregador` é raiz própria.** Mesmo motivo do `Membro`, mais
um: a jornada congela um `vinculoSnapshot` na abertura (ADR-022), e congelar
exige uma coisa com identidade própria e histórico próprio.

**Não há `CONVIDADO`, e a ausência é decisão.** A pendência do convite mora no
`Convite`, que é raiz própria e sabe expirar — e o aceite cria o `Membro` já
`ATIVO`. Um estado `CONVIDADO` aqui seria um segundo lugar dizendo a mesma
coisa, que teria de concordar com `Convite.PENDENTE` para sempre. `REMOVIDO`
não apaga a linha: o vínculo é registro de quem teve acesso à loja e quando, e
o `UNIQUE (usuario_id, estabelecimento_id)` faz a recontratação reusar o mesmo
vínculo. Quem **sai** e quem **é removido** terminam no mesmo `REMOVIDO`: a
diferença entre as duas coisas pertence ao `motivo` do `VinculoAlteradoV1`, não
a um quarto estado que todo filtro do sistema teria de tratar para sempre.

---

## 2. Autorização contextual — o conceito central

> **Permissão não pertence ao usuário. Pertence ao vínculo entre o usuário e o
> estabelecimento.**

Jorge é administrador da pizzaria da Marli e, no mercadinho do Sérgio, é
entregador sem acesso a nada. Não são dois usuários — é o mesmo usuário com dois
`Membro` diferentes. A pergunta que o sistema responde nunca é "o que este
usuário pode fazer", é **"o que este usuário pode fazer nesta loja"**.

### O que o JWT carrega, e o que não carrega

| Carrega | Não carrega |
|---|---|
| `sub` — identidade do usuário | Permissões |
| Validade, emissor, assinatura | Papel |
| | Lista de estabelecimentos |

A lista completa de claims está na **ADR-015**, emendada em 26/08/2026
justamente por causa desta tabela: a versão original incluía `roles` e `scope`,
escritos antes de a ADR-011 existir.

Colocar permissão no token quebra de duas formas. Primeiro, ela fica **velha**:
a Marli revoga o acesso do Rafa às 20h e o token dele continua valendo até
expirar. Segundo, ela é **por estabelecimento** — um token que carregasse todas
as permissões de todas as lojas cresceria com o número de vínculos e vazaria a
lista de lojas do usuário em cada requisição.

A permissão é resolvida **por requisição**, contra o `merchant-service`, com
cache curto.

### Permissões

Exatamente as de H2.1, mais uma:

```
VER_PRODUTO · CRIAR_PRODUTO · ALTERAR_PRODUTO · DESATIVAR_PRODUTO
VER_PEDIDO  · ALTERAR_STATUS
VER_VENDAS  · VER_ENTREGA
GERENCIAR_EQUIPE
GERENCIAR_JORNADA
```

> **`GERENCIAR_JORNADA` é acréscimo ao PRD**, e está marcado como tal. Abrir
> turno, registrar adiantamento e fechar jornada mexem em dinheiro e não cabem
> em nenhuma das permissões de H2.1 — `VER_VENDAS` é leitura, `VER_ENTREGA` é
> outra coisa. `docs/dominio/liquidacao.md` §10 deixou essa pergunta em aberto; é
> aqui que ela se fecha. Se a decisão for que apenas administrador abre jornada,
> a permissão vira um preset e não um item — mas continua precisando existir.

**`papel` não é uma lista de permissões.** Tem só dois valores e uma função:
`ADMINISTRADOR` é quem `GERENCIAR_EQUIPE` **não** pode tocar. Toda autorização
real é feita pela lista de permissões; o papel existe para impedir escalada.

Presets — "Atendente", "Gerente" — são conveniência de tela que expande para um
conjunto de permissões no momento do convite. **Não são armazenados**, e nenhuma
regra de negócio pergunta por eles. Preset armazenado vira papel de fato, e aí a
matriz item a item que o PRD pediu deixa de existir na prática.

### Escalada de privilégio

As três regras de H2.2, como invariantes verificáveis:

| # | Regra | Verificada em |
|---|---|---|
| A1 | Quem tem `GERENCIAR_EQUIPE` administra `COLABORADOR`, nunca `ADMINISTRADOR` | Convite, alteração, suspensão, remoção |
| A2 | Ninguém concede permissão que não possui — `concedidas ⊆ próprias` | Convite **e** aceite |
| A3 | Um estabelecimento sempre mantém ≥ 1 `ADMINISTRADOR` `ATIVO` | Saída da própria loja — nas demais, decorre de A1 |

**A1, lida por inteiro.** "Nunca alcança `ADMINISTRADOR`" é sobre a
**permissão**, não sobre quem a tem: `GERENCIAR_EQUIPE` sozinha não alcança um
administrador, e `GERENCIAR_EQUIPE` mais o papel de administrador, sim. Ao pé
da letra, a frase tornaria todo administrador impossível de remover por quem
quer que fosse — e "o administrador saiu da empresa" é o caso (c) da ADR-029,
que existe porque isso acontece. O gerente que só tem a permissão continua
barrado, que é a escalada que H2.2 nomeia.

**A3 tem dois regimes.** Nas seis operações administrativas ela *decorre* de
A1: mexer num administrador exige ser um administrador ativo, ninguém administra
o próprio vínculo, logo todo alvo administrador tem pelo menos um par e a
contagem nunca chega a zero. A demonstração está em
`Equipe.administradoresAtivos()`.

Em **sair da própria loja** ela é *verificada*, e é o único lugar do sistema que
a verifica — porque é a única operação sem autor separado do alvo, e portanto a
única onde a premissa "ninguém se administra" não vale. Quem é o último
administrador ativo promove alguém antes de sair.

Nos dois regimes, a garantia depende, sob concorrência, do **cadeado na linha do
estabelecimento** que `MembroRepositorio.equipeParaAlteracao` toma antes de
contar: sem ele, duas saídas simultâneas passam pela checagem — as duas — e a
loja fica órfã.

A2 é verificada **duas vezes**, e a segunda é a que se esquece: entre o convite e
o aceite podem passar dias, e o convidante pode ter perdido a permissão que
estava concedendo. Validar só na emissão deixa um convite virar um privilégio
que ninguém mais tem autoridade para dar.

A3 é o que impede a loja de ficar órfã. A operação que a violaria falha com
mensagem explícita — nunca com erro genérico, nunca em silêncio.

**A3 garante que o administrador existe, não que alguém o alcança.** Telefone
trocado, aparelho perdido, administrador que saiu da empresa — o registro
continua lá e ninguém entra. O caminho de volta é a ADR-029, e ele tem uma regra
que vale repetir aqui: **a recuperação opera sobre o vínculo, nunca sobre a
credencial.** Ela cria ou promove um `Membro` naquele estabelecimento e não toca
em `Usuario` — porque o mesmo usuário pode administrar outras lojas, e uma prova
que valia para uma passaria a valer para todas.

**Revogação também é limitada por A2.** Quem tem `GERENCIAR_EQUIPE` mas não tem
`VER_VENDAS` não pode retirar `VER_VENDAS` de ninguém. Sem essa simetria, um
gerente rebaixa colegas até o conjunto vazio usando uma permissão que ele
próprio não tem.

### Convite

```
Convite
├── estabelecimentoId, telefone ou link
├── permissoesOferecidas  [n]
├── convidadoPor          usuário
├── expiraEm              Instant
└── estado                PENDENTE | ACEITO | CANCELADO
```

O aceite cria o `Membro` com `estado = ATIVO`. Convite expirado não aceita.
Convite nunca concede `papel = ADMINISTRADOR` — promoção é ato separado, feito
por um administrador existente, sobre um membro que já aceitou.

**Não há `EXPIRADO`, e é a mesma decisão que tirou o `CONVIDADO` do `Membro`.**
Expirar não é ato de ninguém: é o relógio passando de `expiraEm`. Guardá-lo como
estado exigiria uma rotina varrendo a tabela, e essa rotina seria ou inexistente
— peça que nunca roda — ou um segundo lugar que precisa concordar com `expiraEm`
para sempre. O domínio deriva: pendente é `estado = PENDENTE` **e** `agora <
expiraEm`. `CANCELADO` fica, porque cancelar é ato de alguém, num instante que
se registra.

**O token autoriza; o telefone endereça.** Quem aceita é quem apresenta o token
— 32 bytes, uso único, sete dias. Conferir que ele é o dono daquele telefone
exigiria perguntar ao `identity-service` qual é o telefone de um `usuarioId`, e
essa porta não existe (ADR-001). Por isso "convidar quem já é da equipe" só é
detectado no aceite, que é onde o `usuarioId` aparece. O telefone fica guardado
como endereço de entrega e registro de para quem o convite foi mandado.

**Quem volta pelo convite volta `COLABORADOR`.** O aceite de quem já teve
vínculo reativa o mesmo registro e o rebaixa — senão um gerente restauraria um
`ADMINISTRADOR` removido sem nenhum administrador na operação, que é escalada
com aparência de gentileza.

---

## 3. Como os outros serviços perguntam

O `merchant-service` expõe uma consulta de autorização. Todos os demais a
consomem por porta, com cache **em processo** (Caffeine) e **política de negar
quando indisponível** — ADR-011, e a armadilha já registrada no `CLAUDE.md`.

```java
public interface AutorizacaoComercialPort {
    /** Vazio quando não há vínculo ativo — nunca nulo, nunca exceção de "não achei". */
    Optional<ContextoDeAcesso> contexto(UUID usuarioId, UUID estabelecimentoId);
}

record ContextoDeAcesso(UUID usuarioId, UUID estabelecimentoId,
                        Papel papel, Set<Permissao> permissoes) {}
```

| Aspecto | Regra | Por quê |
|---|---|---|
| Cache | **Em processo** (Caffeine), 60 s para resposta positiva e 10 s para negativa, chave `(usuarioId, estabelecimentoId)` — ADR-011 | Toda requisição de todo serviço passa aqui, e a cache existe para não sair do processo |
| Invalidação | Evento `VinculoAlteradoV1` remove a entrada em cada instância | O TTL é a rede de segurança, o evento é o caminho rápido |
| Queda do serviço | Negar. O TTL de 60 s **é** a janela de tolerância — não há modo degradado separado | Fail-closed que abre sob pressão não é fail-closed |
| Ausência de vínculo | `Optional.empty()` → 403 com mensagem clara | Nunca 404: o 404 confirma ou nega a existência da loja |

### O que a operação da loja responde

```java
public interface OperacaoDoEstabelecimentoPort {
    /** Vazio quando o estabelecimento não existe — nunca nulo. */
    Optional<OperacaoAtual> operacao(UUID estabelecimentoId);
}

record OperacaoAtual(boolean aberto,
                     TipoDeOperacao tipoDeOperacao,
                     int maxEntregasSimultaneas) {}
```

`aberto` já considera horário **e** pausa: quem pergunta não recompõe a regra
das faixas que cruzam a meia-noite. Ela mora aqui, num lugar só.

Mesma cache do `AutorizacaoComercialPort` — 60 s positivo, 10 s negativo — e
**falha fechada**. Invalidada por **dois** eventos, porque a resposta compõe
dois assuntos: `ExpedienteAlteradoV1` muda `aberto`; `ConfiguracaoOperacionalAlteradaV1`
muda `tipoDeOperacao` e `maxEntregasSimultaneas`. Perder qualquer um dos dois
deixa a resposta velha até o TTL.

Três consumidores: o `order` para a guarda de T02 e para I8, e o `delivery`
para o rodízio.

O aceite manual fora do horário continua valendo (§4): a guarda de T02 é "loja
aberta **ou** aceite manual explícito", e o segundo ramo não pergunta nada.

**Fail-closed, e o preço disso.** Se o `merchant-service` cair, nenhum outro
serviço autoriza nada, e a plataforma inteira para. É consequência assumida, não
descuido: a alternativa — liberar em caso de dúvida — significa que uma queda
vira acesso irrestrito aos dados de todas as lojas. Mitigação é disponibilidade
(réplicas, cache com TTL que sobrevive a queda curta), não relaxamento da regra.
Os detalhes de TTL, invalidação e janela de tolerância estão na **ADR-011**.

### As três portas, e a política de cache de cada uma

| Porta | Cache | Por quê |
|---|---|---|
| `AutorizacaoComercialPort` | 60 s positivo · 10 s negativo | ADR-011 |
| `OperacaoDoEstabelecimentoPort` | idem | Resposta usada e descartada |
| `DeliveryQuotePort` (ADR-019, ADR-020) | **nenhum** | A resposta é **congelada** no pedido como `taxaSnapshot` — cache aqui grava dado velho para sempre. ADR-034 |

**Porta sem essa coluna preenchida não está documentada.** O padrão das três
decisões anteriores é "consulte e guarde"; a terceira linha existe para que
ninguém o aplique por analogia onde ele corrompe.

### Identificador na URL nunca é confiável

Invariante 9 do `CLAUDE.md`, e é aqui que ela se materializa. As rotas vivem sob
`/api/v1/merchants/{estabelecimentoId}/...` (ADR-012), e o
`{estabelecimentoId}` da URL é **entrada do atacante**, não contexto confiável.

Toda requisição confronta o `sub` do token com o identificador da URL através da
porta acima. Sem vínculo ativo, 403 — e a resposta é idêntica para "loja não
existe" e "você não tem acesso", senão o código de erro vira um scanner de
estabelecimentos.

Nenhuma consulta filtra por `estabelecimentoId` vindo do corpo da requisição. O
filtro usa o identificador **já validado** contra o vínculo.

**Como isso ficou no código (C-A).** O `estabelecimentoId` da URL entra no caso
de uso junto com o `sub` do token, e a primeira coisa que acontece é a busca do
vínculo pelo **par** (usuário, loja). Quem não tem vínculo e quem pediu uma loja
inexistente caem na mesma consulta vazia, e recebem o mesmo 403 com o mesmo
corpo — há um teste que compara os dois corpos.

**A porta pela qual os outros serviços perguntam ainda não existe**, e é
deliberado: nenhum dos seis tem código, e uma porta sem consumidor é uma peça que
nunca roda com cara de pronta. Dentro do `merchant`, autorização é consulta ao
próprio repositório. O cache de 60 s da ADR-011 acompanha a porta — e, antes
dela, o `VinculoAlteradoV1` que o invalida: cache sem invalidação é permissão
revogada continuando a valer, em silêncio.

---

## 4. Configuração de operação

### Tipo de operação

| Valor | Subestados de preparo liberados |
|---|---|
| `PRODUCAO` | `NA_FILA` · `EM_PRODUCAO` · `FINALIZANDO` |
| `SEPARACAO` | `SEPARANDO` · `CONFERIDO` · `EMBALADO` |
| `MISTA` | os seis |

É o `order-service` que valida o subestado (I8 em `pedido.md`), consultando este
valor. Alterar o tipo de operação **não** altera pedidos em andamento — um
pedido em `EM_PRODUCAO` continua válido mesmo se a loja virar `SEPARACAO`.

### Modalidades e métodos aceitos

Métodos aceitos são uma **matriz**, não uma lista. Aceitar Pix no balcão e
recusar Pix na entrega é configuração comum, e uma lista única não a expressa.

```
metodosPorModalidade : Map<Modalidade, Set<MetodoPagamento>>

ENTREGA  → { DINHEIRO, CARTAO, PIX }
RETIRADA → { DINHEIRO, CARTAO }
```

No fechamento do pedido, `metodoDeclarado` tem que pertencer ao conjunto da
`modalidade` escolhida. Validação **no momento do pedido**, não congelada: a
matriz descreve o que a loja aceita hoje, e um pedido antigo já registrou o que
foi de fato declarado e liquidado.

**Não existe campo `modalidadesAceitas`.** A loja aceita a modalidade se há
entrada para ela no mapa: `modalidadesAceitas` é `metodosPorModalidade.keySet()`,
derivado. Os dois campos existiam lado a lado, e as chaves de um eram os
elementos do outro — dois campos que precisam ser iguais são uma invariante a
testar para sempre e a violar por descuido, que é o mesmo argumento com que a
emenda de 26/08 colapsou `deliveryFee` em `taxaSnapshot`.

Mapa vazio é inválido: uma loja que não entrega nem deixa retirar não opera. E
modalidade com conjunto de métodos vazio também é — aceitar entrega sem aceitar
forma nenhuma de pagar é pedido que entra e não fecha.

### Pedido mínimo

```
pedidoMinimoPorModalidade : Map<Modalidade, Money>

ENTREGA  → R$ 25,00
RETIRADA → R$ 0,00        zero = sem mínimo
```

Matriz pelo mesmo motivo dos métodos de pagamento: mínimo para entrega e nenhum
para retirada é a configuração comum, e o valor único não a expressa. **Zero é
valor válido e significa "sem mínimo"** — não é ausência de configuração.

E **a ausência não é permitida**: toda modalidade aceita tem entrada, e nenhuma
modalidade não aceita tem. Sem a primeira metade, ausência e zero se confundem —
o erro que M11 nomeia do outro lado, em "ausência de área ≠ taxa zero". Sem a
segunda, sobra configuração morta que vira ativa sozinha no dia em que alguém
aceitar a modalidade.

**O mínimo é sobre `subtotalDosItens`, nunca sobre o `total`** (ADR-028). Sobre o
total, a taxa de entrega ajudaria a atingi-lo: um mínimo de R$ 25 com taxa de
R$ 9 viraria um mínimo de R$ 16 de comida — e de R$ 13 num bairro mais caro. O
critério passaria a depender de onde o cliente mora.

A recusa diz **quanto falta**, como a do troco: "O pedido mínimo para entrega é
R$ 25,00. Faltam R$ 3,00." E **não há aceite manual por cima do mínimo** —
diferente do aceite fora do horário, porque `T01` é a transição que cria o
pedido, e uma exceção aqui deixaria o pedido nascer violando a guarda que o
criou.

### Política de troco

```
fundoMaximoDeTroco              Money
aceitaPedidoSemTrocoDisponivel  boolean
```

Fecha a ponta solta que `pedido.md` I4 deixou. No fechamento do pedido, com
`metodoDeclarado = DINHEIRO`:

```
trocoDevido = trocoPara − total

trocoDevido > fundoMaximoDeTroco  ∧  ¬aceitaPedidoSemTrocoDisponivel
    → pedido RECUSADO, com a mensagem de quanto de troco a loja consegue dar

trocoDevido > fundoMaximoDeTroco  ∧  aceitaPedidoSemTrocoDisponivel
    → pedido ACEITO, com aviso ao cliente e ao entregador
```

A recusa precisa dizer o número. "Não conseguimos troco para R$ 100, o máximo
hoje é R$ 50" resolve; "pedido inválido" faz o cliente ir embora.

### Horário e pausa

> **O expediente é a unidade.** Um expediente é uma abertura até o fechamento
> correspondente — a pizzaria que abre às 18h e fecha às 2h tem **um**
> expediente, não dois dias. É o que `liquidacao.md` fecha, o que `catalogo.md`
> usa como `expedienteDeReferencia` para reativar o que acabou, e o que o
> `ExpedienteAlteradoV1` comunica quando muda. Pausar e retomar acontecem
> **dentro** de um expediente e não abrem outro.

```
horarioDeFuncionamento : Map<DiaDaSemana, List<Faixa>>   Faixa = (inicio, fim)
```

Múltiplas faixas por dia, porque almoço e jantar são turnos separados e a loja
fecha no meio.

**Todo horário aqui é civil, no fuso do estabelecimento.** `fusoHorario` é um
identificador IANA — `America/Sao_Paulo`, `America/Manaus` —, obrigatório e
validado contra o conjunto de zonas brasileiras (ADR-025). "A loja está aberta
agora?" é sempre um instante convertido com essa zona; nunca a hora do
servidor.

**Faixa que cruza a meia-noite é o bug clássico.** A pizzaria abre 18h e fecha
2h. Regra: uma faixa com `fim < inicio` pertence ao dia de **início** e se
estende ao dia seguinte. Terça 18:00–02:00 significa "de terça às 18h até
quarta às 2h" — e às 00:30 de quarta a loja está aberta **pela faixa de terça**.
Testar isto com um pedido à 01:00 é obrigatório.

Quatro detalhes que a implementação precisou fixar e este documento não dizia:

- **Início inclusivo, fim exclusivo.** Às 18:00 em ponto abriu; às 02:00 em
  ponto já fechou. Sem isso, 18:00–02:00 e 02:00–06:00 se sobreporiam num
  minuto, e o pedido daquele minuto passaria por duas regras.
- **Faixa com `inicio == fim` é recusada** — não há como saber se é turno vazio
  ou vinte e quatro horas.
- **Horário vazio é válido** e significa "nunca abre por horário". É o estado de
  uma loja recém-cadastrada; o aceite manual de T02 continua sendo o caminho
  para atender assim mesmo.
- **Faixas que se sobrepõem são permitidas** — "aberta" é um OU sobre as faixas,
  então sobrepor é redundância, não contradição. Faixa **idêntica** repetida no
  mesmo dia é recusada, porque não significa nada.

### O dia operacional

```
HORA_DE_CORTE = 04:00        constante de domínio, igual para todas as lojas

diaOperacional(instante, fuso):
    local = instante convertido para fuso
    se local.hora < 04:00  →  local.data − 1 dia
    senão                  →  local.data
```

Uma venda à 01:30 de domingo pertence ao dia operacional de **sábado**, que é
como o comerciante fala e como ele confere. É o critério que o `catalog` usa
para o expediente de referência e o `settlement` para o total do dia — ADR-025.

Pausa é outra coisa:

```
pausa : { ativa, pausadoAte: Instant | INDEFINIDA, motivo }
```

`pausadoAte` é `Instant` (UTC), como todo carimbo de tempo persistido —
ADR-025. "Pausado até as 21h" é hora civil só na tela; o que se grava é o
instante.

| | Fechado por horário | Pausado |
|---|---|---|
| Origem | Configuração | Ação manual imediata (H1.2) |
| Duração | Previsível | Até reabrir ou até `pausadoAte` |
| Efeito em pedido novo | Bloqueia | Bloqueia |
| Efeito em pedido em andamento | **Nenhum** | **Nenhum** |

Pausar não cancela nada. A cozinha atolou, param de entrar pedidos novos, e os
trinta que já estão na fila seguem seu curso. Um sistema que cancelasse ao
pausar seria abandonado no primeiro sábado.

**Motivo é obrigatório quando a pausa está ativa**, e **pausa vencida não precisa
de faxina**: uma pausa com `pausadoAte` no passado continua registrada como o
comerciante a deixou, e a pergunta "está pausada agora?" devolve não. O registro
diz o que foi feito; o cálculo diz o que vale agora — nenhuma rotina passa
limpando, e nenhum campo fica mentindo.

**Aceite manual fora do horário.** T02 em `pedido.md` permite aceitar com a loja
fechada, desde que seja ato explícito de alguém com `ALTERAR_STATUS`. O cliente
que ligou às 2h05 e o dono resolveu atender não deve esbarrar numa regra.

---

## 5. Áreas de entrega

Modelo decidido na ADR-020. As regras de integridade:

```
AreaDeEntrega
├── nome                    "Boa Viagem"        — como o cliente vê
├── identificadorNormalizado                    — derivado, único na loja
├── faixasDeCep       [n]   (inicio, fim)       — opcionais
├── taxa                    Money               — zero é válido
└── ativa                   boolean
```

| # | Invariante | O que quebra sem ela |
|---|---|---|
| E1 | `identificadorNormalizado` único por estabelecimento | "Boa Viagem" e "boa viagem" com taxas diferentes |
| E2 | Faixas de CEP não se sobrepõem dentro da mesma loja | O mesmo endereço resolve para duas taxas |
| E3 | `taxa ≥ 0` | — |
| E4 | Ausência de área ≠ `taxa = 0` | Entregar de graça onde não se entrega |

**M9 e M10 não têm o mesmo escopo, e a diferença é o modo de falha de cada uma.**
M9 vale entre **todas** as áreas, ativas ou não: o que ela impede é a mesma área
ser cadastrada duas vezes sem ninguém perceber, e desativar uma das duas não
desfaz a confusão. M10 vale **só entre as ativas**: o que ela impede é o mesmo
endereço resolver para duas taxas, e área desativada não cota nada. Reativar uma
área é reconstruir o agregado, e a construção confere M10 de novo.

**A normalização é regra de domínio, não detalhe de banco.** Maiúsculas, sem
acento, sem espaço duplo, aparado. "Boa Viagem", "boa viagem" e "BOA  VIAGEM"
são a mesma área — e se não forem, a Marli cadastra a mesma sem perceber e
metade dos pedidos sai com a taxa errada.

**Desativar área não altera pedido existente.** O pedido congelou
`nomeAreaSnapshot` e `taxaSnapshot` (ADR-020). Desativar afeta pedidos futuros e
mais nada.

**Área desativada com pedido em rota é caso normal.** O entregador termina a
entrega; a área simplesmente não aceita pedido novo.

**Procurar área devolve `Optional`, nunca uma taxa.** Não existe
`taxaPara(cep)` que responda zero quando não acha — é M11 na forma da
assinatura, e não numa checagem que alguém pode esquecer. São duas consultas: por
**nome de bairro**, que é o caminho da ADR-020 e o que o cliente diz na conversa,
e por **CEP**, que é o refinamento de quem quer resolver o endereço sem
perguntar. Área sem faixa de CEP é normal — ela só não é alcançável pela segunda.

---

## 6. Vínculo de entregador

Modelo e justificativa na ADR-022. O que este documento acrescenta:

- O mesmo entregador tem **um vínculo por estabelecimento**, com remuneração
  independente. Dois vínculos do mesmo par é erro.
- Desativar vínculo **não** fecha jornada aberta. Fechar o turno é ato do
  fechamento (`liquidacao.md` §3), e um vínculo desativado no meio do expediente
  não pode deixar dinheiro sem acerto.
- Alterar remuneração **não** afeta jornada já aberta — o `vinculoSnapshot` foi
  congelado na abertura.
- Entregador é usuário do sistema com identidade própria, mas **não é
  necessariamente `Membro`**. Ele vê a própria tela de entregas por vínculo, não
  por permissão comercial. Confundir os dois daria a Jorge acesso ao painel da
  loja.

---

## 7. Eventos publicados

Todos com `correlationId`, todos via outbox na mesma transação da alteração.

| Evento | Quando | Consumidores |
|---|---|---|
| `EstabelecimentoCriadoV1` | Cadastro concluído | `conversation` |
| `VinculoAlteradoV1` | Membro criado, alterado, suspenso, removido | **Todos** — invalidação de cache de autorização |
| `ConfiguracaoOperacionalAlteradaV1` | Tipo, modalidades, métodos, troco, `maxEntregasSimultaneas` | `order`, `delivery`, `conversation` |
| `ExpedienteAlteradoV1` | Abriu, fechou, pausou, retomou — com `motivo` | `conversation`, **`catalog`** (reativa `ESGOTADO_HOJE`) |
| `AreasDeEntregaAlteradasV1` | Área criada, alterada, desativada | `conversation` (lista de bairros) |
| `VinculoEntregadorAlteradoV1` | Vínculo ou remuneração | `delivery` |

> Este evento se chamava `DisponibilidadeAlteradaV1` e colidia com um evento de
> mesmo nome no `catalog-service`, que significa outra coisa. **ADR-031**
> renomeou este, e não aquele: o `catalog` nomeava o conceito de domínio dele,
> este aqui nomeava um atributo solto para um fato que o resto do repositório já
> chama de expediente.

O `motivo` desse evento não é decoração: o `catalog-service` só reativa produtos
`ESGOTADO_HOJE` quando ele é `ABERTURA_DE_EXPEDIENTE`. Retomada de pausa **não**
reativa nada, e é por isso que pausa e abertura precisam ser distinguíveis no
payload.

`VinculoAlteradoV1` é o mais crítico: é ele que faz a revogação de acesso valer
em segundos em vez de esperar o TTL. Se este evento se perder, alguém continua
com acesso que já foi retirado — é o caso que justifica o TTL curto existir
mesmo tendo invalidação por evento.

---

## 8. Invariantes

| # | Invariante | O que quebra sem ela |
|---|---|---|
| M1 | Permissão é do vínculo, nunca do usuário | Acesso de uma loja vaza para outra |
| M2 | O JWT não carrega permissão nem papel | Revogação demora até o token expirar |
| M3 | `GERENCIAR_EQUIPE` não alcança `ADMINISTRADOR` | Escalada de privilégio (H2.2) |
| M4 | `concedidas ⊆ próprias`, no convite **e** no aceite | Convite antigo vira privilégio sem dono |
| M5 | Revogação também limitada por `próprias` | Gerente esvazia permissões que não possui |
| M6 | ≥ 1 `ADMINISTRADOR` `ATIVO` por estabelecimento | Loja órfã, sem quem administre |
| M7 | Sem vínculo ativo → 403 idêntico a "não existe" | O código de erro vira scanner de lojas |
| M8 | Indisponibilidade do serviço → **negar** | Queda vira acesso irrestrito |
| M9 | `identificadorNormalizado` único por loja | Áreas duplicadas com taxas divergentes |
| M10 | Faixas de CEP não se sobrepõem | O mesmo endereço com duas taxas |
| M11 | Ausência de área ≠ taxa zero | Entrega grátis onde não se entrega |
| M12 | `metodosPorModalidade` não vazio, e nenhum conjunto de métodos vazio | Loja que não opera; ou modalidade que aceita pedido e nenhuma forma de pagar |
| M13 | Um vínculo de entregador por par entregador × loja | Remuneração ambígua na jornada |
| M14 | Pausa e fechamento não afetam pedido em andamento | Sábado à noite com pedidos cancelados em massa |
| M15 | `descontoDeRetirada ≥ 0` | Desconto negativo vira acréscimo silencioso |
| M16 | `fusoHorario` é identificador IANA válido do conjunto brasileiro, nunca nulo | Horário de funcionamento, expediente e fechamento erram juntos e em silêncio |
| M17 | `pedidoMinimoPorModalidade` tem entrada para **toda** modalidade aceita e para nenhuma outra, e toda entrada é `≥ 0` | Mínimo negativo não significa nada; ausência se confunde com zero (o erro que M11 nomeia do outro lado); mínimo de modalidade não aceita é configuração morta |
| M18 | Recuperação de estabelecimento cria ou promove `Membro` — nunca altera credencial de `Usuario` | Recuperar uma loja daria acesso às outras lojas do mesmo usuário |

---

## 9. O que este documento deliberadamente não decide

- **Limite de estabelecimentos por usuário** e de membros por estabelecimento.
  Não há regra; provavelmente não precisa haver antes de existir abuso.
- **Formato e provedor do link de convite.** É integração, não domínio.
