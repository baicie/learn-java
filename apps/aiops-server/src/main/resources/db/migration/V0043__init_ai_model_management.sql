create table if not exists ai_model_config (
  id varchar(64) primary key,
  tenant_id varchar(64) not null references tenant(id) on delete cascade,
  provider varchar(32) not null check (provider = 'deepseek'),
  name varchar(160) not null,
  model_name varchar(160) not null,
  base_url varchar(512) not null,
  encrypted_api_key text not null,
  enabled boolean not null default true,
  is_default boolean not null default false,
  last_test_status varchar(16) check (last_test_status in ('success', 'failed')),
  last_test_message varchar(500),
  last_tested_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uk_ai_model_config_tenant_name unique (tenant_id, name)
);

create index if not exists idx_ai_model_config_tenant_updated
  on ai_model_config(tenant_id, updated_at desc);

create unique index if not exists uk_ai_model_config_tenant_default
  on ai_model_config(tenant_id) where is_default;
