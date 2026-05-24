use super::LibraryRepository;
use chrono::Utc;
use std::fs;
use std::path::PathBuf;
use rusqlite::params;
use uuid::Uuid;
use crate::error::{AppError, AppResult};
use crate::models::{BookDeleteInput, BookDto, BookImportInput, ScanFolderResultDto, ScannedBookFileDto};


    pub fn scan_folder(repo: &LibraryRepository, path: &str) -> AppResult<ScanFolderResultDto> {
        let root = PathBuf::from(path);
        if !root.exists() {
            return Err(AppError::InvalidInput(format!(
                "Folder does not exist: {}",
                path
            )));
        }
        if !root.is_dir() {
            return Err(AppError::InvalidInput(format!(
                "Path is not a folder: {}",
                path
            )));
        }

        let existing_filenames = repo.existing_book_filenames_lowercase()?;
        let mut files: Vec<ScannedBookFileDto> = Vec::new();
        let mut skipped_unsupported_count: i64 = 0;
        let mut skipped_unreadable_count: i64 = 0;
        let mut pending_dirs = vec![root];

        while let Some(current_dir) = pending_dirs.pop() {
            let entries = match fs::read_dir(&current_dir) {
                Ok(entries) => entries,
                Err(_) => {
                    skipped_unreadable_count += 1;
                    continue;
                }
            };

            for entry_result in entries {
                let entry = match entry_result {
                    Ok(entry) => entry,
                    Err(_) => {
                        skipped_unreadable_count += 1;
                        continue;
                    }
                };

                let file_type = match entry.file_type() {
                    Ok(file_type) => file_type,
                    Err(_) => {
                        skipped_unreadable_count += 1;
                        continue;
                    }
                };

                let entry_path = entry.path();
                if file_type.is_dir() {
                    pending_dirs.push(entry_path);
                    continue;
                }
                if !file_type.is_file() {
                    skipped_unsupported_count += 1;
                    continue;
                }

                let extension = entry_path
                    .extension()
                    .and_then(|value| value.to_str())
                    .map(|value| value.to_ascii_lowercase());
                let Some(format) = extension else {
                    skipped_unsupported_count += 1;
                    continue;
                };
                if format != "pdf" && format != "epub" {
                    skipped_unsupported_count += 1;
                    continue;
                }

                let file_name = entry_path
                    .file_name()
                    .and_then(|value| value.to_str())
                    .map(|value| value.to_string())
                    .unwrap_or_else(|| entry_path.to_string_lossy().to_string());

                let file_name_lower = file_name.to_ascii_lowercase();
                let is_duplicate = existing_filenames.contains(&file_name_lower);

                files.push(ScannedBookFileDto {
                    full_path: entry_path.to_string_lossy().to_string(),
                    file_name,
                    format,
                    is_duplicate,
                });
            }
        }

        files.sort_by(|a, b| a.full_path.cmp(&b.full_path));

        Ok(ScanFolderResultDto {
            files,
            skipped_unsupported_count,
            skipped_unreadable_count,
        })
    }

    pub fn import_book(repo: &LibraryRepository, app: tauri::AppHandle, input: BookImportInput) -> AppResult<BookDto> {
        let source_path = PathBuf::from(&input.source_path);
        if !source_path.exists() {
            return Err(AppError::InvalidInput(format!(
                "Source file does not exist: {}",
                input.source_path
            )));
        }

        let app_data_dir = app
            .path()
            .app_data_dir()
            .map_err(|err| AppError::InvalidInput(err.to_string()))?;
        let books_dir = app_data_dir.join("books");
        std::fs::create_dir_all(&books_dir).map_err(|err| {
            AppError::InvalidInput(format!("Failed to create books directory: {}", err))
        })?;

        let file_name = source_path
            .file_name()
            .ok_or_else(|| AppError::InvalidInput("Invalid file name".to_string()))?
            .to_string_lossy()
            .to_string();

        let dest_path = books_dir.join(&file_name);
        if dest_path.exists() {
            let stem = source_path
                .file_stem()
                .ok_or_else(|| AppError::InvalidInput("Invalid file name".to_string()))?
                .to_string_lossy()
                .to_string();
            let ext = source_path
                .extension()
                .unwrap_or_default()
                .to_string_lossy()
                .to_string();
            let unique_name = format!("{}_{}.{}", stem, Uuid::new_v4(), ext);
            let dest_path = books_dir.join(unique_name);
            std::fs::copy(&source_path, &dest_path)
                .map_err(|err| AppError::InvalidInput(format!("Failed to copy file: {}", err)))?;
        } else {
            std::fs::copy(&source_path, &dest_path)
                .map_err(|err| AppError::InvalidInput(format!("Failed to copy file: {}", err)))?;
        }

        let now = Utc::now().to_rfc3339();
        let title = input.title.unwrap_or_else(|| {
            source_path
                .file_stem()
                .unwrap_or_default()
                .to_string_lossy()
                .to_string()
        });
        let author = input.author.unwrap_or_default();
        let format = input.format;

        let book = BookDto {
            id: Uuid::new_v4().to_string(),
            title,
            author,
            file_path: dest_path.to_string_lossy().to_string(),
            format,
            sync_status: "local".to_string(),
            current_page: 0,
            total_pages: 0,
            created_at: now.clone(),
            updated_at: now,
        };

        repo.connection.execute(
            "INSERT INTO books (id, title, author, file_path, format, sync_status, current_page, total_pages, created_at, updated_at, version)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, 1)",
            params![
                book.id,
                book.title,
                book.author,
                book.file_path,
                book.format,
                book.sync_status,
                book.current_page,
                book.total_pages,
                book.created_at,
                book.updated_at
            ],
        )?;

        if let Some(cover_source_path) = LibraryRepository::find_cover_source_path(&source_path) {
            if let Err(err) = repo.upsert_book_cover_from_file(&app, &book.id, &cover_source_path) {
                let _ = repo.log_recoverable_cover_error(
                    &app,
                    &format!(
                        "cover_ingest_failed book_id={} source={} error={}",
                        book.id,
                        cover_source_path.display(),
                        err
                    ),
                );
            }
        }

        let _ = repo.run_deferred_cover_cleanup(&app);

        Ok(book)
    }

    pub fn delete_book(repo: &mut LibraryRepository, app: tauri::AppHandle, input: BookDeleteInput) -> AppResult<()> {
        let book_id = input.book_id.trim();
        let maybe_cover_path = repo.delete_book_metadata(book_id)?;

        if let Some(storage_path) = maybe_cover_path {
            let remove_result = fs::remove_file(PathBuf::from(&storage_path));
            if let Err(err) = remove_result {
                if err.kind() != std::io::ErrorKind::NotFound {
                    repo.enqueue_cover_cleanup(&app, &storage_path)?;
                    repo.log_recoverable_cover_error(
                        &app,
                        &format!(
                            "deferred_cover_cleanup_queued book_id={} path={} error={}",
                            book_id, storage_path, err
                        ),
                    )?;
                }
            }
        }

        let _ = repo.run_deferred_cover_cleanup(&app);
        Ok(())
    }
