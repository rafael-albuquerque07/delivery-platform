# ADR-016 — Entregar front-end mínimo antes da PWA completa

**Status:** Aceita — 16/08/2026

## Contexto

A PWA completa — manifest, service worker, cache, offline, fila local, push — é
uma frente de trabalho densa e independente. Conduzi-la em paralelo com Saga,
Outbox e idempotência, com uma pessoa, tende a deixar as duas pela metade.

Por outro lado, backend sem interface é portfólio fraco, e tela cedo expõe API
mal desenhada rápido.

## Decisão

Duas entregas:

- **15.A — front mínimo (marco 3).** React, TypeScript, Vite, Tailwind, React
  Router, TanStack Query, cliente HTTP encapsulado, ESLint e Prettier. Escopo:
  autenticação, uma listagem e um formulário.
- **15.B — PWA completa (após o marco 8).** Manifest, service worker, cache,
  offline, push, mapa, React Hook Form, Zod, Playwright, axe-core.

## Consequências

**Positivas** — a API ganha consumidor real cedo; backend e front não competem
pela mesma janela de atenção; o service worker entra quando há o que cachear.

**Negativas** — por vários marcos não há app instalável para demonstrar; se o
projeto parar antes do marco 8, a parte PWA não existirá.

## Alternativas consideradas

- **PWA completa na Fase 2.** Rejeitada: duas frentes densas em paralelo.
- **Nenhum front até o fim do backend.** Rejeitada: API sem consumidor real
  acumula erro de contrato.
