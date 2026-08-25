# Contratos

Esquemas, não classes Java compartilhadas. Nada aqui vira dependência de código
entre serviços — a única coisa que se compartilha é o **formato**.

- `openapi/` — um arquivo por serviço, validado no CI
- `asyncapi/` — eventos de domínio publicados via RabbitMQ
- `events/` — JSON Schema por evento, versionado

## Regra de versionamento

Todo evento carrega `eventId`, `eventType`, `eventVersion`, `occurredAt`,
`correlationId` e `payload`. A versão vive no campo `eventVersion` e no nome do
arquivo de esquema — **nunca dentro do `eventType`**. `PedidoRecebidoV1`, como
aparece nos documentos de domínio, é a forma abreviada do par.

### O que é compatível

| Compatível | Incompatível |
|---|---|
| acrescentar campo **opcional** ao `payload` | remover campo |
| relaxar restrição — teto maior, piso menor | renomear campo |
| acrescentar um evento novo | mudar o tipo de um campo |
| — | tornar obrigatório um campo que era opcional |
| — | acrescentar valor a um enum |
| — | **mudar o significado de um campo mantendo nome e tipo** |

A última é a perigosa: nenhum esquema pega. Mudança de semântica é mudança de
versão, mesmo que o esquema não mude uma vírgula. ADR-027.

### A ordem é o consumidor primeiro

Contrato de evento é mais difícil de mudar que contrato REST, e por um motivo
que não é óbvio: **a mensagem de ontem chega hoje**, para um código que mudou no
meio. Três passos, três implantações:

    1.  Consumidor passa a entender v1 E v2        implanta
    2.  Produtor passa a emitir v2                 implanta
    3.  Consumidor deixa de entender v1            implanta, depois de a fila drenar

O passo 3 espera a **fila esvaziar**, não a implantação terminar. Inverter a
ordem — produtor primeiro — quebra na janela entre as duas implantações.

Como é monorepo, cada passo é um commit em vez de uma negociação entre
repositórios (ADR-001). O monorepo resolve a coordenação; não resolve a
implantação.

### Como se sabe que a versão antiga pode sair

Os oito consumidores estão neste repositório: é varredura, não confiança.
Verificação no build, requisito do marco 3 — esquema obsoleto com consumidor
**falha**; esquema sem consumidor nenhum é **avisado**.
