-- O idx_abertura_por_loja da V7 repete a chave primária: os dois são b-tree
-- sobre (estabelecimento_id, expediente), e o PostgreSQL percorre um b-tree nos
-- dois sentidos — o "desc" do índice não acrescenta nada à leitura do histórico
-- de uma loja. Custava uma segunda escrita a cada abertura, para nada.
--
-- Migration nova, e não edição da V7: a V7 já rodou, e o Flyway grava o
-- checksum dela.

drop index idx_abertura_por_loja;
