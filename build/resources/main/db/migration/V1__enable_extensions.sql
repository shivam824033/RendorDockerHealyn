-- Healyn V1: required PostgreSQL extensions.
-- citext   : case-insensitive text (email)
-- btree_gist: composite indexes for EXCLUDE constraints (appointment conflicts, blackouts)
-- pg_trgm  : trigram indexes for fuzzy patient name search
-- pgcrypto : crypto primitives (UUID v4 fallback, digests if needed in SQL)

CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
