create table public.devices (
  id          uuid primary key default gen_random_uuid(),
  user_id     text not null,
  hardware_id text not null,
  name        text not null,
  os          text not null,
  type        text not null default 'desktop' check (type in ('desktop', 'mobile', 'tablet', 'web')),
  last_active timestamptz not null default now(),
  created_at  timestamptz not null default now(),
  unique(user_id, hardware_id)
);

create index idx_devices_user_id on devices(user_id);
create index idx_devices_last_active on devices(last_active desc);
