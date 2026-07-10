-- Migration 03: Enable Realtime publication for devices table
-- This allows the Realtime subscription to receive live changes to devices.

alter publication supabase_realtime add table public.devices;

-- Optional: Set the replica identity to full so UPDATE events include old data
-- (needed for accurate conflict resolution on the client side)
alter table public.devices replica identity full;
