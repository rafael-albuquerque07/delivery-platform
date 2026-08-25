# Operação — procedimentos

Um documento por procedimento. Cada um responde **como se executa**, sob pressão
e sem improviso. É o terceiro tipo de documento do repositório:

| | Responde |
|---|---|
| `docs/architecture/decisions/` | *por que* se decidiu assim |
| `docs/dominio/` | *qual é a regra* hoje |
| **`docs/operacao/`** | *como se executa* |

## Os procedimentos

| Documento | Quando se abre | Base |
|---|---|---|
| [`exclusao-de-titular.md`](exclusao-de-titular.md) | um titular pede exclusão dos dados dele | ADR-013 |
| [`mensagem-na-fila-morta.md`](mensagem-na-fila-morta.md) | o alerta de fila morta disparou — ou apareceu sobra no fechamento | ADR-026 |
| [`recuperacao-de-acesso.md`](recuperacao-de-acesso.md) | o administrador de uma loja perdeu o acesso e o canal não resolve | ADR-029 |
| [`reconciliacao-de-pagamento.md`](reconciliacao-de-pagamento.md) | fecha o dia e é hora de conferir o provedor de pagamento contra o nosso registro | ADR-023, ADR-030 |

## Material de apoio

Não são procedimentos: são documentos que existem para serem **revisados por
alguém de fora**.

| Documento | Para quê |
|---|---|
| [`legitimo-interesse.md`](legitimo-interesse.md) | o teste de balanceamento das duas hipóteses de legítimo interesse — rascunho de engenharia, para revisão jurídica |
| [`revisao-juridica.md`](revisao-juridica.md) | a pauta da consulta com advogado: o que foi assumido, com que raciocínio, e o que muda conforme a resposta |

## Uma regra comum aos quatro procedimentos

Todos registram **quem autorizou, com que prova, quando**. O registro é
somente-inserção, como os ajustes de valor — e o nome de quem autorizou sobrevive
ao desligamento, anonimizado só depois dos cinco anos (ADR-013 §5).

Procedimento sem rastro é decisão sem dono.
