-- One schema per module; modules read and write only their own schema (設計_004 2.1).
-- The shared schema is created by Flyway itself, because it holds Flyway's history table.
-- Schemas of later stages (flashsale, coupon, ...) are added with their tables.
CREATE SCHEMA IF NOT EXISTS account;
CREATE SCHEMA IF NOT EXISTS catalog;
CREATE SCHEMA IF NOT EXISTS inventory;
CREATE SCHEMA IF NOT EXISTS cart;
CREATE SCHEMA IF NOT EXISTS ordering;
CREATE SCHEMA IF NOT EXISTS payment;
CREATE SCHEMA IF NOT EXISTS ledger;
CREATE SCHEMA IF NOT EXISTS settlement;
