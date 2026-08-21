-- Um banco lógico por serviço. Em produção, cada um com credencial própria;
-- em desenvolvimento, uma instância com bancos separados.
-- Nenhum serviço acessa o banco de outro.

CREATE DATABASE identity_db;
CREATE DATABASE merchant_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
CREATE DATABASE delivery_db;
CREATE DATABASE geolocation_db;

-- PostGIS apenas onde é usado.
\connect geolocation_db
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_topology;
