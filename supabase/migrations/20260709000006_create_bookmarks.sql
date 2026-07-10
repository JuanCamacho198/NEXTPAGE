create table public.bookmarks (
  id            text primary key,
  user_id       uuid not null references auth.users(id) on delete cascade,
  book_id       text not null,
  cfi_location  text not null default '',
  title_snippet text,
  locator_json  text,
  deleted_at    timestamptz,
  updated_at    timestamptz not null default now(),
  created_at    timestamptz not null default now()
);

create unique index idx_bm_active on public.bookmarks(user_id, book_id, coalesce(cfi_location, '')) where deleted_at is null;
create index idx_bm_user_book on public.bookmarks(user_id, book_id);
create index idx_bm_updated on public.bookmarks(updated_at desc);

alter table public.bookmarks enable row level security;

create policy "Users own bookmarks"
  on public.bookmarks for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

alter publication supabase_realtime add table public.bookmarks;
