# ADR-027 — O que conta como mudança compatível num evento

**Status:** Aceita — 24/08/2026
**Relacionada:** ADR-001 (monorepo), ADR-002 (banco por serviço), ADR-026 (fila morta)
**Detalhada em:** `contracts/README.md`
**Precisa existir antes do marco 3** — é quando nasce o primeiro par produtor–consumidor de verdade

## Contexto

Os eventos já carregam versão: o envelope tem `eventType` e `eventVersion`, e o
esquema vive num arquivo por versão. E `contracts/README.md` já diz que mudança
incompatível cria uma versão nova.

O que **não** existe é a definição de *incompatível*. Sem ela, o sufixo de versão
é convenção esperando ser quebrada: alguém acrescenta um campo obrigatório, não
muda a versão porque "só acrescentei", e um consumidor quebra três semanas
depois, processando uma mensagem que já estava na fila.

Contrato de evento é mais difícil de mudar que contrato REST, e por um motivo que
não é óbvio: **o consumidor pode estar processando uma mensagem antiga**. Numa
API, cliente e servidor conversam agora. Numa fila, a mensagem de ontem chega
hoje, para um código que mudou no meio.

### A regra atual descreve o mundo errado

`contracts/README.md` diz hoje:

> Mudança incompatível cria uma nova versão; a anterior continua publicada até
> nenhum consumidor depender dela.

Isso descreve um sistema de **repositórios separados**, onde produtor e
consumidor evoluem sem coordenação e a única saída é publicar as duas versões em
paralelo. Aqui é monorepo (ADR-001): produtor, consumidor e esquema mudam no
mesmo commit.

O monorepo remove o problema de **coordenação**. Não remove o de
**implantação** — os serviços não sobem no mesmo instante, e as filas seguram
mensagens da versão anterior enquanto isso.

## Decisão

### 1. Compatível é o que não quebra o consumidor antigo

| Compatível | Incompatível |
|---|---|
| acrescentar campo **opcional** ao `payload` | remover campo |
| relaxar restrição — teto maior, piso menor | renomear campo |
| acrescentar um evento novo | mudar o tipo de um campo |
| — | tornar obrigatório um campo que era opcional |
| — | **mudar o significado de um campo mantendo nome e tipo** |

A última linha é a perigosa: **nenhum esquema pega.** O campo continua sendo uma
string, o consumidor continua lendo, e passa a agir sobre outra coisa. Vale como
regra: mudança de semântica é mudança de versão, mesmo que o esquema não mude
uma vírgula.

### 2. Valor novo em enum é incompatível por padrão

Acrescentar um valor a um enum é compatível para quem só registra o campo e
**incompatível para quem decide caminho com ele** — um `switch` exaustivo passa a
cair no vazio.

Como o produtor não sabe qual dos dois o consumidor é, a regra fica do lado
seguro: **valor novo em enum exige versão nova**, salvo se todos os consumidores
tratarem valor desconhecido como "ignorar".

E é assim que consumidor se escreve desde a primeira linha: **valor desconhecido
não estoura, não bloqueia e não vira exceção** — vira registro de que chegou algo
que este consumidor não entende. É regra de código, não de esquema, e por isso
está no `CLAUDE.md`.

### 3. A ordem é o consumidor primeiro, sempre

Três passos, três implantações, e o monorepo faz de cada um um commit em vez de
uma negociação entre repositórios:

```
1.  Consumidor passa a entender v1 E v2        implanta
2.  Produtor passa a emitir v2                 implanta
3.  Consumidor deixa de entender v1            implanta — só depois da fila drenar
```

**O passo 3 espera a fila esvaziar**, não a implantação terminar. Uma mensagem v1
publicada um segundo antes do passo 2 ainda está na fila, e o consumidor precisa
entendê-la. Antecipar o passo 3 é como se produz uma fila morta cheia de
mensagens que eram válidas quando nasceram (ADR-026).

Inverter a ordem — produtor primeiro — é o erro comum, e ele quebra na janela
entre as duas implantações.

### 4. Remover uma versão é verificável, e é o monorepo que permite

`contracts/README.md` dizia "até nenhum consumidor depender dela" sem dizer como
se sabe. Em polirepo, não se sabe. Aqui, **os oito consumidores estão no mesmo
repositório**: é varredura.

Verificação no build, requisito do marco 3:

- um esquema marcado obsoleto que ainda tenha consumidor **falha o build**;
- um esquema sem consumidor nenhum é **avisado**, porque é o sinal de que pode
  ser removido.

É uma das poucas vezes em que o monorepo transforma uma regra de processo em
regra de build. Vale registrar como consequência positiva da ADR-001 que não
estava prevista lá.

### 5. Onde a versão mora

Reafirmando, porque a forma abreviada engana:

```
eventType     "PedidoRecebido"                 sem sufixo de versão
eventVersion  1                                inteiro
arquivo       events/pedido-recebido-v1.json
```

`PedidoRecebidoV1`, como aparece nos documentos de domínio, é a **forma
abreviada do par**. Não é o conteúdo de `eventType`.

## Consequências

**Positivas**

- "Mudança incompatível" deixa de ser julgamento de quem está escrevendo e passa
  a ser tabela.
- A armadilha da semântica — mesmo nome, mesmo tipo, outro significado — fica
  nomeada, e é a única que nenhuma ferramenta pega.
- A remoção de versão vira verificação de build em vez de coragem.
- A ordem de implantação fica escrita antes de existir a primeira implantação,
  que é o único momento em que escrever isso é barato.

**Negativas**

- **Três implantações para uma mudança de contrato.** É mais cerimônia do que
  parece necessário num sistema de uma pessoa, e a tentação de pular o passo 1
  vai existir toda vez. A defesa é que o custo de pular aparece em produção, na
  janela entre duas implantações, e não em teste.
- **Enum aditivo tratado como incompatível é conservador demais** para o caso em
  que todos os consumidores ignoram o desconhecido. Aceito: a alternativa exige
  saber, a cada mudança, como cada consumidor lê o campo — e essa auditoria custa
  mais que a versão nova.
- **A verificação de build ainda não existe.** Até existir, a regra depende de
  ninguém errar — a mesma situação da regra de dependência entre serviços da
  ADR-001.
- **Nada disso vale para o contrato REST**, que tem regra própria e mais frouxa
  porque cliente e servidor conversam no mesmo instante. Duas regras diferentes
  para duas coisas parecidas é fonte de confusão, e está dito aqui para não ser
  descoberto por engano.

## Alternativas consideradas

- **Publicar as duas versões em paralelo durante a transição.** É o que o texto
  antigo do `contracts/README.md` sugeria, e é a resposta certa em polirepo.
  Rejeitada: dobra o tráfego, obriga o produtor a manter dois mapeamentos, e
  resolve um problema de coordenação que o monorepo já resolve. O problema que
  sobra é de implantação, e para ele a resposta é a ordem do §3, não a
  duplicação.
- **Sem versão nenhuma; evoluir o esquema no lugar.** Rejeitada pelo motivo do
  contexto: a mensagem de ontem chega hoje. Sem versão, o consumidor não tem como
  saber qual formato está lendo.
- **Versão dentro do `eventType`** (`PedidoRecebidoV1` como valor do campo).
  Rejeitada: duplica informação que já tem campo próprio, e obriga a analisar
  string para descobrir a versão.
- **Compatibilidade decidida por ferramenta de registro de esquemas.** É o
  desenho de quem tem dezenas de serviços e times separados. Rejeitada: mais um
  componente para operar, para resolver com automação o que oito consumidores no
  mesmo repositório resolvem com varredura.
- **Enum aditivo sempre compatível**, exigindo que todo consumidor ignore o
  desconhecido. Mais simples e é a prática comum. Rejeitada por um motivo
  específico deste sistema: vários enums decidem caminho — modalidade, método
  liquidado, motivo de cancelamento — e um valor novo silenciosamente ignorado
  num deles é dinheiro contado errado.

## Emenda que esta decisão provoca

`contracts/README.md` diz que a versão anterior "continua publicada até nenhum
consumidor depender dela". Sob esta ADR a regra é outra: **o consumidor aceita as
duas antes de o produtor mudar**, e a versão antiga sai depois de a fila drenar.
O texto precisa ser substituído, não complementado.

## Emenda que esta decisão provoca — segunda

O documento de arquitetura v2 (`docs/referencia/`), §13.3 e §18.2, diz que falta
definir o que conta como mudança compatível e como se sabe que nenhum consumidor
depende da versão anterior. As duas passam a estar respondidas aqui. O que
permanece pendente é a **verificação no build**, requisito do marco 3.
