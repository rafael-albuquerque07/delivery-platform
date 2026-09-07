CREATE TABLE usuario (
    id                     UUID PRIMARY KEY,
    nome                   VARCHAR(120) NOT NULL,
    telefone               VARCHAR(16) NOT NULL,
    telefone_verificado_em TIMESTAMPTZ NOT NULL,
    email                  VARCHAR(254),
    email_verificado_em    TIMESTAMPTZ,
    hash_da_senha          VARCHAR(120) NOT NULL,

    CONSTRAINT uq_usuario_telefone UNIQUE (telefone),

    -- U3: canal só recupera com verificadoEm != null. Sem e-mail, não há o
    -- que verificar.
    CONSTRAINT ck_usuario_email_verificado_exige_email
        CHECK (email IS NOT NULL OR email_verificado_em IS NULL)
);

-- U2: e-mail é opcional; quando presente, único. Índice parcial em vez de
-- UNIQUE na coluna para deixar explícito que múltiplos NULL coexistem.
CREATE UNIQUE INDEX uq_usuario_email ON usuario (email) WHERE email IS NOT NULL;
