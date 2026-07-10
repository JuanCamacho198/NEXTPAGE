-- Migration 01: Add supabase_user_id column to devices table
-- This column stores the Supabase Auth user UUID for RLS policies.
-- Existing devices get their supabase_user_id set based on user_id text field
-- (which previously stored email or legacy user IDs).

alter table public.devices
  add column supabase_user_id uuid;

-- Backfill: set supabase_user_id for existing devices where user_id looks like a UUID
-- (devices created with Google One Tap already used user_id = Google sub, which is a UUID)
update public.devices
  set supabase_user_id = user_id::uuid
  where user_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$';

-- Add index for RLS queries
create index idx_devices_supabase_user_id on devices(supabase_user_id);
