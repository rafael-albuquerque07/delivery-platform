# Procedimento — pedido de exclusão de titular

**Status:** esqueleto · **Base:** ADR-013
**Vale enquanto não houver saga automatizada.** Ver ADR-013 §7.

> Este documento é o que existe no lugar de código. Enquanto não houver cliente
> real, exclusão é operação manual e auditada. Automatizar antes de haver demanda
> é construir uma saga para zero pedidos por ano.

---

## Antes de executar

| Passo | Por quê |
|---|---|
| **Identifique o titular** e confirme a identidade dele | Executar exclusão a pedido de terceiro é vazamento com outro nome |
| **Registre o pedido** com data, canal e quem atendeu | O prazo de resposta ao titular corre a partir daqui |
| **Confirme o escopo**: consumidor, colaborador ou entregador | O tratamento é diferente — ADR-013 §5 |

**A anonimização é irreversível.** Não há desfazer. Confira duas vezes o
identificador antes de qualquer comando.

---

## Execução, por serviço

Ordem importa: exclua o que é excluível antes de anonimizar o que permanece, para
que uma interrupção no meio não deixe dado pessoal órfão.

| # | Serviço | Ação | Escopo |
|---|---|---|---|
| 1 | `conversation` | **Excluir** `Contato`, conversas, mensagens, endereços conhecidos | Tudo |
| 2 | `order` | **Anonimizar** nome, telefone e endereço textual do cliente | Só esses campos. Valores, itens, área e liquidações permanecem |
| 3 | `delivery` | **Anonimizar** o endereço em trânsito, se houver entrega não terminal | Derivado do pedido |
| 4 | `identity` | **Anonimizar** conta, se o titular for usuário do sistema | Só após 5 anos do fim do vínculo — ADR-013 §3 |
| 5 | `merchant` | **Anonimizar** telefone de colaborador ou dados de entregador | Idem |
| 6 | `settlement` | **Anonimizar** identificação do entregador nos fechamentos | Valores permanecem |
| 7 | `payment` | Nada a fazer | Guarda `txid`, não dado do titular |
| 8 | `catalog` · `gateway` | Nada a fazer | Não guardam dado pessoal |

**Documento fiscal emitido não é tocado.** Art. 16, I. Ver a resposta ao titular
abaixo.

---

## Depois de executar

1. **Verifique** que nenhum serviço ainda responde ao identificador do titular.
2. **Registre a conclusão** com data e quem executou.
3. **Responda ao titular** dizendo o que foi apagado **e o que foi conservado,
   com o motivo**. Prometer exclusão total e manter nota fiscal é pior que
   explicar a diferença.
4. **Anote na lista de exclusões pendentes de reaplicação** — próxima seção.

---

## Restauração de backup

Backup restaurado ressuscita o que foi apagado. Isto **não é observação, é passo
obrigatório** de qualquer restauração:

> Depois de restaurar qualquer banco a partir de backup, **reaplique todas as
> exclusões registradas** com data posterior à do backup, antes de o serviço
> voltar a atender.

Os backups rolam em 30 dias (ADR-013 §3), então a lista a reaplicar é curta — mas
esquecê-la desfaz silenciosamente uma exclusão que já foi confirmada ao titular.

---

## A escrever antes do primeiro cliente

- Comandos concretos por serviço, com o SQL e os filtros exatos
- Quem tem autoridade para executar
- Prazo de resposta ao titular
- Encarregado (DPO) e canal de atendimento
- O mesmo procedimento para **acesso** e **portabilidade** (LGPD art. 18), que é
  a mesma varredura no sentido inverso
