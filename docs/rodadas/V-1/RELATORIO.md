# V-1 — o validador de contrato ganha configuração, e passa

- **Aplicada em:** 22/11/2026 · **sobre:** `759eafb` · **versão do pacote:** v1
- **Commits:** `8dd9f55` (código) e `5acb7ed` (documentos); esta pasta chega no commit da S-7

## O que se mediu
- antes: o redocly falhava com 11 erros e 7 avisos; o workflow `contracts` falhou nas 24 execuções
  que teve, de 29/09 a 22/11/2026 — nunca passou
- o ajv, medido à parte antes de mudar: `unknown format "uuid"` no `_envelope-v1.json` — nunca
  tinha rodado no CI, porque o redocly falhava antes dele
- a P4 confirmada: o springdoc escreve `security: []` com `@SecurityRequirements` vazio na classe
- depois: redocly *"valid"* com 4 avisos (`operation-4xx-response`), ajv válido, `contracts=0`
- testes: identity 107, merchant 511, catalog 396 — iguais antes e depois; front 215/215

## O que mudou
- `identity`: `@Operation(summary)` nas quatro rotas e `@SecurityRequirements` vazio nos três controllers
- licença MIT nos três `OpenApiConfig`; os três contratos regravados; `generated/identity.ts`
- `redocly.yaml` na raiz; `ajv-formats` 3.0.1 no `package.json` e no lockfile, com `-c ajv-formats`
- `redocly.yaml` no filtro de caminho do `contracts.yml`
- emenda da ADR-039, índice, `contracts/README.md`, uma linha na tabela sem gatilho do razão

## Perguntas ao Rafael
| Pergunta | Resposta |
|---|---|
| Corrigir só a forma dos comandos (Gradle de `backend/`, redocly e ajv medidos à parte)? | sim, e medir o ajv antes; se falhar, parar |
| O ajv falhou: como consertar? | `ajv-formats` |
| A emenda da ADR-039 tinha dois fatos errados | reescrever com o medido |
| `redocly.yaml` no filtro do workflow? | acrescentar |
| `CLAUDE.md`:406 e ADR-039:12 na busca da regra 15 | ficam, são história |

## Previsões × resultado
| A previsão | O que saiu |
|---|---|
| `security: []` nas quatro rotas | saiu |
| contratos do `merchant` e do `catalog`: só o `license` | só o `license` |
| 0 erros, 4 avisos, saída 0 | igual |
| `contracts` verde pela primeira vez | verde, e os outros cinco workflows também |

## Defeitos achados
- do pacote: `./gradlew.bat` chamado da raiz; `tail` escondendo o resultado; `$?` medindo o `tail`;
  a emenda dizia que o `contracts/README.md` prometia "validados no CI" (não prometia)
- no caminho: a VM do WSL caía sem sessão anexada e a ponte do Windows recusava conexão — `wsl --shutdown`
- a emenda da ADR-039 não menciona o `ajv-formats`; o `npm install` relatou vulnerabilidades no audit, não conferidas

## O razão
| | antes | depois |
|---|---|---|
| recorte estreito | 112 | 112 |
| segunda passada | 28 | 28 |
| linhas numeradas | 132 | 132 |
