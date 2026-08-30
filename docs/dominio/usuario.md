# Usuário — a conta de quem entra no painel

**Serviço:** `identity-service`
**Decisões que o moldam:** ADR-011 (autorização por requisição), ADR-013
(retenção e anonimização), ADR-015 emendada (claims), ADR-029 (recuperação),
ADR-036 (identificador de login), ADR-035 (idioma)

> Este documento é o primeiro deste diretório a nascer **depois** das ADRs que o
> cercam, e não antes. Até 30/08/2026 o `Usuario` aparecia em cinco documentos
> sem ser definido em nenhum: a ADR-011 o citava como metade de um par, a ADR-015
> como dono de um claim, a ADR-029 duas vezes pela negativa, e a única lista de
> campos existia de passagem numa ADR sobre retenção. A §7 é maior que o normal
> porque essa dívida está sendo paga em voz alta.

---

## 1. Agregado

`Usuario` é raiz de agregado. Ele representa **uma pessoa que entra no sistema** —
não um comerciante, não um colaborador, não um entregador. Esses são papéis que a
pessoa exerce em um estabelecimento, e vivem no `Vinculo`, no `merchant-service`.

```
Usuario  (raiz)
├── identificacao        id, nome
├── telefone             numero, verificadoEm    ← login e canal de cadastro (ADR-036)
├── email                endereco, verificadoEm  ← opcional (ADR-029 §1)
└── credencial           hashDaSenha
```

O `id` é o que atravessa a fronteira: é o `sub` do token (ADR-015 emendada) e é o
identificador de titular que os outros serviços referenciam quando guardam dado
pessoal (ADR-013). **Telefone não referencia nada** — ele identifica na porta de
entrada e para por aí, porque muda de dono e o `id` não.

---

## 2. Canais e verificação

A conta tem dois canais, com pesos diferentes:

| Canal | Obrigatório | Para quê |
|---|---|---|
| `telefone` | **sim** — é o canal de cadastro | login (ADR-036) e recuperação comum (ADR-029 §1) |
| `email` | não — é o "segundo canal", pedido e não exigido | recuperação secundária |

Cada um carrega `verificadoEm`. **Canal não verificado não autentica e não
recupera** — ele é só um dado de contato até que a posse seja provada.

O telefone **nasce verificado**: a ADR-029 §1 diz que o canal usado para criar a
conta nasce verificado, e a ADR-036 fixa que esse canal é sempre o telefone. É o
que garante que não existe conta sem meio de entrar.

O `verificadoEm` é carimbo de tempo, não booleano, e a diferença importa: quando
a política de reverificação existir (§7), ela vai precisar saber *quando*, não
apenas *se*.

---

## 3. Credencial

A senha existe **apenas como hash** (ADR-013 §1). Nunca em claro, nunca em log,
nunca em evento, nunca em resposta de API — a tabela de armadilhas do `CLAUDE.md`
já diz isso para log, e aqui vale para todo lugar.

**Não há campo separado para o algoritmo.** Hash moderno — bcrypt, argon2 —
carrega o próprio identificador de algoritmo, custo e sal dentro da string. Um
campo à parte seria uma segunda fonte da mesma verdade, e as duas envelheceriam
em ritmos diferentes.

---

## 4. O que o `Usuario` **não** guarda

Esta seção existe porque quase tudo que alguém espera encontrar aqui está em
outro lugar, de propósito.

| Não guarda | Onde vive | Por quê |
|---|---|---|
| permissão, papel | `Vinculo`, no `merchant-service` | permissão é do par usuário × estabelecimento, resolvida por requisição com falha fechada (ADR-011) |
| lista de estabelecimentos | idem | um `Usuario` com a lista dentro vazaria as lojas dele em toda requisição |
| estado de "administrador" | idem | não existe administrador global; existe administrador **de um estabelecimento** |
| dado do consumidor | `conversa.md` | o consumidor não tem conta (P3) — é um número numa conversa |

E a recíproca, que é a invariante M18 do `estabelecimento.md` vista deste lado:
**nenhuma operação de recuperação de estabelecimento toca a credencial deste
agregado.** Recuperar acesso à loja A cria ou promove um `Membro` em A; mexer na
senha do `Usuario` vazaria para B, C e D (ADR-029).

---

## 5. Eventos

**Nenhum, hoje.**

O `identity-service` não publica evento de domínio no marco 1. Criar conta não é
fato que outro serviço precise saber: o `merchant-service` descobre o usuário
quando um vínculo é criado, e ninguém mais tem interesse.

Isto é decisão, não omissão, e por isso o `identity-service` entra na seção *"O
que deliberadamente não tem evento"* de `contracts/eventos.md` — até 30/08/2026 a
matriz não o mencionava em lugar nenhum, que é lacuna do tipo que a ADR-032
existe para impedir. Se algum dia o `identity` publicar, o nome entra na matriz
antes de entrar no código (ADR-031, ADR-032).

### Consumidos

Nenhum.

---

## 6. Invariantes

| # | Invariante | O que quebra se falhar |
|---|---|---|
| U1 | `telefone` presente, normalizado e **único** no sistema | Sem ele não há como entrar nem recuperar; repetido, duas pessoas disputam a mesma conta (ADR-036) |
| U2 | `email` opcional; quando presente, **único** | Dois cadastros com o mesmo e-mail tornam a recuperação secundária ambígua (ADR-029 §1) |
| U3 | Canal só autentica ou recupera com `verificadoEm ≠ null` | Cadastrar o telefone de outra pessoa daria acesso à conta dela |
| U4 | O `telefone` nasce verificado no cadastro; **não existe `Usuario` com telefone não verificado** | Seria conta válida sem meio de entrar — o caso que a ADR-036 fecha ao fixar o telefone como canal de cadastro |
| U5 | A senha existe apenas como hash; nunca em claro, log, evento ou resposta | Vazamento de credencial em texto puro é irreversível |
| U6 | O `Usuario` não guarda permissão, papel nem lista de estabelecimentos | Permissão dentro do sujeito fica velha e é resolvida sem consultar quem sabe (ADR-011) |
| U7 | Nenhuma operação de recuperação de **estabelecimento** altera a credencial | Prova de posse de uma loja daria acesso a todas as outras (ADR-029, M18) |
| U8 | O `Usuario` existe sem estabelecimento nenhum | Criar conta não pode exigir criar loja; o convite (ADR-029) depende de a conta existir antes do vínculo |
| U9 | Registros com dado pessoal **deste titular** referenciam o `id`, nunca o telefone | Telefone muda de dono; anonimização é destrutiva e sem mapa reverso, e precisa de chave estável |

**A U9 fala só deste titular, e a distinção importa.** A ADR-013 §6 nomeia **dois**
identificadores estáveis — `usuarioId` e `contatoId` —, porque há dois tipos de
titular. O consumidor não tem conta (P3): ele é `Contato`, raiz própria do
`conversation-service` (`conversa.md`), alcançado pelo `contatoId`. Um registro
com dado pessoal referencia o identificador do titular **que ele descreve**, e o
`Usuario` responde por um dos dois.

**Como isto vira teste.** U1 a U4 são teste de integração com o banco — índice
único e guarda de verificação só existem de verdade contra o Postgres. U5 é teste
de unidade sobre o serializador e o log. U6 é `ArchUnit`: nenhuma classe do pacote
`domain` do `identity` pode referenciar permissão. U7 e U8 são testes do agregado.
**U9 é regra de revisão, não de código** — não há como um teste do `identity`
verificar o que o `order` guarda.

---

## 7. O que este documento deliberadamente **não** decide

Oito itens, e o primeiro é o que mais me incomoda.

**Número reciclado.** Operadora recicla telefone. Um número devolvido e
redistribuído pode entregar a outra pessoa a chave de uma conta abandonada — e a
prova de posse dela será legítima. Nenhuma política existe. Candidatos: exigir
senha *além* da posse do número, expirar conta sem uso, reverificar
periodicamente. **Decidir antes do marco 5**, quando houver dinheiro na conta.

**Formato de armazenamento do telefone.** `11 98765-4321` e `+5511987654321` são
o mesmo número e duas chaves. Sem normalização canônica na escrita, a unicidade
de U1 é ficção. Recomendação: E.164, normalizado na borda, guardado só assim —
mas é decisão a tomar, não regra vigente.

**Troca de telefone de uma conta existente.** Muda o identificador de login.
Exige o quê — senha, verificação do número novo, verificação do antigo? E as
sessões abertas caem?

**Limite de tentativas.** Login e envio de código de verificação. Não é só
segurança: cada SMS custa, e cadastro de má-fé vira despesa (ADR-036,
consequências).

**Estado da conta.** Existe `Usuario` bloqueado globalmente? A ADR-011 põe toda
permissão no vínculo, o que sugere que revogar acesso é revogar vínculos — e que
estado global não existe. Sugere, não decide.

**Rotação e revogação do refresh token.** A ADR-015 registra nas consequências
negativas que isso vira responsabilidade própria, e que o `jti` existe para
suportá-lo. Onde esse estado mora — neste agregado, em outro, em Redis — não está
decidido.

**Exclusão da conta.** A ADR-013 dá a regra de anonimização; ninguém desenhou o
gatilho, nem o que acontece com os vínculos e o histórico de quem sai.

**O `nome`.** Obrigatório? Serve para quê além de aparecer no painel? É dado
pessoal retido (ADR-013 §1) e ninguém disse por quanto tempo nem para quê.
