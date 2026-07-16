-- 合伙云 Pro V1.2.1
-- PostgreSQL 16+ / UTF-8
CREATE SCHEMA IF NOT EXISTS hhy;
SET search_path TO hhy, public;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE OR REPLACE FUNCTION hhy.set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := clock_timestamp();
  RETURN NEW;
END;
$$;
