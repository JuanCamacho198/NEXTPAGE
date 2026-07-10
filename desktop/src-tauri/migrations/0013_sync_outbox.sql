create table if not exists sync_outbox (
  id           text primary key,
  entity_type  text not null,
  entity_id    text,
  operation    text not null check(operation in ('UPSERT', 'DELETE')),
  payload_json text not null,
  retry_count  int not null default 0,
  last_error   text,
  created_at   text not null default (datetime('now')),
  next_retry_at text not null default (datetime('now'))
);

create index if not exists idx_sync_outbox_next_retry on sync_outbox(next_retry_at);
