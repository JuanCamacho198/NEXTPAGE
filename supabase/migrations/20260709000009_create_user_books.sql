create table public.user_books (
  id            text not null,
  user_id       uuid not null references auth.users(id) on delete cascade,
  title         text not null,
  author        text,
  format        text not null,
  content_hash  text,
  file_path     text,
  cover_url     text,
  description   text,
  total_pages   int,
  source_device text,
  imported_at   timestamptz not null default now(),
  updated_at    timestamptz not null default now(),

  primary key (user_id, id)
);

create index idx_user_books_updated on public.user_books(updated_at desc);
create index idx_user_books_hash on public.user_books(content_hash) where content_hash is not null;

alter table public.user_books enable row level security;

create policy "Users own user_books"
  on public.user_books for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

alter publication supabase_realtime add table public.user_books;

alter table public.user_books replica identity full;
