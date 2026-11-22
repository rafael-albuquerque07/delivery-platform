# M3-2 — a cotação do catalog

- **Aplicada em:** 22/11/2026 · **sobre:** `b275220` · **versão do pacote:** v3
- **Commits:** `0bc6680` (código) e `759eafb` (documentos); esta pasta chega no commit da S-7

**As versões v1 e v2 pararam antes do primeiro comando**, pelo que o revisor de pacote achou — o
pacote da v3, guardado ao lado, registra os dois (linhas 3 a 35). A v1: a ADR-024 não citada, a
opção repetida decidida sem o Rafael, uma opção só na exceção, frases que a rodada deixava falsas e
a previsão do razão errada. A v2: o índice das decisões, a frase sobre a porta da ADR-018, a pipeline
`frontend` vermelha também pelo Prettier, e a fronteira do 404.

## O que se mediu
- §0 e §2 bateram com o pacote; o `tipos:conferir` falhava antes, com 190 linhas em `merchant.ts`
  (as quatro rotas `/internal` do `merchant`, da M3-1)
- `catalog`: 365 → 396 testes, verdes (`CotacaoControllerIT` 19, `CotacaoDoProdutoTest` 8)
- back end depois: 1084 verdes; o total de antes (1053) foi deduzido, não medido à parte

## O que mudou
- `catalog`: a rota `POST /internal/merchants/{estabelecimentoId}/catalog/quote` — `CotarItensService`,
  `CotacaoController`, `Produto.cotar`, quatro `Motivo` novos, o 409 com a lista `indisponiveis`
- `ProdutoRepositorio.buscarPorIds`; o `TratadorDeErros` com as frases novas; o `ContratoDeErrosIT`
- `contracts/openapi/catalog-service.json`; `generated/catalog.ts` e `generated/merchant.ts`
- ADR-088; emendas às ADRs 018, 024, 035, 054, 068 e 086; razão, `catalogo.md`, `marco-3.md`

## Perguntas ao Rafael
| Pergunta | Resposta |
|---|---|
| Ryuk inalcançável, portas do Docker quebradas: reiniciar o WSL? | "reinicie o WSL, pode continuar" |

## Previsões × resultado
| A previsão | O que saiu |
|---|---|
| M1 (sem o máximo): cai o caso 6 | caiu o 6 |
| M2 (sem `vendavel()`): caem 9, 10, 14 e 19 | caíram os quatro |
| M3 (inexistente → 404): cai o 11 | caiu o 11 |
| M4 (só a primeira opção): cai o 13 | caiu o 13 |
| M5 (Map no serviço): cai o 19 | caiu o 19 |
| `frontend` volta ao verde; `catalog-service` verde | os dois verdes; o `contracts` vermelho, como já estava desde 29/09 |

Cada mutação restaurada com diff vazio.

## Defeitos achados
- não previstos pelo pacote: o `ErroDoCatalogoTest` passou de 9 para 13 casos; os quatro
  repositórios falsos dos testes ganharam `buscarPorIds`; duas vírgulas finais pelo Prettier
- o caso 17 do `PainelPage` falhou uma vez sob carga e passou na repetição (215/215)
- o workflow `contracts` estava vermelho em todas as execuções — deu origem à V-1

## O razão
| | antes | depois |
|---|---|---|
| recorte estreito | não registrado | 112 |
| segunda passada | 29 (`dividas.md`, a conta da M3-1) | 28 |
| linhas numeradas | não registrado | 132 |

Os números de depois bateram com a previsão do pacote.
