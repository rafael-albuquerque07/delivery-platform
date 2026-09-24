-- O outbox da invariante 7.
--
-- Uma linha aqui e o fato que a originou são gravados na MESMA transação. Não
-- existe instante em que o vínculo mudou e o evento não vai sair — é isso, e só
-- isso, que a invariante pede (ADR-043 §1).
--
-- A tabela é uma cópia durável do payload do evento. A regra da CLAUDE.md sobre
-- log vale inteira para ela: o que não pode ir para o log não pode ir para o
-- outbox. Nome, telefone, documento, dado de pagamento e coordenada exata não
-- entram — nem aqui, nem em `ultimo_erro`.

create table outbox (
    id            uuid         primary key,
    tipo          varchar(120) not null,
    versao        smallint     not null,
    agregado      varchar(60)  not null,
    agregado_id   uuid         not null,
    chave_de_rota varchar(200) not null,
    payload       jsonb        not null,
    ocorrido_em   timestamptz  not null,
    publicado_em  timestamptz,
    tentativas    smallint     not null default 0,
    ultimo_erro   text,

    constraint ck_outbox_versao     check (versao > 0),
    constraint ck_outbox_tentativas check (tentativas >= 0)
);

-- Índice PARCIAL: só as linhas pendentes.
--
-- A tabela cresce para sempre; este índice não. Ele guarda apenas o que
-- `publicado_em is null` deixa passar, então continua do tamanho da fila em vez
-- de crescer com o histórico. É o mesmo raciocínio do índice único parcial do
-- convite (V5), pelo mesmo motivo: o índice serve a uma consulta, e a consulta
-- do relay só enxerga pendentes.
create index idx_outbox_pendente
    on outbox (ocorrido_em)
    where publicado_em is null;

comment on table outbox is
    'Eventos gravados na transação do fato e publicados depois pelo relay (ADR-043).';
comment on column outbox.id is
    'Também é o eventoId do payload: a chave de idempotência que o contrato exige do consumidor.';
comment on column outbox.publicado_em is
    'Nulo enquanto pendente. O índice parcial vive desta coluna.';
comment on column outbox.ultimo_erro is
    'Diagnóstico da última tentativa. Vale a regra de log: nada de segredo, documento ou coordenada.';
