create table public.reading_progress (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null,
  book_id     text not null,
  cfi_location text,
  percentage  real,
  current_page int,
  locator_json text,
  version     int not null default 1,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz not null default now(),
  unique(user_id, book_id)
);

alter table public.reading_progress enable row level security;

create policy "Users can view own reading progress"
  on public.reading_progress for select
  using (auth.uid() = user_id);

create policy "Users can insert own reading progress"
  on public.reading_progress for insert
  with check (auth.uid() = user_id);

create policy "Users can update own reading progress"
  on public.reading_progress for update
  using (auth.uid() = user_id);

create index idx_reading_progress_user_book on public.reading_progress(user_id, book_id);
create index idx_reading_progress_updated on public.reading_progress(updated_at desc);

alter publication supabase_realtime add table public.reading_progress;
