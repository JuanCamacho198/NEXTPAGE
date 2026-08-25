# Verify reading-daily-goal (R1–R5) — Manual harness

## Pre-req
- `bun run dev` + Tauri dev or `bun run build` passes
- Signed-in user `u1` and second user `u2`; also test anon (no userId)

## R1 Per-user persistence
- [ ] Sign in as u1 → open Settings cuenta → selected card reflects stored value (default 20 Regular selected `#d8e2ff` check)
- [ ] Select Intenso 45 → click "Establecer meta →" → toast → reopen panel → 45 still selected (persist)
- [ ] `getSettings` row `reading.dailyGoalMinutes_u1` = "45"
- [ ] Direct `saveDailyGoalMinutes(60,u1)` via console → persists 45 (sanitize 60→45)
- [ ] `saveDailyGoalMinutes(99,u1)` → clamp to 45
- [ ] Switch to u2 → goal shows default 20 (isolation, no cross leak)
- [ ] Anon (`userId=""`) → no `reading.dailyGoalMinutes` row written; reads return 20

## R2 Stores + AppState
- [ ] `SettingsDomainState.dailyGoalMinutes` updates instantly without reload after save
- [ ] `StatsDomainState.todayMinutes / dailyGoalMinutes` → `goalProgress = clamp(today/dailyGoal,0,1)` → 50/30 → 1
- [ ] `get_today_minutes(u1)` returns SUM today (12+8 → 20), 0 for yesterday

## R3 SettingsPanel cuenta container (mG4E0 896w parity)
- [ ] Container width max 896, `bg-[#161f335c]` corner 24 `backdrop-blur-[10.5px]` gap 40 p12/p48
- [ ] Header 32 Bold `#d8e2ff` centered "Establece tu meta diaria" + subtitle description
- [ ] Grid 4 cards gap-6 bg-[#161f33] r12 p-8 stroke2: Relajado 10 (hand), Regular 20 (book, default selected), Serio 30 (chart), Intenso 45 (flame) — selected border+fill `#d8e2ff`, check 24×24
- [ ] Pill button "Establecer meta →" bg-[#d8e2ff] rounded-full px10 py4 toujours configurable
- [ ] Current value displayed (e.g. "20 min") updates instantly

## R4 Home 6th card
- [ ] Home shows 6th card "Meta diaria X/Y min Z%" — e.g. `10/20 min 50%` bar 50% `#d8e2ff`
- [ ] `40/20 → 100% clamp` bar does not exceed 100%
- [ ] Locale ES shows "Establece tu meta diaria", EN shows "Set your daily goal" via t()
- [ ] Reactive: changing goal in Settings instantly updates Home bar without reload

## R5 i18n + build
- [ ] `t('settings.daily_goal_title')` ES="Establece tu meta diaria" EN="Set your daily goal"
- [ ] `bun run check` 0 errors, `bun run lint` Tailwind canonical pass, `cargo check` ok
- [ ] `DELETE FROM app_settings WHERE key LIKE 'reading.dailyGoalMinutes%'` rollback works
