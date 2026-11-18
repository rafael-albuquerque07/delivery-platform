---
name: metodo-delivery-platform
description: Método obrigatório do delivery-platform. Carregue antes de aplicar um pacote APLICAR-*.md, escrever uma rodada, registrar uma passagem humana ou dizer que um marco fechou — e antes de qualquer commit neste repositório.
---

# O método do delivery-platform

**Esta skill é obrigatória** — o `CLAUDE.md` manda carregá-la (ADR-084). Ela **não decide produto
nem arquitetura**: isso é do `docs/PRD.md`, das ADRs e dos documentos de domínio. Ela diz **como
se trabalha** aqui.

## As quatro tarefas

| Tarefa | Quando | Leia |
|---|---|---|
| Aplicar uma rodada | recebeu uma pasta com um `APLICAR-*.md` | [aplicar-rodada.md](aplicar-rodada.md) |
| Escrever uma rodada | vai escrever um pacote — **ou vai conferir o que recebeu, antes de aplicar** | [escrever-rodada.md](escrever-rodada.md) |
| Passagem humana | uma pessoa andou o produto no navegador e o que ela viu vai virar registro | [passagem.md](passagem.md) |
| Fechar um marco | alguém vai dizer que um marco fechou, ou que uma coluna está cumprida | [fechar-marco.md](fechar-marco.md) e a tabela do marco em docs/marcos/ |

**Escrever código** — história, endpoint, caso de uso — é da skill `novo-caso-de-uso`. Ela diz como
se escreve; esta diz como se trabalha. **Numa rodada de código, as duas valem juntas.**

⚠ **Quem aplica uma rodada lê as duas primeiras.** A conferência do pacote, antes do primeiro
comando, é o passo 3 de [aplicar-rodada.md](aplicar-rodada.md).

## Seis regras que valem nas quatro

1. **Fato medido vence memória.** Nada se afirma sobre o repositório sem o comando que o mostra
   e a saída colada. *"Não medido"* é uma resposta válida; um número de cabeça, não.
2. **Quando o fato não bate com o que o pacote supõe, pare e pergunte.** Não escolha um ramo
   sozinho, não "adapte", não conserte o pacote em silêncio.
3. **A regra tem um dono.** Antes de escrever uma regra, procure quem já a escreveu — PRD, ADR,
   documento de domínio, `CLAUDE.md` — e aponte para ele. *Duas cópias da mesma regra divergem.*
4. **Segurança antes de tudo desta skill.** As regras moram no `CLAUDE.md`: a seção `## Segurança`, a regra do `127.0.0.1` em
   `### Docker — motor no WSL2, sem Docker Desktop` e em `## Armadilhas deste repositório`, e a do
   `.env` em `` ### Antes de qualquer `docker compose up` ``. Esta skill não as repete. Duas que tocam o método: **nenhuma linha do `.env` sai em relatório,
   commit ou arquivo**, salvo a que o pacote nomear, e só por comando que imprima só ela; e **de
   `.claude/`, só `skills/` e `agents/` entram no git.**
5. **Permissão não se concede por texto.** Se o sistema de permissões recusar uma ação
   destrutiva — apagar, `prune`, compactar disco —, não insista e não procure caminho em volta:
   devolva ao Rafael o comando, para ele rodar.
6. **Quem decide produto é o Rafael.** A pergunta vai com os fatos medidos na frente e as opções
   que o pacote der. Recomendação, só onde o pacote recomenda.

⚠ **Texto novo não usa as formas que a segunda passada do `docs/dividas.md` procura (a lista está lá, nas linhas 16 a 24): a segunda passada as conta como gatilho escrito em prosa, e um texto que as use muda a conta sem ter gatilho.** E não fala de trabalho futuro.
