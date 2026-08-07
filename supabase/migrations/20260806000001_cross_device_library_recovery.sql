-- Cross-device recovery foundation.
-- This migration is additive: legacy rows, columns, and Storage objects remain intact.

alter table public.user_books
  add column if not exists lifecycle text not null default 'imported',
  add column if not exists catalog_version bigint not null default 1,
  add column if not exists recovery_protocol text not null default 'legacy',
  add column if not exists remote_provider text,
  add column if not exists remote_file_id text,
  add column if not exists remote_path text,
  add column if not exists remote_name text,
  add column if not exists protocol_version text,
  add column if not exists imported_from text,
  add column if not exists deleted_at timestamptz,
  add column if not exists deleted_by_device text,
  add column if not exists unavailable_reason text,
  add column if not exists cover_bucket text,
  add column if not exists cover_object_path text,
  add column if not exists cover_hash text,
  add column if not exists cover_media_type text;

alter table public.user_books
  add constraint user_books_lifecycle_check
    check (lifecycle in ('available', 'imported', 'unavailable', 'deleted')),
  add constraint user_books_catalog_version_check
    check (catalog_version > 0),
  add constraint user_books_recovery_protocol_check
    check (recovery_protocol in ('legacy', 'recovery_protocol_v1'));

-- Existing content hashes remain untouched; uniqueness closes future cross-device races.
drop index if exists public.idx_user_books_hash;
create unique index idx_user_books_user_content_hash
  on public.user_books(user_id, content_hash)
  where content_hash is not null
    and recovery_protocol = 'recovery_protocol_v1';
create index idx_user_books_lifecycle_version
  on public.user_books(user_id, lifecycle, catalog_version desc);
create index idx_user_books_remote_reference
  on public.user_books(user_id, remote_provider, remote_file_id)
  where remote_file_id is not null;

alter table public.user_books enable row level security;
drop policy if exists "Users own user_books" on public.user_books;
create policy "user_books_select_own"
  on public.user_books for select to authenticated
  using ((select auth.uid()) = user_id);
create policy "user_books_insert_own"
  on public.user_books for insert to authenticated
  with check ((select auth.uid()) = user_id);
create policy "user_books_update_own"
  on public.user_books for update to authenticated
  using ((select auth.uid()) = user_id)
  with check ((select auth.uid()) = user_id);
create policy "user_books_delete_own"
  on public.user_books for delete to authenticated
  using ((select auth.uid()) = user_id);

-- user_books is already in supabase_realtime from the legacy migration.
alter table public.user_books replica identity full;

insert into storage.buckets (id, name, public)
values ('book-covers', 'book-covers', false)
on conflict (id) do update set public = false;

drop policy if exists "book_covers_insert_own" on storage.objects;
create policy "book_covers_insert_own"
  on storage.objects for insert to authenticated
  with check (
    bucket_id = 'book-covers'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );

drop policy if exists "book_covers_select_own" on storage.objects;
create policy "book_covers_select_own"
  on storage.objects for select to authenticated
  using (
    bucket_id = 'book-covers'
    and (
      owner_id = (select auth.uid()::text)
      or (storage.foldername(name))[1] = (select auth.uid()::text)
    )
  );

drop policy if exists "book_covers_update_own" on storage.objects;
create policy "book_covers_update_own"
  on storage.objects for update to authenticated
  using (
    bucket_id = 'book-covers'
    and (
      owner_id = (select auth.uid()::text)
      or (storage.foldername(name))[1] = (select auth.uid()::text)
    )
  )
  with check (
    bucket_id = 'book-covers'
    and (storage.foldername(name))[1] = (select auth.uid()::text)
  );
