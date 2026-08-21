# PRD — Plataforma de Delivery para Comércio de Bairro

## 1. Resumo executivo

Uma pizzaria de bairro recebe pedidos pelo WhatsApp, anota no caderno, calcula o troco de cabeça e, às onze da noite, tenta descobrir quanto vendeu e quanto o motoboy tem no bolso. Erra. Discute. Aceita a diferença e vai dormir.

Este produto transforma esse WhatsApp em sistema — sem tirar o comerciante do canal onde o pedido já acontece, sem cobrar comissão sobre a venda e sem exigir que ele aprenda um software novo.

A funcionalidade que vende o produto não é o cardápio digital nem o rastreamento no mapa. É o **fechamento de expediente**: ao fim do turno, uma tela mostra quanto entrou por método, quanto cada entregador recebeu e deve, qual a divergência de caixa e quanto ele tem a pagar de diária e comissão. O que hoje leva trinta minutos de papel e termina em desconfiança passa a levar dois minutos e terminar em extrato.

**Não somos um marketplace.** Não intermediamos a escolha do consumidor, não cobramos percentual da venda e não somos donos do cliente. O comerciante é o cliente do produto; o consumidor é usuário.

---

## 2. Problema

### 2.1 Como o comércio de bairro opera hoje

| Etapa | Ferramenta atual | O que dá errado |
|---|---|---|
| Receber pedido | WhatsApp pessoal ou Business | Pedido some na conversa; cliente sem resposta desiste |
| Registrar | Caderno, bloco de comanda | Ilegível, perdido, sem histórico |
| Calcular | Calculadora, cabeça | Erro de conta e de troco |
| Despachar | Grito para o motoboy | Ninguém sabe quem levou o quê |
| Receber | Dinheiro, maquininha, Pix na entrega | Comprovante falso de Pix; troco errado |
| Fechar o dia | Papel e memória | Divergência inexplicável, atrito com o entregador |
| Saber quanto vendeu | Não sabe | Decisão de compra e de preço no escuro |

### 2.2 Por que as alternativas não resolvem

**Marketplaces de delivery** trazem volume e cobram por isso — comissão sobre cada venda, além de ficarem com o relacionamento com o cliente. Resolvem a demanda, não a operação. E o comerciante continua com caderno para os pedidos que chegam pelo WhatsApp, que costumam ser a maioria dos recorrentes.

**Sistemas de PDV tradicionais** foram desenhados para o balcão. Exigem computador, treinamento e um fluxo que não começa onde o pedido de fato nasce. O atendente acaba digitando de novo, no sistema, o que já leu no WhatsApp.

**Planilhas e cadernos** custam zero e é por isso que sobrevivem. Qualquer substituto precisa ser melhor no fechamento de caixa já na primeira semana, ou o comerciante volta para o papel.

### 2.3 A dor que decide a compra

De todas as dores acima, uma se repete todo dia, envolve dinheiro e gera conflito interpessoal: **não saber, ao fim do expediente, quanto entrou, por qual método, e com quem está o dinheiro.**

É essa dor que o produto ataca primeiro. Cardápio, pedido e entrega existem para produzir o dado que fecha o caixa.

---

## 3. Público-alvo

### 3.1 Perfil do estabelecimento

| Atributo | Faixa-alvo |
|---|---|
| Segmento | Pizzaria, hamburgueria, açaí, marmitaria, minimercado, hortifruti, distribuidora de água e gás |
| Lojas | 1 a 3 |
| Pedidos por dia | 20 a 150 |
| Entregadores | 1 a 4, **próprios** — diarista, CLT ou fixo da casa |
| Origem do pedido | WhatsApp majoritariamente; telefone e balcão em menor volume |
| Pagamento | Predominantemente na entrega: dinheiro, maquininha e Pix |
| Maturidade digital | Opera pelo celular; raramente usa computador |

### 3.2 Personas

**Marli — dona da pizzaria (comprador e usuário principal)**
52 anos, atende o WhatsApp entre um forno e outro. Não usa computador; faz tudo pelo celular. Já testou dois sistemas e abandonou os dois porque "dava mais trabalho que o caderno". Compra o produto se o fechamento do dia parar de dar diferença. Cancela se precisar aprender algo complicado.

**Rafa — atendente do turno da noite (usuário de alta frequência)**
22 anos, opera o painel durante o pico. Precisa que a tela mostre o que fazer sem procurar. É quem sofre se o sistema for lento ou tiver muitos cliques. Não deve ter permissão para ver faturamento nem alterar preço.

**Jorge — entregador da casa (usuário periférico, alto impacto)**
Recebe diária mais comissão. Sai com fundo de troco da loja e volta com dinheiro no bolso. Quer saber, sem discussão, quanto entregou e quanto vai receber. É a outra ponta do conflito que o fechamento resolve.

**Cliente final (usuário, não comprador)**
Quer pedir pelo WhatsApp, sem baixar aplicativo e sem se cadastrar. Tolera esperar; não tolera não ser respondido. Repete o mesmo pedido com frequência.

---

## 4. Proposta de valor

> **Seu WhatsApp vira sistema. Seu dia fecha sozinho.**

| Para | Que sofre com | O produto oferece | Diferente de |
|---|---|---|---|
| Dono de comércio de bairro | Caixa que não fecha e pedido que se perde | Operação completa a partir do canal que ele já usa, com fechamento de expediente automático | Marketplace, que traz demanda mas cobra comissão e não organiza a operação |
| | Comissão sobre cada venda | Assinatura fixa por loja | Percentual sobre faturamento |
| | Sistemas que exigem treinamento | Fluxo que começa na conversa que ele já teria | PDV desenhado para balcão |

### 4.1 Princípios de produto

1. **O comerciante é o cliente.** Toda decisão de escopo passa por "isto serve ao dono da loja?".
2. **O canal é o WhatsApp.** Não competimos por atenção com um aplicativo que o cliente teria que instalar.
3. **Assinatura, não comissão.** O preço não pode crescer com o sucesso da loja.
4. **O dinheiro tem dono.** Todo valor recebido fora da plataforma é registrado com método, valor e responsável pela custódia.
5. **Simples de largar é simples de adotar.** Se o comerciante não conseguir fechar o primeiro dia sozinho, o produto falhou.

---

## 5. Premissas de negócio

Estas premissas determinam o desenho. Se alguma deixar de valer, o escopo precisa ser revisto.

| # | Premissa | Se deixar de valer |
|---|---|---|
| P1 | A plataforma **não custodia** o valor na maioria das transações | Volta a ser necessário adquirente, captura e estorno como caminho principal |
| P2 | O entregador **pertence ao estabelecimento** | Voltam oferta competitiva e remuneração por corrida |
| P3 | O **comerciante** é o cliente; o consumidor é usuário | Voltam descoberta, busca e navegação entre lojas |
| P4 | O pedido nasce predominantemente em **conversa** | O canal deixa de ser fundação e vira integração acessória |
| P5 | A taxa de entrega é por **área nomeada**, não por distância | Volta a ser necessário cálculo de rota e geoprocessamento |
| P6 | A maioria dos produtos opera em **disponibilidade qualitativa**, não em saldo contado | Controle quantitativo volta ao caminho principal |

---

## 6. Escopo funcional

Requisitos organizados por épico. Cada história traz critérios de aceite verificáveis.

### E1 — Conta e estabelecimento

**H1.1** Como comerciante, quero criar minha conta e cadastrar meu estabelecimento para começar a operar.
- Cadastro conclui em uma tela, pelo celular
- Quem cria o estabelecimento vira administrador dele automaticamente, sem passo adicional
- Um mesmo usuário pode ter vínculo com mais de um estabelecimento

**H1.2** Como comerciante, quero definir como minha loja opera para que o sistema se comporte de acordo.
- Tipo de operação: produção (cozinha), separação (balcão) ou mista
- Modalidades aceitas: entrega, retirada ou ambas
- Métodos de pagamento aceitos, por modalidade
- Política de troco: valor máximo de fundo e se aceita pedido sem troco disponível
- Horário de funcionamento e pausa manual imediata

**H1.3** Como comerciante, quero definir minhas áreas de entrega e taxas por bairro.
- Cadastro de bairros ou faixas de CEP atendidos, com taxa por área
- Endereço fora da área é recusado no momento do pedido, com mensagem clara
- Taxa zero é valor válido e distinto de área não atendida

### E2 — Equipe e permissões

**H2.1** Como administrador, quero convidar colaboradores com permissões diferentes das minhas.
- Convite por link ou telefone; o colaborador aceita e passa a ver apenas aquela loja
- Permissões concedidas item a item: ver produto, criar produto, alterar produto, desativar produto, ver pedido, alterar status, ver vendas, ver entrega, gerenciar equipe
- Colaborador sem permissão recebe recusa clara, não erro genérico

**H2.2** Como plataforma, preciso impedir escalada de privilégio.
- Quem gerencia equipe administra colaboradores, nunca administradores
- Ninguém concede permissão que não possui
- Um estabelecimento sempre mantém pelo menos um administrador ativo
- Nenhuma tela expõe dado de outro estabelecimento, mesmo com o identificador em mãos

### E3 — Cardápio e disponibilidade

**H3.1** Como comerciante, quero cadastrar produtos com as opções que meu cliente escolhe.
- Produto com preço-base, descrição e imagem
- Grupos de opções com mínimo e máximo de escolhas: tamanho obrigatório, adicionais opcionais, remoções
- Cada opção com seu acréscimo de preço
- Produto rascunho não aparece para o cliente

**H3.2** Como comerciante, quero marcar o que acabou sem precisar contar estoque.
- Estado por produto: disponível, acabando, esgotado hoje, esgotado por tempo indeterminado
- **Esgotado hoje volta automaticamente a disponível na abertura do expediente seguinte**
- Produto esgotado não é oferecido no canal nem aceito no carrinho

> A reativação automática é o que faz o recurso ser usado de verdade. Se o comerciante precisar lembrar de reativar doze itens toda manhã, ele para de usar na segunda semana.

**H3.3** Como comerciante de minimercado, quero controlar quantidade quando o produto exige.
- Modo de controle escolhido por produto: sem controle, qualitativo ou quantitativo
- Produto quantitativo tem saldo, reserva ao entrar no pedido e baixa na conclusão
- *Entra a partir do marco 5*

### E4 — Pedido e painel

**H4.1** Como atendente, quero ver os pedidos chegando e acompanhar o preparo numa tela só.
- Fila por status, com o mais urgente primeiro
- Alerta sonoro e visual para pedido novo
- Subestados coerentes com o tipo de operação: na fila, em produção, finalizando (cozinha); separando, conferido, embalado (balcão)
- Impressão ou exibição do pedido para a produção

**H4.2** Como cliente, quero montar meu pedido e saber o total antes de confirmar.
- Um pedido contém itens de um único estabelecimento
- Total exibido com itens, taxa de entrega e desconto discriminados
- Preço alterado entre a montagem e a confirmação exige nova confirmação explícita — nunca cobrança silenciosa da diferença

**H4.3** Como comerciante de mercado, quero substituir um item que acabou sem cancelar o pedido.
- Substituição por similar, remoção ou cancelamento, com registro de quem autorizou e por quê
- O valor original permanece registrado; o ajuste é lançamento novo, com histórico
- *Entra a partir do marco 5*

**H4.4** Como comerciante, quero cancelar ou o cliente cancelar dentro da regra.
- Todo cancelamento registra a causa
- Pedido pago que é cancelado dispara devolução do valor
- Pedido não pode ficar sem saída possível em nenhum estado

### E5 — Liquidação e troco

**H5.1** Como comerciante, quero registrar como cada pedido foi efetivamente pago.
- Momento do pagamento: online, na entrega ou na retirada
- **Método declarado** — o que o cliente disse que pagaria — registrado separadamente do **método liquidado** — o que de fato aconteceu
- Métodos presenciais: dinheiro, cartão na maquininha, Pix na entrega
- Não recebido é registro válido e explícito, nunca ausência de registro

> Declarado e liquidado divergem no caso normal, não na exceção: o cliente pede dinheiro e acaba pagando no cartão porque o entregador não tinha troco. Um campo só destrói a informação de que era preciso levar troco — e com ela a medição de acerto do preparo de caixa.

**H5.2** Como comerciante, quero controlar o troco.
- Cliente informa para quanto precisa de troco; o sistema calcula e mostra ao entregador
- Valor informado menor que o total impede o fechamento do pedido, com pergunta de volta ao cliente
- Sobra por indisponibilidade de moeda é registrada como ajuste de arredondamento, não como gorjeta
- Gorjeta é campo próprio e explícito

**H5.3** Como comerciante, quero que Pix na entrega seja confirmado de verdade.
- Cobrança gera identificador próprio por pedido, com QR dinâmico
- **Comprovante em imagem apresentado ao entregador não confirma pagamento**
- Estado só passa a liquidado com a confirmação do provedor
- A tela do entregador mostra a situação de forma inequívoca, sem texto ambíguo

### E6 — Entregadores e despacho

**H6.1** Como comerciante, quero cadastrar meus entregadores e como cada um é remunerado.
- Vínculo entre entregador e estabelecimento, com remuneração definida no vínculo
- Modelos: diária, comissão por entrega, diária mais comissão, taxa fixa por entrega
- O mesmo entregador pode atender duas lojas com regras diferentes

**H6.2** Como atendente, quero despachar o pedido para um entregador.
- Atribuição direta pelo painel, ou rodízio por ordem de retorno à loja
- O entregador vê endereço, itens, valor a receber do cliente e troco a levar
- Estados da entrega: atribuída, no estabelecimento, retirada, em trânsito, entregue, retornando, disponível na loja

**H6.3** Como comerciante, quero saber quem está com cada pedido e quem já voltou.
- Painel mostra entregador, pedido e estado atual
- Retorno à loja é estado registrado, não suposição

### E7 — Fechamento de expediente

> Marco central do produto. É a funcionalidade que sustenta a assinatura.

**H7.1** Como comerciante, quero abrir e fechar a jornada de cada entregador.
- Abertura registra fundo de troco entregue, horário e responsável
- Durante a jornada acumulam-se entregas concluídas, valores recebidos por método e adiantamentos
- Fechamento apura: dinheiro a devolver, divergência, remuneração devida e saldo líquido

**H7.2** Como comerciante, quero um extrato por entregador ao fim do dia.
- Créditos: diária, comissões, taxas fixas, gorjetas recebidas em cartão
- Débitos: dinheiro recebido em nome da loja, fundo de troco levantado, adiantamentos
- Resultado: valor a pagar ou a receber
- Extrato exportável e enviável ao entregador

**H7.3** Como comerciante, quero que o fechamento seja confiável.
- Jornada fechada não é editável; correção é lançamento de ajuste com autor, motivo e data
- Nenhum valor é contado duas vezes, mesmo com repetição de mensagem interna
- Total do dia por método bate com a soma das liquidações registradas

### E8 — Canal WhatsApp

**H8.1** Como cliente, quero pedir pelo WhatsApp sem instalar nada.
- Número próprio por estabelecimento
- Cardápio apresentado por lista interativa, com botões
- Confirmação de pedido por botão explícito, nunca por reação ou emoji
- Endereço e forma de pagamento coletados na própria conversa

**H8.2** Como comerciante, quero atender pelo WhatsApp mesmo sem inteligência artificial.
- Menu numerado determinístico atende o pedido recorrente de ponta a ponta
- Este modo permanece disponível como alternativa quando a interpretação automática falhar

**H8.3** Como plataforma, preciso controlar o custo do canal.
- Fluxo desenhado para o menor número de turnos possível
- Custo por pedido concluído acompanhado como indicador de produto
- Alerta quando o custo de uma conversa ultrapassa o limite configurado

### E9 — Atendimento assistido

**H9.1** Como cliente, quero ser entendido escrevendo do meu jeito.
- Interpretação de texto livre, áudio e localização
- Preço, disponibilidade, área de entrega e total vêm sempre do sistema — **a interpretação automática nunca calcula valor**
- Imagem nunca é aceita como comprovante de pagamento

**H9.2** Como cliente, quero falar com uma pessoa quando precisar.
- Escalonamento automático em: pedido explícito do cliente, duas tentativas sem resolver, valor acima do teto, **qualquer pergunta sobre alergia, restrição alimentar ou ingrediente**
- Fila de atendimento humano visível no painel, com tempo de espera e motivo
- Devolução explícita ao atendimento automático após a resolução
- Fora do horário comercial, resposta honesta em vez de silêncio

**H9.3** Como comerciante, quero que o atendimento tenha a cara da minha loja.
- Persona com nome, tom regional e memória do histórico do cliente
- Identificação como assistente virtual no primeiro contato, com resposta honesta se perguntado diretamente

### E10 — Obrigações fiscais

**H10.1** Como comerciante, preciso emitir documento fiscal do pedido.
- Emissão a partir do pedido concluído
- Contingência quando o serviço de emissão estiver indisponível
- *Entra a partir do marco 7*

---

## 7. Não-objetivos

Registrados explicitamente para evitar retorno em cada revisão de escopo.

| Fora do escopo | Motivo |
|---|---|
| Marketplace, descoberta e comparação entre lojas | O cliente do produto é o comerciante; intermediar a escolha do consumidor é outro negócio |
| Comissão sobre venda | Contraria o princípio de preço fixo e a proposta de valor |
| Pool competitivo de entregadores autônomos | O entregador pertence ao estabelecimento |
| Aplicativo nativo para consumidor | O canal é o WhatsApp; aplicativo compete por instalação |
| Rastreamento em mapa em tempo real | O comerciante conhece o entregador e o alcança por telefone; entra apenas se houver operação que justifique |
| Repasse financeiro e conciliação bancária | O produto registra o acerto operacional; não movimenta valor |
| Cupons, promoções e precificação dinâmica | Campos existem no modelo com valor neutro; regra fica para depois |
| Operação em múltiplas cidades ou regiões | Fora do perfil-alvo inicial |
| Avaliações e reputação | Não resolve a dor que decide a compra |

---

## 8. Métricas de sucesso

### 8.1 Métricas de produto

| Métrica | Por que importa | Meta inicial |
|---|---|---|
| **Tempo de fechamento de expediente** | É a dor que vende o produto | Abaixo de 5 minutos, contra cerca de 30 no papel |
| **Divergência média de caixa por jornada** | Mede se o acerto ficou confiável | Queda mês a mês; próxima de zero no terceiro mês |
| **Pedidos com liquidação registrada** | Invariante de negócio | 100 % — qualquer valor abaixo indica furo no fluxo |
| **Pedidos concluídos sem intervenção humana no canal** | Define se o atendimento assistido funciona | Acima de 70 % dos pedidos recorrentes |
| **Pedidos perdidos por falta de resposta** | Dor direta do comerciante | Próximo de zero |
| **Retenção mensal do estabelecimento** | Mede se o produto substitui o caderno de fato | Acima de 85 % no terceiro mês |
| **Custo de canal por pedido concluído** | Define o piso do preço | Abaixo de R$ 0,35 |

### 8.2 Sinal de adoção real

O indicador mais honesto de que o produto pegou não é volume de pedidos: é **o comerciante fechar o expediente pelo sistema por cinco dias seguidos**. Enquanto ele conferir no papel em paralelo, o produto ainda não substituiu nada.

---

## 9. Modelo de negócio

**Assinatura mensal por estabelecimento**, sem percentual sobre venda.

O custo variável relevante é o canal de mensageria. A partir de 1º de outubro de 2026, respostas livres dentro da janela de 24 horas passam a ser tarifadas por mensagem — cerca de R$ 0,04 por mensagem enviada pela loja.

```
6 mensagens enviadas por pedido        ≈ R$ 0,24
50 pedidos/dia × 30 dias               ≈ R$ 360/mês por loja
```

Consequências para o produto, não apenas para a engenharia:

- **Menos turnos de conversa é requisito**, não refinamento. Listas e botões resolvem em uma mensagem o que texto livre resolve em quatro.
- O plano precisa comportar esse custo com margem, ou repassá-lo de forma transparente por faixa de volume.
- Laço de conversa — interpretação ruim que gera vinte mensagens de esclarecimento — é risco financeiro, e por isso tem alerta próprio.

---

## 10. Marcos de entrega

Cada marco entrega valor operável, não uma camada técnica.

| Marco | Entrega | O comerciante consegue |
|---|---|---|
| **1** | Conta, estabelecimento, equipe e permissões | Cadastrar a loja e o time, com poderes diferentes |
| **2** | Cardápio com opções e disponibilidade qualitativa | Publicar o cardápio e marcar o que acabou |
| **3** | Painel de pedidos e ciclo de preparo | Receber e acompanhar pedidos numa tela |
| **4** | Liquidação presencial, troco e Pix confirmado | Registrar como cada pedido foi pago de verdade |
| **5** | Entregadores, vínculo, remuneração e despacho | Despachar e saber quem está com cada pedido |
| **6** | **Fechamento de expediente** | Fechar o dia com extrato por entregador |
| **7** | Canal WhatsApp determinístico | Receber pedido pelo WhatsApp com menu numerado |
| **8** | Pagamento online e emissão fiscal | Cobrar antecipado e emitir documento |
| **9** | Atendimento assistido e escalonamento | Atender sozinho a maior parte dos pedidos |
| **10** | Controle quantitativo e substituição de item | Operar minimercado com contagem de estoque |
| **11** | Rastreamento e telemetria | Mostrar a entrega no mapa |

### 10.1 Ponto de parada com produto vendável

**Ao fim do marco 6 existe produto completo.** A loja cadastra equipe e cardápio, recebe e prepara pedidos, despacha com entregador próprio, registra como cada pedido foi liquidado e fecha o expediente com extrato. Nenhum marco posterior é necessário para que isso funcione, e a dor que decide a compra já está resolvida.

Os marcos 7 a 9 ampliam o alcance — trazem o pedido para dentro do canal onde ele já nasce. Os marcos 10 e 11 abrem segmentos novos.

---

## 11. Riscos de produto

| Risco | Impacto | Mitigação |
|---|---|---|
| **Comerciante volta ao papel na primeira semana** | Perda do cliente | Fechamento de expediente entregue cedo; sem treinamento obrigatório; operação inteira pelo celular |
| **Regra de preço do canal muda de novo** | Margem comprimida | Fornecedor atrás de camada substituível; menos turnos por desenho; custo por pedido monitorado |
| **Interpretação automática erra valor ou pedido** | Prejuízo e desconfiança | Valor nunca vem da interpretação; confirmação por botão; escalonamento obrigatório em alergia e restrição |
| **Trote no pedido presencial** | Prejuízo direto do comerciante | Confirmação ativa na conversa; teto de valor para cliente sem histórico; bloqueio por reincidência |
| **Entregador contesta o acerto** | Atrito e abandono do recurso | Extrato imutável, com lançamentos rastreáveis e correção por ajuste, nunca por edição |
| **Dependência de um único canal externo** | Interrupção total do recebimento | Painel e balcão continuam operando; canal é entrada, não fundação da operação |
| **Dados pessoais em conversa** | Exposição regulatória | Retenção definida por tipo; áudio com prazo curto; exclusão que alcança a conversa; contrato com o provedor de interpretação |

---

## 12. Requisitos não funcionais

Detalhados no documento de arquitetura; resumidos aqui pelo que afeta a experiência.

- **Confiabilidade financeira.** Nenhuma entrega ou retirada é concluída sem liquidação registrada, com método efetivo, valor efetivo e responsável pela custódia identificados.
- **Isolamento entre estabelecimentos.** Nenhum dado atravessa a fronteira de uma loja, mesmo com o identificador conhecido.
- **Continuidade.** Falha do canal externo não impede operar pelo painel e pelo balcão.
- **Desempenho percebido.** O painel responde em pico de pedidos; a fila é a tela mais otimizada do produto.
- **Privacidade.** Endereço, telefone e conteúdo de conversa tratados como dado pessoal, com retenção por finalidade.
- **Auditoria.** Alterações em produto, preço, equipe, pedido e liquidação são rastreáveis.

---

## 13. Glossário de negócio

| Termo | Significado |
|---|---|
| **Estabelecimento** | A loja. Unidade de isolamento de dados, equipe e cardápio |
| **Vínculo** | Relação entre uma pessoa e um estabelecimento, com papel e permissões próprios |
| **Modalidade** | Entrega ou retirada no balcão |
| **Momento do pagamento** | Online, na entrega ou na retirada |
| **Método declarado** | O que o cliente disse que pagaria |
| **Método liquidado** | O que de fato aconteceu |
| **Liquidação** | Registro de que um pedido foi pago, com método, valor e responsável pela custódia |
| **Custódia** | Responsabilidade por valor recebido fora da plataforma — em regra, o entregador |
| **Jornada** | Turno de um entregador num estabelecimento, com abertura, movimento e fechamento |
| **Divergência** | Diferença entre o esperado e o apurado no fechamento da jornada |
| **Disponibilidade qualitativa** | Estado do produto — disponível, acabando, esgotado hoje — sem contagem de saldo |
| **Área de entrega** | Bairro ou faixa de CEP atendido, com taxa própria |
| **Ajuste** | Lançamento que corrige um valor sem apagar o original |
