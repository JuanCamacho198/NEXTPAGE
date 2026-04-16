use std::fs;
use std::path::PathBuf;

use chrono::Utc;
use nextpage_desktop::commands::list_library_books_internal;
use nextpage_desktop::db::open_and_migrate;
use nextpage_desktop::models::{
    AppSettingDto, BookDto, ListLibraryBooksInput, ReadingSessionInput,
};
use nextpage_desktop::repository::LibraryRepository;
use uuid::Uuid;

fn temp_db_path() -> PathBuf {
    std::env::temp_dir().join(format!("nextpage_desktop_it_{}.db", Uuid::new_v4()))
}

#[test]
fn migration_chain_creates_desktop_parity_schema() {
    let db_path = temp_db_path();
    let connection = open_and_migrate(&db_path).unwrap();
    let repository = LibraryRepository::new(connection);

    assert!(repository.has_desktop_parity_schema().unwrap());

    let _ = fs::remove_file(db_path);
}

#[test]
fn list_library_books_internal_enforces_response_version_and_schema_presence() {
    let db_path = temp_db_path();
    let connection = open_and_migrate(&db_path).unwrap();
    let repository = LibraryRepository::new(connection);

    let unsupported = list_library_books_internal(
        &repository,
        Some(ListLibraryBooksInput {
            response_version: Some(99),
        }),
    );
    assert!(unsupported.is_err());

    let supported = list_library_books_internal(
        &repository,
        Some(ListLibraryBooksInput {
            response_version: Some(1),
        }),
    );
    assert!(supported.is_ok());

    let _ = fs::remove_file(db_path);
}

#[test]
fn delete_book_metadata_soft_deletes_cover_metadata_and_returns_storage_path() {
    let db_path = temp_db_path();
    let connection = open_and_migrate(&db_path).unwrap();
    let mut repository = LibraryRepository::new(connection);
    let now = Utc::now().to_rfc3339();

    repository
        .upsert_book(BookDto {
            id: "book-delete".to_string(),
            title: "Delete Test".to_string(),
            author: "Tester".to_string(),
            file_path: "C:/library/delete-test.epub".to_string(),
            format: "epub".to_string(),
            sync_status: "local".to_string(),
            current_page: 0,
            total_pages: 100,
            created_at: now.clone(),
            updated_at: now,
        })
        .unwrap();

    let storage_path = repository.delete_book_metadata("book-delete").unwrap();
    assert!(storage_path.is_none());

    let remaining = repository.list_books().unwrap();
    assert!(remaining.is_empty());

    let _ = fs::remove_file(db_path);
}

#[test]
fn restart_roundtrip_preserves_settings_and_stats() {
    let db_path = temp_db_path();
    let now = Utc::now().to_rfc3339();

    {
        let connection = open_and_migrate(&db_path).unwrap();
        let mut repository = LibraryRepository::new(connection);

        repository
            .upsert_book(BookDto {
                id: "book-restart".to_string(),
                title: "Restart Test".to_string(),
                author: "Tester".to_string(),
                file_path: "C:/library/restart-test.epub".to_string(),
                format: "epub".to_string(),
                sync_status: "local".to_string(),
                current_page: 0,
                total_pages: 100,
                created_at: now.clone(),
                updated_at: now.clone(),
            })
            .unwrap();

        repository
            .upsert_settings(vec![AppSettingDto {
                key: "ui.theme".to_string(),
                value_json: "\"sepia\"".to_string(),
                updated_at: now.clone(),
            }])
            .unwrap();

        repository
            .save_reading_session(ReadingSessionInput {
                book_id: "book-restart".to_string(),
                started_at: now.clone(),
                ended_at: None,
                duration_seconds: 300,
                start_percentage: Some(0.0),
                end_percentage: Some(45.0),
            })
            .unwrap();
    }

    {
        let connection = open_and_migrate(&db_path).unwrap();
        let repository = LibraryRepository::new(connection);

        let settings = repository.get_settings().unwrap();
        assert_eq!(settings.len(), 1);
        assert_eq!(settings[0].key, "ui.theme");
        assert_eq!(settings[0].value_json, "\"sepia\"");

        let stats = repository.get_reading_stats(Some("book-restart")).unwrap();
        assert_eq!(stats.total_sessions, 1);
        assert_eq!(stats.total_minutes_read, 5);
    }

    let _ = fs::remove_file(db_path);
}
