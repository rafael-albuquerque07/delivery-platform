-- Áreas de entrega (estabelecimento.md §5, ADR-020). O modelo é por BAIRRO
-- NOMEADO: é a palavra que o cliente diz na conversa. A faixa de CEP é o
-- refinamento opcional de quem quer resolver o endereço sem perguntar.

CREATE TABLE estabelecimento_area_entrega (
    estabelecimento_id        UUID          NOT NULL REFERENCES estabelecimento (id) ON DELETE CASCADE,

    -- Derivado do nome: maiúsculas, sem acento, sem espaço duplo, aparado.
    -- É gravado porque é a CHAVE da área dentro da loja, e é a chave primária
    -- abaixo que faz M9 valer também no banco: "Boa Viagem" e "boa viagem" são
    -- a mesma área, e cadastrar as duas com taxas diferentes é o defeito que
    -- E1 nomeia.
    identificador_normalizado VARCHAR(80)   NOT NULL,

    -- Como o cliente vê, com acento e caixa que o comerciante escolheu.
    nome                      VARCHAR(80)   NOT NULL,

    -- ADR-009: Money persiste como o valor, escala 2. Zero é taxa VÁLIDA, e é
    -- diferente de não haver área -- M11. Quem procura recebe Optional, nunca
    -- um zero por falta de resposta.
    taxa                      NUMERIC(19,2) NOT NULL,

    ativa                     BOOLEAN       NOT NULL,

    PRIMARY KEY (estabelecimento_id, identificador_normalizado),

    -- E3
    CONSTRAINT ck_estabelecimento_area_taxa_nao_negativa
        CHECK (taxa >= 0)
);

-- Faixas de CEP, inclusivas nas duas pontas. O CEP é texto de oito dígitos:
-- guardado como número, o 01310-100 -- a Avenida Paulista -- vira 1310100 e o
-- zero da frente só volta se alguém lembrar de formatar. Com largura fixa, a
-- comparação lexicográfica É a comparação numérica.
--
-- M10 -- faixas que não se sobrepõem entre as áreas ATIVAS da loja -- não cabe
-- aqui: é uma regra sobre o conjunto das linhas, e SQL não a expressa sem
-- gatilho ou índice de exclusão. Ela mora no agregado, e o
-- AreaDeEntregaTest é quem a prova.
CREATE TABLE estabelecimento_area_faixa_cep (
    estabelecimento_id        UUID        NOT NULL,
    identificador_normalizado VARCHAR(80) NOT NULL,
    cep_inicio                VARCHAR(8)  NOT NULL,
    cep_fim                   VARCHAR(8)  NOT NULL,

    PRIMARY KEY (estabelecimento_id, identificador_normalizado, cep_inicio, cep_fim),

    -- A faixa pertence a uma área que existe. DEFERRABLE INITIALLY DEFERRED
    -- porque as duas tabelas são coleções independentes do mesmo agregado e o
    -- Hibernate não promete a ordem em que as insere: sem o adiamento, uma
    -- inserção de faixa antes da área derrubaria a transação por ordem, não por
    -- dado errado.
    CONSTRAINT fk_estabelecimento_area_faixa_cep_area
        FOREIGN KEY (estabelecimento_id, identificador_normalizado)
        REFERENCES estabelecimento_area_entrega (estabelecimento_id, identificador_normalizado)
        ON DELETE CASCADE
        DEFERRABLE INITIALLY DEFERRED,

    CONSTRAINT ck_estabelecimento_area_faixa_ordenada
        CHECK (cep_inicio <= cep_fim),

    CONSTRAINT ck_estabelecimento_area_faixa_oito_digitos
        CHECK (cep_inicio ~ '^[0-9]{8}$' AND cep_fim ~ '^[0-9]{8}$')
);
