-- O vínculo entre um usuário e um estabelecimento (`estabelecimento.md` §1).
--
-- M1 é a FORMA desta tabela: a permissão pendura no vínculo, e o vínculo é o
-- par. Não há coluna de permissão em lugar nenhum que se refira só ao usuário.
CREATE TABLE membro (
    id                 UUID PRIMARY KEY,

    -- SEM chave estrangeira, e não é esquecimento: `usuario` mora no banco do
    -- identity-service. A integridade entre serviços é do domínio, não do
    -- banco — e um FK aqui seria o primeiro passo para os dois esquemas
    -- virarem um só.
    usuario_id         UUID NOT NULL,

    -- COM chave estrangeira: agregados diferentes, mesmo serviço, mesmo banco.
    -- Um vínculo para uma loja que não existe não é estado válido de agregado
    -- nenhum — é lixo. Seria impossível no dia em que `Membro` mudasse de
    -- serviço, e nesse dia a linha sai junto.
    estabelecimento_id UUID NOT NULL REFERENCES estabelecimento(id),

    papel              VARCHAR(16) NOT NULL,
    estado             VARCHAR(16) NOT NULL,
    criado_em          TIMESTAMPTZ NOT NULL,
    alterado_em        TIMESTAMPTZ NOT NULL,

    -- Um vínculo por par. É o que faz REMOVIDO ser um estado e não uma
    -- exclusão: recontratar reusa a linha, e o histórico de quem teve acesso
    -- àquela loja não se perde ao "limpar" (ADR-029).
    CONSTRAINT uq_membro_usuario_loja UNIQUE (usuario_id, estabelecimento_id)
);

-- O caminho quente da autorização já está coberto pelo índice único acima.
-- Este cobre o caminho frio: carregar a equipe inteira de uma loja.
CREATE INDEX idx_membro_estabelecimento ON membro (estabelecimento_id);

CREATE TABLE membro_permissao (
    membro_id UUID NOT NULL REFERENCES membro(id) ON DELETE CASCADE,
    permissao VARCHAR(24) NOT NULL,

    PRIMARY KEY (membro_id, permissao)
);

-- A3 não aparece aqui, e não é omissão. "Pelo menos um administrador ativo por
-- loja" não se escreve como CHECK nem como índice: é condição sobre o conjunto
-- de linhas, e o SQL declarativo só a expressa com gatilho. Ela vive na classe
-- Equipe, e o que a torna confiável sob concorrência é o `for update` na linha
-- de `estabelecimento` que o MembroRepositorio toma antes de contar.
