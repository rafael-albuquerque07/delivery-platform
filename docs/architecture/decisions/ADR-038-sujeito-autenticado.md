# ADR-038 — O `sub` vira `UUID` na borda, e o caso de uso não conhece o token

**Status:** Aceita — 08/09/2026
**Relacionada:** ADR-011 (autorização por requisição), ADR-012 (roteamento do
gateway), ADR-015 emendada (claims), ADR-037 (emissão do access token)
**Precisa existir antes do primeiro endpoint autenticado** — o
`merchant-service` é ele, e o que este documento decidir será copiado oito vezes

## Contexto

Desde 07/09/2026 o `identity-service` emite token. O token carrega **seis
claims**, e o `sub` é *"o único dado de identidade"* (ADR-015 emendada): um UUID
em forma de texto.

Nenhum endpoint autenticado existe ainda. O login é público, o JWKS é público, e
o `/actuator/health` é público — as três rotas que a ADR-037 §1 liberou. Então a
pergunta **"como o `sub` chega ao caso de uso"** nunca precisou de resposta.

Ela precisa agora, e por dois motivos. O primeiro é que o `merchant-service` vai
ter o primeiro endpoint que pergunta *quem é você*. O segundo é o padrão que este
repositório já viu duas vezes: **a primeira implementação de uma coisa vira o
molde das outras oito**, com ou sem decisão. Aconteceu com a classe de
propriedades — não havia um `@ConfigurationProperties` no backend inteiro até
ontem, e agora há um que os outros vão copiar.

Uma busca por `UsuarioAutenticado`, `@AuthenticationPrincipal` e
`ContextoDeAcesso` no repositório inteiro não encontra nada, em código nem em
documento.

## Decisão

### 1. A borda converte; a aplicação recebe `UUID`

```java
@GetMapping("/api/v1/estabelecimentos")
public List<ResumoDeEstabelecimento> meus(@AuthenticationPrincipal Jwt token) {
    return listar.doUsuario(usuarioDe(token));
}
```

O controller recebe o `Jwt`, extrai o `sub`, converte para `UUID`, e passa o
`UUID` adiante. **O pacote `application` nunca importa
`org.springframework.security..`**, e nenhuma classe fora de `api` conhece a
existência de um token.

**Por que não ler o `SecurityContextHolder` no caso de uso.** Funcionaria, e é o
atalho mais comum. Mas transforma o caso de uso em algo que só executa dentro de
uma requisição HTTP autenticada: o teste de unidade passa a precisar montar um
contexto de segurança para exercitar regra de negócio, e a mesma regra deixa de
poder ser chamada por um consumidor de evento ou por uma tarefa agendada.

**Por que não um resolvedor de argumento.** Um `HandlerMethodArgumentResolver`
que injetasse o `UUID` direto na assinatura seria mais curto de escrever e
esconderia de onde o valor vem. Duas linhas explícitas na borda são legíveis por
quem nunca viu o projeto; maquinário invisível não é.

### 2. É `UUID` puro, não um tipo próprio

A ADR-011 escreve a porta assim, no corpo dela:

```java
Optional<ContextoDeAcesso> contexto(UUID usuarioId, UUID estabelecimentoId);
```

Esta ADR segue a assinatura que aquela já decidiu, em vez de inventar um
invólucro que a contradiria no primeiro uso.

### 3. `sub` que não é UUID é 401, não 500

Token assinado por nós com `sub` ilegível não deveria existir. Se existir, é
token que **não conseguimos atribuir a ninguém** — e requisição sem sujeito
atribuível é requisição não autenticada, não erro de servidor.

Um 500 aqui também vazaria: diria que o token passou pela validação de assinatura
e quebrou depois.

### 4. O gateway não repassa o sujeito em cabeçalho

A ADR-012 já decide que cada serviço revalida o token. Some a isso a invariante 9
do `CLAUDE.md` — *identificador que vem da URL não é confiável* — e a conclusão é
a mesma para cabeçalho: **o único sujeito confiável é o que sai do token que o
próprio serviço validou.** Nenhum serviço lê `X-Usuario-Id` de coisa nenhuma.

## Consequências

**Positivas**

- O caso de uso é chamável de qualquer lugar — teste, listener de evento, tarefa
  — porque recebe um valor, não um contexto.
- O `application` continua sem Spring Security, e o `ArchUnit` já vigia o
  `domain`.
- Duas linhas em cada controller, sem infraestrutura nova.

**Negativas**

- **`UUID` e `UUID` lado a lado compilam trocados.** `contexto(estabelecimentoId,
  usuarioId)` é um bug silencioso, e a ADR-011 põe os dois na mesma assinatura. A
  ordem é sempre *usuário, depois estabelecimento*, em toda a base. Se um dia essa
  troca acontecer de verdade, o conserto é tipar os dois — e aí a ADR-011 é
  emendada junto, não contornada.
- **A conversão se repete em cada controller autenticado.** É duplicação aceita:
  são duas linhas, e a alternativa é o maquinário invisível do §1.
- **Não há como saber, pelo `UUID`, se o usuário ainda existe.** O token vale
  trinta minutos (ADR-037 §3) e a conta pode ter sumido nesse intervalo. Quem
  precisar dessa garantia consulta; o token não a dá e não deve dar.

## Alternativas consideradas

- **`SecurityContextHolder` dentro do caso de uso.** Rejeitada no §1.
- **Resolvedor de argumento injetando `UUID`.** Rejeitada no §1. Continua
  disponível se o número de controllers autenticados crescer a ponto de a
  repetição incomodar mais que a explicitude ajudar.
- **Um tipo `UsuarioId`.** Impediria a troca de argumentos. Rejeitada porque
  contradiria a assinatura que a ADR-011 já escreveu, e porque cada serviço teria
  de declarar o seu — não há módulo compartilhado, e não haver é decisão.
- **O gateway injeta `X-Usuario-Id` e os serviços confiam.** Economiza a
  revalidação. Rejeitada pela ADR-012 e pela invariante 9: identificador que chega
  de fora do token é identificador que alguém pode escrever.

## O que esta decisão **não** decide

**Onde mora o validador de `iss` e `aud` que os nove serviços vão repetir.** A
ADR-037 §5 decide que *todos* validam explicitamente, e deixa o "onde" para
quando o segundo consumidor existir. Ele ainda não existe.

**Como o caso de uso obtém o `estabelecimentoId`.** Vem da URL, e a invariante 9
diz que não é confiável sozinho — é a ADR-011 que resolve isso, pela porta de
autorização. Este documento decide o sujeito, não o objeto.
