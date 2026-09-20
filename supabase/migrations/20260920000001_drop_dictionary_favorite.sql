-- 20260920000001_drop_dictionary_favorite.sql
-- `is_favorite` on public.user_dictionary_words never drove any behaviour: no
-- filter, no ordering, no badge and no button read it, so the column only
-- travelled on every sync. Dropping the partial index from the create migration
-- first, then the column itself.
--
-- Applied to the remote project as a separately reported step.

drop index if exists public.idx_udw_fav;
alter table public.user_dictionary_words drop column if exists is_favorite;
