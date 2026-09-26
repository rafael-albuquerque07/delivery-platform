-- A marca d'água da abertura do expediente (ADR-046).
--
-- NÃO é um cache de "está aberta". É o registro de um ato: *publiquei a
-- abertura do expediente D para a loja X*. A regra do estabelecimento.md §4
-- vale inteira — nenhuma rotina passa limpando, e nenhum campo aqui fala do
-- estado atual da loja, só do que já foi publicado.
--
-- A CHAVE PRIMÁRIA É A IDEMPOTÊNCIA. Duas instâncias da varredura disputam a
-- mesma chave e o PostgreSQL decide: exatamente uma insere, exatamente um
-- evento sai. Não há leitura-antes-da-escrita para correr, e portanto não há
-- janela. É a mesma forma do índice único que decide a corrida de cadastro no
-- identity (C-B).
--
-- De quebra, isto é o histórico de expedientes abertos por loja — que é o que
-- o marco 6 vai querer para o fechamento.

create table abertura_de_expediente (
    estabelecimento_id uuid        not null,
    expediente         date        not null,
    publicado_em       timestamptz not null,

    constraint pk_abertura_de_expediente
        primary key (estabelecimento_id, expediente),

    constraint fk_abertura_estabelecimento
        foreign key (estabelecimento_id) references estabelecimento (id)
);

-- Consulta do histórico de uma loja, e a varredura não precisa de índice:
-- ela só tenta inserir, e a própria chave primária resolve.
create index idx_abertura_por_loja
    on abertura_de_expediente (estabelecimento_id, expediente desc);

comment on table abertura_de_expediente is
    'Expedientes cuja abertura já foi publicada. A PK é a idempotência da varredura (ADR-046).';
comment on column abertura_de_expediente.expediente is
    'O dia operacional (ADR-025), não a data civil: 01:30 de domingo pertence ao expediente de sábado.';
