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
        Some(ListLibraryBooksInput { response_version: Some(99) }),
    );
    assert!(unsupported.is_err());

    let supported = list_library_books_internal(
        &repository,
        Some(ListLibraryBooksInput { response_version: Some(1) }),
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
                ended_at: Some((Utc::now() + chrono::Duration::seconds(300)).to_rfc3339()),
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

#[test]
fn dto_camel_case_contract_and_mapper_roundtrip_are_preserved() {
    let dto = nextpage_desktop::models::dto::ReadingProgressDto {
        id: "p1".to_string(),
        book_id: "b1".to_string(),
        cfi_location: "epubcfi(/6/2)".to_string(),
        percentage: 42.5,
        updated_at: Utc::now().to_rfc3339(),
    };

    let json = serde_json::to_value(&dto).unwrap();
    assert!(json.get("bookId").is_some());
    assert!(json.get("cfiLocation").is_some());
    assert!(json.get("updatedAt").is_some());

    let domain = nextpage_desktop::models::mapper::progress_dto_to_domain(dto.clone());
    let back = nextpage_desktop::models::mapper::progress_domain_to_dto(domain);
    assert_eq!(dto, back);
}

#[test]
fn repository_domain_sql_ownership_isolated_by_module_files() {
    let progress_src = include_str!("../src/repository/progress.rs");
    let highlights_src = include_str!("../src/repository/highlights.rs");
    let bookmarks_src = include_str!("../src/repository/bookmarks.rs");
    let collections_src = include_str!("../src/repository/collections.rs");
    let search_src = include_str!("../src/repository/search.rs");

    assert!(progress_src.contains("reading_progress") || progress_src.contains("reading_sessions"));
    assert!(highlights_src.contains("highlights"));
    assert!(bookmarks_src.contains("bookmarks"));
    assert!(
        collections_src.contains("collections") || collections_src.contains("book_collections")
    );
    assert!(search_src.contains("book_text_chunks") || search_src.contains("book_text_fts"));
}

#[test]
fn repository_mutability_boundaries_are_explicit_in_facade_signatures() {
    let repository_mod = include_str!("../src/repository/mod.rs");

    assert!(repository_mod.contains("pub fn list_books(&self"));
    assert!(repository_mod.contains("pub fn get_progress(&self"));
    assert!(repository_mod.contains("pub fn save_progress(&self"));

    assert!(repository_mod.contains("pub fn delete_book(&mut self"));
    assert!(repository_mod.contains("pub fn delete_book_metadata(&mut self"));
    assert!(repository_mod.contains("pub fn index_book_text(&mut self"));
}

#[test]
fn commands_feature_isolation_contract_is_enforced_by_module_layout() {
    let commands_mod = include_str!("../src/commands/mod.rs");
    let bookmarks_mod = include_str!("../src/commands/bookmarks.rs");

    assert!(commands_mod.contains("pub mod bookmarks;"));
    assert!(commands_mod.contains("pub use bookmarks::*;"));

    assert!(bookmarks_mod.contains("save_bookmark"));
    assert!(bookmarks_mod.contains("delete_bookmark"));
    assert!(!bookmarks_mod.contains("save_progress"));
    assert!(!bookmarks_mod.contains("list_highlights"));
}

#[test]
fn domain_evolution_keeps_ipc_contract_stable_via_mapper_boundaries() {
    let domain = nextpage_desktop::models::domain::ReadingProgress {
        id: "p2".to_string(),
        book_id: "b2".to_string(),
        locator: "epubcfi(/6/4)".to_string(),
        percent: 77.0,
        updated_at: Utc::now().to_rfc3339(),
    };

    let dto = nextpage_desktop::models::mapper::progress_domain_to_dto(domain);
    let json = serde_json::to_value(dto).unwrap();

    assert!(json.get("bookId").is_some());
    assert!(json.get("cfiLocation").is_some());
    assert!(json.get("percentage").is_some());
    assert!(json.get("updatedAt").is_some());
}
