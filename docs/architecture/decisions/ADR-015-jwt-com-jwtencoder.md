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
