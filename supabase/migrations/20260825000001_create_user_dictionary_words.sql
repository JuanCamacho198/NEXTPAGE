-- 20260825000001_create_user_dictionary_words.sql
-- PR1 Dictionary Foundation: per-user dictionary with RLS + realtime

create table if not exists public.user_dictionary_words (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  word text not null check (char_length(trim(word)) between 1 and 200),
  normalized_word text not null,
  tags jsonb not null default '[]'::jsonb,
  is_favorite boolean not null default false,
  srs_stage int not null default 0 check (srs_stage between 0 and 5),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (user_id, normalized_word)
);

create index if not exists idx_udw_user on public.user_dictionary_words (user_id);
create index if not exists idx_udw_norm on public.user_dictionary_words (normalized_word);
create index if not exists idx_udw_updated on public.user_dictionary_words (updated_at);
create index if not exists idx_udw_fav on public.user_dictionary_words (user_id) where is_favorite;

alter table public.user_dictionary_words enable row level security;

drop policy if exists "udw_select" on public.user_dictionary_words;
create policy "udw_select" on public.user_dictionary_words for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "udw_insert" on public.user_dictionary_words;
create policy "udw_insert" on public.user_dictionary_words for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists "udw_update" on public.user_dictionary_words;
create policy "udw_update" on public.user_dictionary_words for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

drop policy if exists "udw_delete" on public.user_dictionary_words;
create policy "udw_delete" on public.user_dictionary_words for delete to authenticated using ((select auth.uid()) = user_id);

grant select, insert, update, delete on public.user_dictionary_words to authenticated;

-- realtime + replica identity full (LWW needs old/new)
alter publication supabase_realtime add table public.user_dictionary_words;
alter table public.user_dictionary_words replica identity full;
