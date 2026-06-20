-- Phase 6.2: Knowledge Base & Vector Retrieval.
-- Portable MVP implementation:
-- - Embeddings are stored as jsonb numeric arrays.
-- - Java service performs cosine rerank.
-- - No pgvector / Milvus dependency in this phase.
-- - No execution capability is added.

create table if not exists kb_document (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  source_type varchar(64) not null,
  source_id varchar(64) not null,
  title varchar(240) not null,
  status varchar(32) not null default 'indexed',
  metadata jsonb not null default '{}'::jsonb,
  indexed_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_kb_document_source_type
    check (source_type in ('incident_case', 'postmortem', 'manual')),
  constraint ck_kb_document_status
    check (status in ('indexed', 'stale', 'disabled'))
);

create unique index if not exists uq_kb_document_source
  on kb_document(tenant_id, source_type, source_id);

create index if not exists idx_kb_document_status
  on kb_document(tenant_id, status, indexed_at desc);

create table if not exists kb_chunk (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  document_id varchar(64) not null references kb_document(id) on delete cascade,
  chunk_order int not null,
  source_type varchar(64) not null,
  source_id varchar(64) not null,
  title varchar(240) not null,
  content text not null,
  content_hash varchar(128) not null,
  token_estimate int not null default 0,
  embedding jsonb not null default '[]'::jsonb,
  metadata jsonb not null default '{}'::jsonb,
  status varchar(32) not null default 'indexed',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_kb_chunk_source_type
    check (source_type in ('incident_case', 'postmortem', 'manual')),
  constraint ck_kb_chunk_status
    check (status in ('indexed', 'stale', 'disabled')),
  constraint uq_kb_chunk_document_order unique (tenant_id, document_id, chunk_order)
);

create index if not exists idx_kb_chunk_document
  on kb_chunk(tenant_id, document_id, chunk_order);

create index if not exists idx_kb_chunk_source
  on kb_chunk(tenant_id, source_type, source_id);

create index if not exists idx_kb_chunk_content_hash
  on kb_chunk(tenant_id, content_hash);

create table if not exists kb_search_log (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  query text not null,
  source_types jsonb not null default '[]'::jsonb,
  tags jsonb not null default '[]'::jsonb,
  top_k int not null default 5,
  result_count int not null default 0,
  created_by varchar(64) not null,
  created_at timestamptz not null default now(),
  constraint ck_kb_search_log_top_k check (top_k >= 1 and top_k <= 50)
);

create index if not exists idx_kb_search_log_tenant_created
  on kb_search_log(tenant_id, created_at desc);
