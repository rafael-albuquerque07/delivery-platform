CREATE TABLE estabelecimento (
    id                                 UUID PRIMARY KEY,
    nome                               VARCHAR(120)  NOT NULL,

    -- Só dígitos: 11 (CPF) ou 14 (CNPJ). Não é UNIQUE de propósito —
    -- estabelecimento.md §9 diz que não há limite de lojas por usuário, e um MEI
    -- com dois pontos de venda repete o CPF. Unicidade aqui recusaria a segunda
    -- loja em silêncio.
    documento                          VARCHAR(14)   NOT NULL,

    telefone                           VARCHAR(16)   NOT NULL,

    -- ADR-013: endereço textual e bairro, nunca coordenada.
    endereco_textual                   VARCHAR(240)  NOT NULL,
    bairro                             VARCHAR(80)   NOT NULL,

    -- M16: identificador IANA do conjunto brasileiro, nunca nulo. O maior hoje
    -- é America/Porto_Velho, com 19 caracteres; 40 dá folga sem convidar lixo.
    fuso_horario                       VARCHAR(40)   NOT NULL,

    tipo_de_operacao                   VARCHAR(16)   NOT NULL,

    -- ADR-009: Money persiste como o valor, escala 2. A moeda mora no tipo.
    desconto_de_retirada               NUMERIC(19,2) NOT NULL,
    fundo_maximo_de_troco              NUMERIC(19,2) NOT NULL,

    aceita_pedido_sem_troco_disponivel BOOLEAN       NOT NULL,

    -- M15
    CONSTRAINT ck_estabelecimento_desconto_de_retirada_nao_negativo
        CHECK (desconto_de_retirada >= 0),

    -- Simétrica: dívida de troco não existe.
    CONSTRAINT ck_estabelecimento_fundo_de_troco_nao_negativo
        CHECK (fundo_maximo_de_troco >= 0),

    CONSTRAINT ck_estabelecimento_documento_11_ou_14_digitos
        CHECK (documento ~ '^[0-9]{11}$' OR documento ~ '^[0-9]{14}$')
);

-- A matriz de métodos aceitos (estabelecimento.md §4). Par plano; o mapper
-- reagrupa em Map<Modalidade, Set<MetodoPagamento>> na volta.
--
-- M12 exige que a loja aceite ao menos uma modalidade, e que nenhuma modalidade
-- aceita fique sem método. Nenhuma das duas cabe num CHECK: são regras sobre a
-- AUSÊNCIA de linhas em tabela filha, e SQL não as expressa sem gatilho. Ficam
-- no agregado, e o EstabelecimentoTest é quem as prova.
CREATE TABLE estabelecimento_metodo_aceito (
    estabelecimento_id UUID        NOT NULL REFERENCES estabelecimento (id) ON DELETE CASCADE,
    modalidade         VARCHAR(16) NOT NULL,
    metodo             VARCHAR(16) NOT NULL,

    PRIMARY KEY (estabelecimento_id, modalidade, metodo)
);

-- M17: entrada para toda modalidade aceita, e para nenhuma outra. A totalidade
-- também é regra do agregado, pelo mesmo motivo. O que o banco garante é o sinal.
--
-- Zero é valor válido e significa "sem mínimo" — não é ausência de configuração.
-- A ausência seria confundida com zero, que é o erro que M11 já nomeia do outro
-- lado, em "ausência de área ≠ taxa zero".
CREATE TABLE estabelecimento_pedido_minimo (
    estabelecimento_id UUID          NOT NULL REFERENCES estabelecimento (id) ON DELETE CASCADE,
    modalidade         VARCHAR(16)   NOT NULL,
    valor              NUMERIC(19,2) NOT NULL,

    PRIMARY KEY (estabelecimento_id, modalidade),

    CONSTRAINT ck_estabelecimento_pedido_minimo_nao_negativo
        CHECK (valor >= 0)
);
