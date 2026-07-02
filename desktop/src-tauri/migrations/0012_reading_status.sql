CREATE TABLE book_reading_status (
    book_id TEXT PRIMARY KEY,
    status TEXT NOT NULL CHECK (status IN ('to_read', 'reading', 'completed')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
);

INSERT OR REPLACE INTO book_reading_status (book_id, status, updated_at)
SELECT bc.book_id,
       CASE
         WHEN MAX(bc.collection_id) = 3 THEN 'completed'
         ELSE 'reading'
       END AS status,
       datetime('now')
FROM book_collections bc
WHERE bc.collection_id IN (2, 3)
GROUP BY bc.book_id;

DELETE FROM book_collections WHERE collection_id IN (2, 3);
DELETE FROM collections WHERE id IN (2, 3);
