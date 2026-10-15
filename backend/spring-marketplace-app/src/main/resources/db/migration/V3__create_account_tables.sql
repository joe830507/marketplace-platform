-- Account module (設計_004 3). Terms and shipping addresses are added with their APIs.
-- Usernames are unique ignoring case; queries compare lower(username) so they use these indexes.

CREATE TABLE account.member (
  id                 uuid        PRIMARY KEY,
  username           text        NOT NULL,
  password_hash      text        NOT NULL,
  display_name       text        NOT NULL,
  status             text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
  failed_login_count integer     NOT NULL DEFAULT 0,
  locked_until       timestamptz,
  created_at         timestamptz NOT NULL,
  created_by         text        NOT NULL,
  updated_at         timestamptz NOT NULL,
  updated_by         text        NOT NULL,
  version            bigint      NOT NULL
);

CREATE UNIQUE INDEX member_username_key ON account.member (lower(username));

CREATE TABLE account.seller (
  id          uuid        PRIMARY KEY,
  name        text        NOT NULL,
  seller_type text        NOT NULL CHECK (seller_type IN ('FIRST_PARTY', 'THIRD_PARTY')),
  status      text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
  created_at  timestamptz NOT NULL,
  created_by  text        NOT NULL,
  updated_at  timestamptz NOT NULL,
  updated_by  text        NOT NULL,
  version     bigint      NOT NULL
);

CREATE TABLE account.seller_user (
  id                   uuid        PRIMARY KEY,
  seller_id            uuid        NOT NULL REFERENCES account.seller (id),
  username             text        NOT NULL,
  password_hash        text        NOT NULL,
  role                 text        NOT NULL CHECK (role IN ('ADMIN', 'MEMBER')),
  status               text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
  failed_login_count   integer     NOT NULL DEFAULT 0,
  locked_until         timestamptz,
  must_change_password boolean     NOT NULL DEFAULT false,
  created_at           timestamptz NOT NULL,
  created_by           text        NOT NULL,
  updated_at           timestamptz NOT NULL,
  updated_by           text        NOT NULL,
  version              bigint      NOT NULL
);

CREATE UNIQUE INDEX seller_user_username_key ON account.seller_user (lower(username));
CREATE INDEX seller_user_seller_id_idx ON account.seller_user (seller_id);

CREATE TABLE account.operator_user (
  id                   uuid        PRIMARY KEY,
  username             text        NOT NULL,
  password_hash        text        NOT NULL,
  status               text        NOT NULL CHECK (status IN ('ACTIVE', 'DISABLED')),
  failed_login_count   integer     NOT NULL DEFAULT 0,
  locked_until         timestamptz,
  must_change_password boolean     NOT NULL DEFAULT false,
  created_at           timestamptz NOT NULL,
  created_by           text        NOT NULL,
  updated_at           timestamptz NOT NULL,
  updated_by           text        NOT NULL,
  version              bigint      NOT NULL
);

CREATE UNIQUE INDEX operator_user_username_key ON account.operator_user (lower(username));

-- Role assignments are only inserted or deleted, never updated.
CREATE TABLE account.operator_user_role (
  operator_user_id uuid        NOT NULL REFERENCES account.operator_user (id),
  role             text        NOT NULL
                   CHECK (role IN ('ADMIN', 'CAMPAIGN', 'CUSTOMER_SERVICE', 'FINANCE')),
  created_at       timestamptz NOT NULL,
  created_by       text        NOT NULL,
  PRIMARY KEY (operator_user_id, role)
);

-- Only the hash of a refresh token is stored, never the token itself (ADR_009).
CREATE TABLE account.refresh_token (
  id           uuid        PRIMARY KEY,
  account_type text        NOT NULL CHECK (account_type IN ('MEMBER', 'SELLER_USER', 'OPERATOR')),
  account_id   uuid        NOT NULL,
  token_hash   text        NOT NULL UNIQUE,
  expires_at   timestamptz NOT NULL,
  revoked_at   timestamptz,
  replaced_by  uuid,
  created_at   timestamptz NOT NULL
);

-- Used to revoke every token of an account.
CREATE INDEX refresh_token_active_account_idx
  ON account.refresh_token (account_type, account_id)
  WHERE revoked_at IS NULL;
