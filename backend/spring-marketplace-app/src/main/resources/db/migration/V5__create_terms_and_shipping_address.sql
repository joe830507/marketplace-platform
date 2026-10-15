-- Terms documents, consents and shipping addresses (設計_004 3). Terms and consents are insert only.

CREATE TABLE account.terms_document (
  id            uuid        PRIMARY KEY,
  document_type text        NOT NULL
                CHECK (document_type IN ('TERMS_OF_SERVICE', 'PRIVACY_POLICY', 'SELLER_TERMS')),
  version       integer     NOT NULL CHECK (version >= 1),
  content       text        NOT NULL,
  published_at  timestamptz NOT NULL,
  published_by  text        NOT NULL,
  UNIQUE (document_type, version)
);

CREATE TABLE account.terms_consent (
  id                uuid        PRIMARY KEY,
  account_type      text        NOT NULL CHECK (account_type IN ('MEMBER', 'SELLER_USER')),
  account_id        uuid        NOT NULL,
  seller_id         uuid,
  terms_document_id uuid        NOT NULL REFERENCES account.terms_document (id),
  consented_at      timestamptz NOT NULL,
  UNIQUE (account_type, account_id, terms_document_id)
);

-- Whether a seller's administrator has accepted the latest seller terms.
CREATE INDEX terms_consent_seller_idx
  ON account.terms_consent (seller_id, terms_document_id)
  WHERE seller_id IS NOT NULL;

CREATE TABLE account.shipping_address (
  id             uuid        PRIMARY KEY,
  member_id      uuid        NOT NULL REFERENCES account.member (id),
  recipient_name text        NOT NULL,
  phone          text        NOT NULL,
  address        text        NOT NULL,
  is_default     boolean     NOT NULL,
  deleted_at     timestamptz,
  created_at     timestamptz NOT NULL,
  created_by     text        NOT NULL,
  updated_at     timestamptz NOT NULL,
  updated_by     text        NOT NULL,
  version        bigint      NOT NULL
);

CREATE INDEX shipping_address_member_idx ON account.shipping_address (member_id);

-- At most one default address per member among the addresses that are not deleted.
CREATE UNIQUE INDEX shipping_address_one_default
  ON account.shipping_address (member_id)
  WHERE is_default AND deleted_at IS NULL;

-- Seller lists are ordered by creation time, newest first (需求 3.11).
CREATE INDEX seller_created_at_idx ON account.seller (created_at);
CREATE INDEX operator_user_created_at_idx ON account.operator_user (created_at);
