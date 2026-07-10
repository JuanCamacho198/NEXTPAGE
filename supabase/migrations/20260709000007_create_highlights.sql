create table public.highlights (
  id           text primary key,
  user_id      uuid not null references auth.users(id) on delete cascade,
  book_id      text not null,
  cfi_range    text not null default '',
  text_content text not null default '',
  note         text,
  color        text not null default 'yellow',
  page         int,
  type         text,
  rect_json    jsonb,
  locator_json text,
  deleted_at   timestamptz,
  updated_at   timestamptz not null default now(),
  created_at   timestamptz not null default now()
);

create index idx_hl_user_book on public.highlights(user_id, book_id);
create index idx_hl_updated on public.highlights(updated_at desc);

alter table public.highlights enable row level security;

create policy "Users own highlights"
  on public.highlights for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

alter publication supabase_realtime add table public.highlights;
