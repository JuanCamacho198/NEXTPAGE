-- Migration 02: Enable RLS on devices table with policies

-- 1. Enable RLS
alter table public.devices enable row level security;

-- 2. Policy: users can select their own devices (both authenticated via supabase_user_id
--    and legacy devices where supabase_user_id is still NULL for migration period)
create policy "users_select_own_devices" on public.devices
  for select
  using (
    auth.uid()::text = supabase_user_id::text
    or
    supabase_user_id is null
  );

-- 3. Policy: users can insert their own devices
--    The supabase_user_id must match the authenticated user
create policy "users_insert_own_devices" on public.devices
  for insert
  with check (
    auth.uid()::text = supabase_user_id::text
    or
    (supabase_user_id is null and auth.uid() is null)
  );

-- 4. Policy: users can update their own devices
create policy "users_update_own_devices" on public.devices
  for update
  using (
    auth.uid()::text = supabase_user_id::text
    or
    supabase_user_id is null
  );

-- 5. Policy: users can delete their own devices
create policy "users_delete_own_devices" on public.devices
  for delete
  using (
    auth.uid()::text = supabase_user_id::text
    or
    supabase_user_id is null
  );
