create table public.tags (
  id         text primary key,
  user_id    uuid not null references auth.users(id) on delete cascade,
  name       text not null,
  color      text,
  created_at timestamptz not null default now(),
  unique(user_id, name)
);

create table public.highlight_tags (
  highlight_id text not null references public.highlights(id) on delete cascade,
  tag_id       text not null references public.tags(id) on delete cascade,
  primary key (highlight_id, tag_id)
);

create index idx_ht_tag on public.highlight_tags(tag_id);

alter table public.tags enable row level security;

create policy "Users own tags"
  on public.tags for all
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

alter table public.highlight_tags enable row level security;

create policy "Users own highlight_tags"
  on public.highlight_tags for all
  using (
    exists (select 1 from public.highlights where id = highlight_id and user_id = auth.uid())
  );

alter publication supabase_realtime add table public.tags;
alter publication supabase_realtime add table public.highlight_tags;
