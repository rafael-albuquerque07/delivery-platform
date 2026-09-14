-- A V1 ainda era editável: ela nunca rodou contra banco que sobrevive -- só
-- contra Testcontainers, que morre no fim do teste. Mesmo assim isto é V2, e
-- não uma edição da V1: o commit da V1 já está publicado e foi o que o CI
-- validou. Editar o arquivo faria o repositório de hoje divergir do que aquele
-- commit provou, sem ganho nenhum além de um schema com menos linhas.

-- ── pausa ────────────────────────────────────────────────────────────────────
-- pausadoAte é Instant em UTC (ADR-025). "Pausado até as 21h" é hora civil só
-- na tela.
ALTER TABLE estabelecimento
    ADD COLUMN pausa_ativa  BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN pausa_ate    TIMESTAMPTZ,
    ADD COLUMN pausa_motivo VARCHAR(160);

-- O DEFAULT existia só para a coluna nascer NOT NULL numa tabela que já podia
-- ter linhas. Mantê-lo faria o banco aceitar um INSERT sem pausa, e o agregado
-- sempre a informa.
ALTER TABLE estabelecimento
    ALTER COLUMN pausa_ativa DROP DEFAULT;

ALTER TABLE estabelecimento
    ADD CONSTRAINT ck_estabelecimento_pausa_ativa_exige_motivo
        CHECK (pausa_ativa = FALSE OR pausa_motivo IS NOT NULL),
    ADD CONSTRAINT ck_estabelecimento_pausa_inativa_nao_guarda_nada
        CHECK (pausa_ativa = TRUE OR (pausa_ate IS NULL AND pausa_motivo IS NULL));

-- ── horário de funcionamento ─────────────────────────────────────────────────
-- Trio plano; o mapper reagrupa em Map<DayOfWeek, List<Faixa>> na volta.
--
-- TIME e não TIMESTAMPTZ, e isso não contradiz a ADR-025: a proibição é sobre
-- hora sem lugar num campo que registra QUANDO algo aconteceu. Aqui é hora
-- civil de propósito -- "abre às 18h" é configuração do calendário da loja, e
-- só vira instante quando alguém pergunta se ela está aberta, com o fusoHorario
-- na mão.
--
-- A chave primária recusa turno repetido no mesmo dia. O agregado recusa o
-- mesmo, e os dois precisam concordar sobre o que é um horário válido -- se só
-- o banco recusasse, a falha apareceria no flush em vez de na construção.
--
-- Sobreposição entre turnos diferentes continua PERMITIDA: "aberta" é um OU
-- sobre as faixas, então sobrepor é redundância, não contradição.
CREATE TABLE estabelecimento_horario (
    estabelecimento_id UUID        NOT NULL REFERENCES estabelecimento (id) ON DELETE CASCADE,
    dia_da_semana      VARCHAR(12) NOT NULL,
    inicio             TIME        NOT NULL,
    fim                TIME        NOT NULL,

    PRIMARY KEY (estabelecimento_id, dia_da_semana, inicio, fim),

    -- Início igual ao fim é ambíguo: nem turno vazio nem vinte e quatro horas.
    CONSTRAINT ck_estabelecimento_horario_faixa_nao_degenerada
        CHECK (inicio <> fim)
);

-- A faixa que cruza a meia-noite é `fim < inicio`, e é VÁLIDA -- a pizzaria que
-- abre às 18h e fecha às 2h. Não há CHECK exigindo inicio < fim, e a ausência é
-- a regra, não esquecimento.
COMMENT ON TABLE estabelecimento_horario IS 'Turnos por dia da semana, hora civil no fuso do estabelecimento. fim < inicio significa que o turno atravessa a meia-noite e pertence ao dia de inicio.';
