-- Um banco lógico por serviço. Em produção, cada um com credencial própria;
-- em desenvolvimento, uma instância com bancos separados.
-- Nenhum serviço acessa o banco de outro.
--
-- Seis bancos PostgreSQL e dois MongoDB — ADR-021.
-- Sem PostGIS: geoprocessamento saiu do MVP com a ADR-020, e extensão criada
-- "por precaução" é superfície que alguém acaba usando.

CREATE DATABASE identity_db;
CREATE DATABASE merchant_db;
CREATE DATABASE settlement_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE delivery_db;
