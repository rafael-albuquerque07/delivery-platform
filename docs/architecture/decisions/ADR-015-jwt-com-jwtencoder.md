# ADR-015 — Emitir JWT com `NimbusJwtEncoder` em vez do Authorization Server completo

**Status:** Aceita — 16/08/2026

## Contexto

O Spring Authorization Server foi incorporado ao Spring Security 7.0, o que
tornou natural considerá-lo. Ele entrega authorization code flow, PKCE, tela de
consentimento e registro de clientes — nenhum desses exercido por um MVP cujos
únicos clientes são a própria PWA e o app do entregador, ambos *first-party*.

O que o MVP precisa é emitir access token curto assinado com chave assimétrica,
publicar a chave pública em JWKS e fazer nove Resource Servers validarem.

## Decisão

O `identity-service` emite com `NimbusJwtEncoder` e par de chaves RSA, expondo
JWKS. Os demais serviços validam pela chave pública.

Claims no padrão OAuth 2.1/OIDC — `sub`, `roles`, `iss`, `aud`, `iat`, `exp`,
`jti`, `scope` — de modo que a migração futura troque apenas o emissor.

`NimbusJwtEncoder` está em `org.springframework.security.oauth2.jwt` (módulo
`spring-security-oauth2-jose`), já transitivo via
`spring-boot-starter-oauth2-resource-server`.

## Consequências

**Positivas** — configuração compreensível linha a linha; superfície menor;
migração futura não toca nenhum Resource Server, que só conhece o JWKS.

**Negativas** — rotação de refresh token, detecção de reúso e revogação passam
a ser responsabilidade própria. Tokens de refresh devem ser tratados por
**família**, e o reúso de um token já consumido revoga a família inteira. Sem
authorization code + PKCE, não há SSO nem login de terceiro sem migrar antes.

## Alternativas consideradas

- **Authorization Server completo agora.** Rejeitada para o MVP; permanece como
  evolução, correta quando surgir cliente de terceiro.
- **Keycloak ou provedor gerenciado.** Rejeitada: acrescenta um container e
  retira do projeto o aprendizado de identidade.

## Emenda de 26/08/2026 — `roles` e `scope` saem da lista de claims

A lista de claims acima foi escrita em 16/08 e contradiz a **ADR-011**, de
23/08, que decidiu o contrário e cita esta ADR entre as relacionadas — sem
emendá-la. O `estabelecimento.md` §2 e a tabela de armadilhas do `CLAUDE.md`
seguem a ADR-011.

**`roles` é o `papel`**, e não pode entrar no token por dois motivos que o
`estabelecimento.md` §2 registra:

- **fica velho.** A Marli revoga o acesso do Rafa às 20h e o token dele continua
  valendo até expirar;
- **é por estabelecimento.** Um token que carregasse o papel em cada loja
  cresceria com o número de vínculos e vazaria a lista de lojas do usuário em
  toda requisição.

**`scope` sai junto, por outro motivo: nada o leria.** A autorização é resolvida
por requisição contra o `merchant-service` (ADR-011), com cache curto e falha
fechada. Um `scope` sem consumidor é um campo com aparência de permissão
esperando que alguém o use — e é assim que a permissão volta para dentro do
token, seis meses depois, sem ninguém decidir isso.

### O conjunto vigente

| Claim | Para quê |
|---|---|
| `iss` | quem emitiu — confere com o JWKS |
| `sub` | qual usuário. **É o único dado de identidade** |
| `aud` | para quais serviços o token vale |
| `iat` · `exp` | validade curta |
| `jti` | identidade do token, para revogação e detecção de reúso |

**Nada além disso.** Permissão, papel e lista de estabelecimentos ficam fora e
são resolvidos por requisição.

A justificativa original desta ADR — formato OAuth para que a migração futura
troque apenas o emissor — **continua valendo**: os seis claims acima são todos
padrão OAuth 2.1/OIDC. O que muda é que dois da lista original descreviam
autorização que este sistema decidiu não carregar no token.
