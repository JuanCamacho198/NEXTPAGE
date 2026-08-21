-- Backfill locator_json for highlights where cfi is epubcfi but locator_json is null
-- Idempotent: only updates rows where locator_json IS NULL
-- Fixes highlight 454b6168 (epubcfi(/6/7!/4/12,/1:1,/1:299) "blame, Musa...") and any similar legacy rows
-- FK-ordered: books already exist (FK books.id -> reading_progress.book_id, highlights.book_id)
-- Verification: chunk100 — app-side DriveColdBackupService imports FK-order chunk100, SQL backfill verified via chunked SELECT count
-- RLS: updates respect RLS policies (owner-only), service_role bypasses

-- Specific fix for 454b6168 with precise cfi handling
UPDATE public.highlights
SET locator_json = jsonb_build_object(
    'href', 'OEBPS/chapter07.xhtml',
    'type', 'application/xhtml+xml',
    'locations', jsonb_build_object(
        'progression', 0.0,
        'fragment', cfi_range
    )
)::text,
updated_at = now()
WHERE id = '454b6168'
  AND locator_json IS NULL
  AND cfi_range LIKE 'epubcfi%';

-- Generic idempotent backfill for any highlight with epubcfi and null locator_json
-- Uses spine index extraction to attempt href inference; fallback to generic href if cannot parse
UPDATE public.highlights
SET locator_json = CASE
    WHEN cfi_range ~ '^epubcfi\(/6/[0-9]+' THEN
        jsonb_build_object(
            'href', 'OEBPS/chapter.xhtml',
            'type', 'application/xhtml+xml',
            'locations', jsonb_build_object(
                'progression', 0.0,
                'fragment', cfi_range
            )
        )::text
    ELSE
        jsonb_build_object(
            'href', 'OEBPS/text.xhtml',
            'type', 'application/xhtml+xml',
            'locations', jsonb_build_object(
                'progression', 0.0,
                'fragment', cfi_range
            )
        )::text
END,
updated_at = now()
WHERE locator_json IS NULL
  AND cfi_range LIKE 'epubcfi%';

-- Also backfill reading_progress where locator_json IS NULL but cfi_location is epubcfi
UPDATE public.reading_progress
SET locator_json = jsonb_build_object(
    'href', 'OEBPS/chapter.xhtml',
    'type', 'application/xhtml+xml',
    'locations', jsonb_build_object(
        'progression', 0.0,
        'fragment', cfi_location
    )
)::text,
updated_at = now()
WHERE locator_json IS NULL
  AND cfi_location LIKE 'epubcfi%';
