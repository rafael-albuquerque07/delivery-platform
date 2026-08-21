# ADR-019 — `DeliveryQuotePort`: cotação separada da execução da entrega

**Status:** ⛔ **Revogada** — 18/08/2026
**Substituída por:** ADR-020 (taxa de entrega por área nomeada)
**Motivo da revogação:** premissa de negócio alterada

---

## Por que foi revogada

Esta ADR resolvia um problema real — o cliente precisa ver a taxa de entrega
antes de pagar, mas a entrega só é criada depois do preparo — e o resolvia
separando **cotação** de **execução**, com uma porta e dois adaptadores:
distância geodésica no MVP, rota viária depois.

A revisão de adequação ao mercado PME estabeleceu que **a taxa de entrega é
cobrada por bairro, não por quilômetro**. Com isso, o problema que esta ADR
resolvia deixa de existir: não há distância a calcular. O que resta é verificar
se um endereço pertence a uma área atendida e consultar a taxa daquela área.

A decisão que a substitui está em **ADR-020**.

## O que permanece válido

Duas ideias desta ADR sobreviveram e foram incorporadas à ADR-020:

1. **Separar cotação de execução.** Continua correto: cotar é responsabilidade do
   momento do checkout; executar é responsabilidade do despacho. São momentos e
   serviços diferentes.
2. **Manter a cotação atrás de uma porta.** É o que permitirá reintroduzir
   cálculo de rota no marco 11, se houver operação que justifique, sem tocar no
   domínio do pedido.

## O que foi descartado

- `FlatRateDeliveryQuoteAdapter` com Haversine e política `baseFee + km × perKm`
- `GeolocationDeliveryQuoteAdapter` como evolução planejada do MVP
- `distanceMeters` como campo congelado no pedido
- A ressalva sobre Haversine subestimar distância viária em 20 % a 40 % — deixa
  de ser relevante porque a distância deixa de precificar

## Registro histórico

O texto integral da versão aceita em 16/08/2026 permanece no histórico do
repositório. Ele continua sendo a decisão correta **se a premissa P5 do PRD
deixar de valer** — ou seja, se a plataforma passar a atender operação que cobre
por distância percorrida.
