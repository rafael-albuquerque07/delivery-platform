-- O convite para entrar na equipe (`estabelecimento.md` §2).
--
-- O token fica em CLARO, pelo mesmo argumento da ADR-042 §2: enquanto o
-- transporte for humano, esta tabela É o canal de entrega, e um token com hash
-- não pode ser lido por quem precisa entregá-lo. A mesma condição de término
-- vale — no dia em que o CanalPort do conversation existir, vira hash.
CREATE TABLE convite (
    id                 UUID PRIMARY KEY,
    estabelecimento_id UUID NOT NULL REFERENCES estabelecimento(id),

    -- O endereço, não a autorização. Quem autoriza é o token: conferir o
    -- telefone exigiria perguntar ao identity-service qual é o telefone de um
    -- usuário, e essa porta não existe.
    telefone           VARCHAR(16) NOT NULL,
    token              VARCHAR(64) NOT NULL,

    -- O VÍNCULO que convidou, não o usuário. M1: permissão pertence ao
    -- vínculo, e é esse vínculo que A2 reconfere no aceite. A chave
    -- estrangeira garante, no banco, que quem convidou era da casa.
    convidado_por      UUID NOT NULL REFERENCES membro(id),

    estado             VARCHAR(16) NOT NULL,
    criado_em          TIMESTAMPTZ NOT NULL,
    expira_em          TIMESTAMPTZ NOT NULL,
    aceito_em          TIMESTAMPTZ,

    CONSTRAINT uq_convite_token UNIQUE (token),
    CONSTRAINT ck_convite_expira_depois_de_criado CHECK (expira_em > criado_em),

    -- Os dois campos contam a mesma coisa e precisam concordar. Sem isto,
    -- um convite ACEITO sem data seria um aceite sem quando.
    CONSTRAINT ck_convite_aceito_tem_data
        CHECK ((estado = 'ACEITO') = (aceito_em IS NOT NULL))
);

-- Um convite pendente por (loja, telefone). Reconvidar substitui, como o
-- código de verificação faz por telefone — senão a pessoa recebe três tokens
-- válidos e usa o que achar primeiro.
--
-- Índice PARCIAL, e é o que torna a regra possível: convites ACEITO e
-- CANCELADO do mesmo par continuam lá, que é o histórico de quem foi chamado
-- e quando.
CREATE UNIQUE INDEX uq_convite_pendente_por_telefone
    ON convite (estabelecimento_id, telefone) WHERE estado = 'PENDENTE';

CREATE INDEX idx_convite_estabelecimento ON convite (estabelecimento_id);

CREATE TABLE convite_permissao (
    convite_id UUID NOT NULL REFERENCES convite(id) ON DELETE CASCADE,
    permissao  VARCHAR(24) NOT NULL,

    PRIMARY KEY (convite_id, permissao)
);

-- EXPIRADO não é estado, e não está aqui. Expirar é o relógio passando de
-- expira_em, não algo que alguém faz ao convite — guardá-lo exigiria uma
-- rotina varrendo a tabela, e uma rotina que ninguém escreveu é peça que nunca
-- roda. O domínio deriva.
