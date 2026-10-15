-- Runs once, when the data volume is first created.
-- The platform and the mock gateway share one container locally but are separate databases with
-- separate users, and share no data (設計_003 3.1).
CREATE USER marketplace WITH PASSWORD 'marketplace';
CREATE DATABASE marketplace OWNER marketplace;

CREATE USER gateway WITH PASSWORD 'gateway';
CREATE DATABASE gateway OWNER gateway;
