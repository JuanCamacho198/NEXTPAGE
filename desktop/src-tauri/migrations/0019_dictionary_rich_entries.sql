-- 0019_dictionary_rich_entries: ten nullable columns for the rich lexicon entry.
-- Four user-authored fields (definition, part_of_speech, phonetic, example) and six
-- reader-captured evidence fields (quote, source_book_id, source_book_title,
-- source_book_author, source_chapter, source_locator).
-- Additive and nullable only: no default, no backfill, no index. A pre-existing row
-- stays valid and reads NULL for every new column.
ALTER TABLE dictionary_words ADD COLUMN definition TEXT;
ALTER TABLE dictionary_words ADD COLUMN part_of_speech TEXT;
ALTER TABLE dictionary_words ADD COLUMN phonetic TEXT;
ALTER TABLE dictionary_words ADD COLUMN example TEXT;
ALTER TABLE dictionary_words ADD COLUMN quote TEXT;
ALTER TABLE dictionary_words ADD COLUMN source_book_id TEXT;
ALTER TABLE dictionary_words ADD COLUMN source_book_title TEXT;
ALTER TABLE dictionary_words ADD COLUMN source_book_author TEXT;
ALTER TABLE dictionary_words ADD COLUMN source_chapter TEXT;
ALTER TABLE dictionary_words ADD COLUMN source_locator TEXT;
