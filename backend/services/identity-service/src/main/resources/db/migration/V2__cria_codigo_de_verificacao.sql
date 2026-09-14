-- ADR-042 — a prova de posse do telefone, antes de existir conta.
--
-- Sem chave estrangeira para `usuario`: a linha existe justamente enquanto o
-- usuário não existe.
--
-- O código é TEXTO e fica em CLARO. Enquanto o transporte for humano, esta
-- tabela É o canal de entrega, e um código com hash não pode ser lido por quem
-- precisa entregá-lo (ADR-042 §2). Texto e não número porque 004291 é um código
-- válido e como número vira 4291.
CREATE TABLE codigo_de_verificacao (
    id        UUID PRIMARY KEY,
    telefone  VARCHAR(16) NOT NULL,
    codigo    VARCHAR(6)  NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    expira_em TIMESTAMPTZ NOT NULL,
    tentativas INTEGER NOT NULL DEFAULT 0,

    -- Um código por telefone. Pedir de novo substitui o anterior, e é isto que
    -- impede a tabela de crescer com o número de pedidos em vez de com o
    -- número de telefones.
    CONSTRAINT uq_codigo_telefone UNIQUE (telefone),

    CONSTRAINT ck_codigo_expira_depois_de_criado CHECK (expira_em > criado_em),
    CONSTRAINT ck_codigo_tentativas_nao_negativas CHECK (tentativas >= 0)
);
