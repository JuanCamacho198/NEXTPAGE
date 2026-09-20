-- 20260919000001_dictionary_rich_entries.sql
-- Dictionary rich entries: ten nullable columns on public.user_dictionary_words.
-- Four user-authored fields (definition, part_of_speech, phonetic, example) and six
-- reader-captured evidence fields (quote, source_book_id, source_book_title,
-- source_book_author, source_chapter, source_locator).
-- Additive and nullable only: no default, no backfill, no index, and no change to
-- RLS, grants, or the realtime publication. Pre-existing rows stay valid and read
-- NULL for every new column (REQ-DRE-001, REQ-DRE-012, REQ-DRE-013).

alter table public.user_dictionary_words add column if not exists definition text;
alter table public.user_dictionary_words add column if not exists part_of_speech text;
alter table public.user_dictionary_words add column if not exists phonetic text;
alter table public.user_dictionary_words add column if not exists example text;
alter table public.user_dictionary_words add column if not exists quote text;
alter table public.user_dictionary_words add column if not exists source_book_id text;
alter table public.user_dictionary_words add column if not exists source_book_title text;
alter table public.user_dictionary_words add column if not exists source_book_author text;
alter table public.user_dictionary_words add column if not exists source_chapter text;
alter table public.user_dictionary_words add column if not exists source_locator text;
