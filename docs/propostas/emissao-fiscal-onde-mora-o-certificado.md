# Proposta — a emissão fiscal: onde mora o certificado, e o que a reforma tributária muda

- **Status:** **proposta. Não é ADR, não está no índice, não vale como decisão.**
- **Data:** 15/11/2026 · **entrou no repositório:** 17/11/2026, na R-15
- **Nasce de:** uma conversa de 15/11/2026 do Rafael sobre ACBr, agentes de automação e o
  certificado A1, e uma pesquisa do mesmo dia sobre a reforma tributária do consumo (EC 132/2023,
  LC 214/2025)
- **Só pode virar ADR depois de:** existir pedido — o marco 3 e o 7a, na ordem da ADR-083

> ⚠ **Quem escreveu esta proposta não é contador nem advogado** — e o Rafael, que desenvolve o
> projeto sozinho, também não tem os dois. O que está abaixo é informação para decidir e para levar
> a quem é. **Nenhuma linha desta proposta é orientação fiscal ou jurídica.**

---

## 1. O que o repositório já diz — medido

| Fato | Onde |
|---|---|
| A emissão fiscal é do **marco 8** — *"Cobrar antecipado e emitir documento"* —, com a **H10.1** | `PRD.md` §10 |
| A emissão *"chega junto"* ao **`payment`**, e está listada como não decidida | `pagamento.md` §11 e §12 |
| A retenção do documento emitido já está decidida: *"INTOCADO — art. 16, I"* | ADR-013 |
| **Não há segredo guardado por estabelecimento** | ADR-085, o primeiro gatilho — medido na R-14 |
| A persona principal *"não usa computador; faz tudo pelo celular"* | `PRD.md` §3 |
| **P1** — a plataforma não custodia valor · **P2** — o entregador pertence ao estabelecimento | `PRD.md` §5 |
| O `.gitignore` ignora `*.pfx` | `.gitignore`:14 |

---

## 2. O que a pesquisa achou — com o grau de certeza de cada coisa

**Legenda:** 🟢 duas fontes concordam · 🟡 uma fonte secundária · 🔴 não consegui abrir a fonte
primária, e o que está escrito é leitura de terceiros.

### 2.1 · Online ou offline

- 🟢 **O fluxo normal da NFC-e é online**, com autorização da SEFAZ em segundos. Isso não mudou.
- 🟢 **A contingência offline continua existindo em novembro de 2026**: emite sem internet e
  transmite depois, *"em geral até 24 horas"*, e **varia por estado**. Há estado em que ela não
  é possível.
- 🟡 Uma **NF-e simplificada** para o varejo está em desenvolvimento, *"com possibilidade de
  contingência offline"* — sinal de que o offline segue no desenho oficial.
- 🔴 **Nenhuma fonte lida diz que a reforma acaba com a contingência offline — e isso não
  autoriza afirmar que não muda.** *A pesquisa não conseguiu abrir a Nota Técnica 2025.002 nem o
  Decreto 12.955/2026. Ausência nas fontes lidas não é ausência na norma.*

### 2.2 · O envio à Receita

- 🟢 **A nota continua sendo autorizada pela SEFAZ.** O que a reforma acrescenta é que os dados
  do documento são **compartilhados automaticamente** entre União, estados e municípios, e que
  a Receita Federal e o Comitê Gestor do IBS publicam juntos o cronograma e as regras.
- 🟡 **O documento fiscal passa a valer como confissão do valor devido** de IBS e CBS (art. 60,
  § 1º), e é a base da apuração e do crédito. *Nota errada deixa de ser só nota errada.*
- 🟢 **O leiaute está em movimento.** A NT 2025.002 teve as versões 1.50, 1.51 (04/08/2026),
  1.52 (01/10/2026) e 1.53 (30/10/2026), com datas de produção adiadas mais de uma vez.
- 🟢 **Datas:** destaque de IBS/CBS obrigatório para o regime normal em fatos geradores desde
  **03/08/2026**; a rejeição automática da nota sem esses campos está **suspensa**, sem data;
  **Simples Nacional e MEI entram em 01/01/2027**.

### 2.3 · Split payment — o recolhimento na liquidação do pagamento

- 🟢 **Não há data em norma publicada.** A lei manda começar de forma gradual, por ato conjunto.
- 🟡 **As duas fontes divergem sobre por onde começa:** uma diz que a lei manda começar pelo
  consumidor final e pelos meios eletrônicos do varejo; a outra diz que o manual preliminar
  descreve uma primeira fase **B2B opcional**, e que a Receita anunciou obrigatoriedade entre
  empresas para 2028 — *anúncio, não norma*.
- 🟡 **O pagamento passa a ser amarrado ao documento fiscal.** O prestador de pagamento consulta
  os sistemas do fisco antes de liquidar; **se a consulta falhar, recolhe o valor que está no
  documento** e o ajuste vem depois.
- 🟡 **O Simples fica fora do split no início**, em regra.

### 2.4 · ⚠ O achado que a pesquisa não procurava: o art. 22

- 🟡 A LC 214/2025, art. 22, torna a **plataforma digital** responsável solidária pelo IBS e
  pela CBS quando o fornecedor nacional **não registra a operação em documento fiscal
  eletrônico**.
- 🟡 **Plataforma digital**, nessa lei, é quem intermedeia operação não presencial **e controla
  ao menos um elemento essencial**: **cobrança, pagamento, definição dos termos e condições, ou
  entrega**. Quem não controla nenhum fica fora.
- 🔴 **O texto literal do artigo não foi lido** — o Planalto redirecionou e os espelhos
  bloquearam. Duas fontes secundárias concordam entre si. A LC 227/2026 alterou o artigo, e esta
  proposta não sabe como.

**Por que isto importa aqui:** a **P1** e a **P2** deste produto — não custodia valor, e o
entregador é da loja — são, por coincidência, **dois dos quatro elementos** da definição. ⚠ **E
o marco 8 traz *pagamento online* e *emissão fiscal* juntos.** *Se o pagamento online fizer a
plataforma passar a controlar a cobrança, é possível que o produto entre na definição — e aí a
emissão fiscal do lojista deixa de ser só uma funcionalidade e passa a ser a proteção da
própria plataforma.* **Isso é interpretação jurídica, e não cabe a esta proposta.**

---

## 3. As decisões — e a recomendação em cada uma

### D1 · Quem o marco 8 serve primeiro

| Opção | Consequência |
|---|---|
| **A persona do canal** — a Marli | emissão sem balcão; só faz sentido se ela for obrigada a emitir |
| **A persona do balcão** — o minimercado do marco 10 | agente local; o fiscal espera o marco 10 |

**Recomendação: não decidir antes da resposta do contador** (§4). *A reforma torna a pergunta
mais urgente, não menos: o Simples e o MEI entram no IBS/CBS em 01/01/2027.*

### D2 · Onde mora o certificado A1

| Opção | A favor | Contra |
|---|---|---|
| **Na nuvem** — ACBrLib em servidor | um lugar só para atualizar um leiaute que muda todo mês; serve o celular; a numeração tem escritor único | **a plataforma guarda a assinatura digital do lojista** — contrato e segurança; sem internet não emite |
| **Na loja** — agente local com ACBrMonitor | o certificado nunca sai da loja; contingência offline | um agente por loja para manter e atualizar a cada nota técnica; exige computador |
| **Num provedor de emissão por API** — terceiro | a custódia do certificado e o leiaute ficam com quem vive disso | custo por documento, contra a margem do §9; dependência; *não pesquisei nenhum a fundo* |

**Recomendação: nuvem primeiro, e a pesquisa reforçou isso por três motivos novos.**

1. **O leiaute é alvo móvel.** Quatro versões da nota técnica em três meses. *Atualizar um
   servidor é uma implantação; atualizar um agente em cada loja é suporte.*
2. **A reforma amarra pagamento e documento.** Quanto mais o recolhimento depender do documento
   no momento da liquidação, menos vale emitir depois.
3. **A contingência offline varia por estado**, e para quem nasce no canal ela quase não tem
   uso: sem internet não há pedido.

⚠ **E a terceira opção merece ser medida antes de recusada.** *Guardar o A1 de terceiros é a
decisão mais pesada desta proposta, e existe um jeito de não guardar.*

### D3 · O que fazer quando não se sabe se autorizou

**Recomendação, e esta não muda com nada:** *"não sei"* é estado do sistema, e o tratamento é
**consultar pela chave, nunca reemitir**. Uma emissão por pedido, com chave de idempotência
igual ao `pedidoId`; número queimado e não usado vira **inutilização**, que é estado do fluxo.

### D4 · ⚠ A fronteira do art. 22, antes do marco 8

| Opção | Consequência |
|---|---|
| **Manter a P1 no pagamento online** — o Pix é do lojista, a plataforma só registra | tende a manter o produto fora da definição; limita o que o marco 8 pode ser |
| **A plataforma passa a iniciar a cobrança** | produto mais completo; possível responsabilidade solidária, e dever de informar ao fisco |

**Recomendação: um advogado tributarista lê o art. 22 contra a P1 e a P2 antes de o marco 8
ser desenhado**, e a resposta entra num documento de revisão fiscal, criado quando o portão abrir. *É a decisão em que
errar custa mais, e é a única desta lista sem inclinação nenhuma.*

### D5 · Quando

**Recomendação: depois do marco 3 e do 7a.** Sem pedido não há nota. *A reforma
não cria prazo para este produto — ele não emite nada hoje. O que ela cria é uma condição: o
que for construído nasce no leiaute da NT 2025.002, com IBS e CBS.*

---

## 3-B. O que fica definido enquanto o projeto for de uma pessoa só

**Acrescentado em 15/11/2026, e prevalece sobre o §3 enquanto valer a condição:** o Rafael
desenvolve sozinho, sem contador e sem advogado. *Então o desenho é feito para que nenhuma
pergunta que exige esses dois profissionais precise de resposta antes de existir um lojista de
verdade.*

⚠ **Isto corrige a recomendação do D2.** *"Nuvem primeiro"* significa guardar o certificado A1
do lojista. **Sem advogado, essa é exatamente a opção que não se escolhe.**

| # | Regra | O que ela dispensa |
|---|---|---|
| R1 | **O projeto não emite documento fiscal de verdade.** A emissão é construída contra uma **SEFAZ simulada** — um dublê que autoriza, rejeita, demora e duplica | certificado, CNPJ, credenciamento, e qualquer exposição fiscal |
| R2 | **Porta e adaptador.** Uma `EmissorFiscalPort` no `payment`, com o adaptador **simulado** agora. Provedor por API, ACBrLib e agente local são adaptadores de depois | decidir o D2 agora |
| R3 | **A plataforma nunca guarda certificado de lojista.** Para o adaptador real sobram dois caminhos: **provedor por API**, em que um terceiro guarda, ou **agente local**, em que o certificado fica na loja | a pergunta 6, do contrato de custódia |
| R4 | **Emitir é configuração da loja, desligada por padrão.** Quem decide se a loja emite é o lojista, com o contador dele — nunca a plataforma | as perguntas 1 a 3, do contador |
| R5 | **A P1 vale também no marco 8.** O Pix é do lojista; a plataforma registra o pagamento e não inicia cobrança | as perguntas 4 e 5, do art. 22 — *pela leitura conservadora das fontes, não por parecer* |
| R6 | **Nenhum XML fiscal escrito à mão.** O adaptador real usa biblioteca ou provedor mantido | acompanhar uma nota técnica que mudou quatro vezes em três meses |

**O que se constrói assim é o que tem valor de engenharia:** a máquina de estados do documento,
uma emissão por pedido com chave igual ao `pedidoId`, o *"não sei → consulta, nunca reemite"*, a
inutilização, e a frase da recusa em português de pizzaria. **Nada disso precisa de SEFAZ de
verdade para ser provado** — precisa de um dublê que falhe de todos os jeitos.

**Por que não a homologação oficial:** a SEFAZ valida a assinatura mesmo em homologação, então
ela exige **certificado A1 válido** de um CNPJ. O **sandbox** de um provedor de API não exige
certificado, e serve para ver um adaptador real funcionar sem empresa.

### Os três portões — o que só abre com profissional

**Estes não são pendências de hoje. São condições que bloqueiam a produção**, e ficam nesta proposta — o
`docs/operacao/revisao-juridica.md` é o dossiê de proteção de dados (ADR-013) e não trata de emissão:

1. **Antes do primeiro lojista real emitir** uma nota por este sistema → contador.
2. **Antes de a plataforma guardar qualquer certificado** → advogado. *A R3 existe para este
   portão nunca precisar abrir.*
3. **Antes de a plataforma iniciar cobrança** em nome próprio ou do lojista → advogado, com o
   art. 22 da LC 214/2025 na mão.

⚠ **O desenho mantém o projeto fora desses três portões. Ele não substitui quem os abre.** *E o
dia em que um lojista pagar a assinatura, o projeto terá um CNPJ — e com ele, um contador. É o
momento natural, e não é hoje.*

**Um recurso gratuito para quando chegar a hora:** o Sebrae tem o *"Fale com o Especialista"*,
descrito como *"consultoria individual e 100% gratuita, com duração de 1 hora"*, sobre os
impactos da reforma — em `sebrae.com.br/subsites/reforma-tributaria`.

---

## 4. As perguntas para quem é de direito

⚠ *Com o §3-B em vigor, nenhuma destas precisa de resposta agora. Elas ficam escritas para o
dia em que um portão for aberto.*

**Para o contador:**

1. Uma pizzaria pequena, MEI ou Simples, vendendo para pessoa física na UF dela, **é obrigada a
   emitir NFC-e hoje? E a partir de 01/01/2027?**
2. O DANFE pode ir **só digital**, pelo WhatsApp?
3. Na UF dela, a **contingência offline** é permitida, e com que prazo?

**Para o advogado:**

4. Este produto — que não cobra, não recebe e não entrega — **é plataforma digital** nos termos
   do art. 22 da LC 214/2025, com a redação da LC 227/2026?
5. **Passa a ser**, se no marco 8 gerar a cobrança do Pix em nome do lojista?
6. Guardar o certificado A1 do lojista para emitir em nome dele: que contrato e que
   responsabilidade isso exige?

---

## 5. O que esta proposta tocaria, se aceita

*Levantado na M-4, em 14/11/2026 — uma medição que não ficou registrada no repositório.
Reconfira antes de usar.*

ADR-021 · ADR-023 · ADR-011 e o javadoc do `Permissao` · `pedido.md` e ADR-010, só se emitir
virar estado · `contracts/eventos.md` · `pagamento.md` §11 e §12 · um bloco novo sobre segredo
do lojista · e, com o D4, o `revisao-juridica.md`.

**E seriam quatro ADRs, nesta ordem:** produto → arquitetura → agente local, só se o balcão for
aceito → tela.

---

## 6. O que esta proposta não faz

Não escolhe biblioteca, provedor nem marco. Não escreve código. **Não interpreta a lei.** E não
substitui a leitura das fontes primárias — a NT 2025.002 vigente, o Decreto 12.955/2026 e o
texto consolidado da LC 214/2025 —, **que a pesquisa não conseguiu abrir** e que alguém precisa
ler antes de qualquer aceite.

---

## Gatilho escrito

**A primeira rodada que desenhar o marco 8.** Aí esta proposta é lida contra o repositório daquele
dia — o §1 e o §5 reconferidos, as fontes primárias abertas — e vira ADR ou é recusada.

**O primeiro lojista pagante.** Aí o projeto tem CNPJ e contador, o §3-B deixa de ser a condição
do projeto, e os três portões passam a ter quem os abra.

## Fontes — todas secundárias, lidas em 15/11/2026

- [NFC-e em 2026: regras, uso e mudanças tributárias — Contábeis, 09/11/2026](https://www.contabeis.com.br/artigos/80693/nfc-e-em-2026-regras-uso-e-mudancas-tributarias/)
- [Confira todas as mudanças nos documentos fiscais em 2026 — Contábeis, 08/01/2026](https://www.contabeis.com.br/noticias/74556/reforma-tributaria-novas-regras-para-df-e-e-ibs-cbs-em-vigor/)
- [IBS e CBS na NF-e em 2026 — Taxcel](https://taxcel.com.br/ibs-cbs-nota-fiscal-2026-obrigatorio-rejeicao)
- [NT 2025.002 v1.52 — Tax360](https://www.tax360.com.br/conteudo/nt-nfe-nfce-2025-002-rtc-v152)
- [Split Payment na LC 214/2025 — Avalara](https://site.avalarabrasil.com.br/reforma-tributaria/split-payment-lei-complementar-214-2025/)
- [Split payment: o prazo real — Hopecont](https://hopecont.com/blog/split-payment-2027/)
- [Simples Nacional e MEI: IBS e CBS na nota fiscal — Jettax, 25/08/2026](https://www.jettax.com.br/blog/simples-nacional-e-mei-ibs-e-cbs-na-nota-fiscal-em-2026-entenda-as-regras/)
- [Responsabilidade de marketplaces e plataformas digitais — Systax, 05/05/2026](https://www.systax.com.br/artigos-e-novidades/como-a-reforma-tributaria-muda-a-responsabilidade-de-marketplaces-e-plataformas-digitais/)
- [Tributação nas plataformas digitais — Migalhas, 21/07/2025](https://www.migalhas.com.br/depeso/434556/reforma-tributaria-36-tributacao-nas-plataformas-digitais)
- [Documento fiscal eletrônico IBS e CBS — SimTax, 02/12/2025](https://simtax.com.br/documento-fiscal-eletronico-ibs-cbs/)
- [NFC-e em contingência — Focus NFe, 28/01/2026](https://focusnfe.com.br/blog/nfc-e-em-contingencia-como-emitir-e-detalhes-para-aprovacao/)
- [Fórum do Projeto ACBr — NT 2025.002 v1.51](https://www.projetoacbr.com.br/forum/topic/93915-reforma-tribut%C3%A1ria-nt-2025002-v151-suspende-data-fixa-de-obrigatoriedade-do-ibscbs-na-nf-e-e-adia-exig%C3%AAncia-em-produ%C3%A7%C3%A3o/)

- [API de Nota Fiscal: ambiente de homologação — NFE.io, 23/06/2026](https://nfe.io/blog/nota-fiscal/api-de-nota-fiscal-ambiente-de-homologacao/)
- [Página especial descomplica Reforma Tributária para pequenos negócios — Agência Sebrae, 19/08/2026](https://agenciasebrae.com.br/economia-e-politica/pagina-especial-descomplica-reforma-tributaria-para-pequenos-negocios/)

⚠ *Várias destas fontes são de empresas que vendem software ou consultoria fiscal. O que vale é
a norma.*
