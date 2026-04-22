use serde::{Deserialize, Serialize};
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error("database error: {0}")]
    Database(#[from] rusqlite::Error),

    #[error("io error: {0}")]
    Io(#[from] std::io::Error),

    #[error("tauri error: {0}")]
    Tauri(#[from] tauri::Error),

    #[error("book id is required")]
    MissingBookId,

    #[error("invalid input: {0}")]
    InvalidInput(String),

    #[error("compatibility error: {0}")]
    Compatibility(String),

    #[error("database constraint violation: {0}")]
    DbConstraint(String),

    #[error("migration failed: {0}")]
    MigrationFail(String),

    #[error("sync conflict: {0}")]
    SyncConflict(String),

    #[error("import error: {0}")]
    ImportError(String),

    #[error("thumbnail generation failed: {0}")]
    ThumbnailFail(String),
}

impl AppError {
    pub fn recoverable(&self) -> bool {
        match self {
            AppError::DbConstraint(_) => false,
            AppError::MigrationFail(_) => false,
            AppError::SyncConflict(_) => true,
            AppError::ImportError(_) => true,
            AppError::ThumbnailFail(_) => true,
            AppError::InvalidInput(_) => true,
            AppError::Compatibility(_) => true,
            AppError::MissingBookId => false,
            AppError::Database(_) => false,
            AppError::Io(_) => false,
            AppError::Tauri(_) => false,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum ErrorCode {
    Database,
    Io,
    Tauri,
    MissingBookId,
    InvalidInput,
    Compatibility,
    DbConstraint,
    MigrationFail,
    SyncConflict,
    ImportError,
    ThumbnailFail,
}

pub type AppResult<T> = Result<T, AppError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_db_constraint_recoverable() {
        let err = AppError::DbConstraint("UNIQUE constraint failed: books.id".to_string());
        assert!(!err.recoverable());
    }

    #[test]
    fn test_migration_fail_recoverable() {
        let err = AppError::MigrationFail("schema version mismatch".to_string());
        assert!(!err.recoverable());
    }

    #[test]
    fn test_sync_conflict_recoverable() {
        let err = AppError::SyncConflict("local and remote changes conflict".to_string());
        assert!(err.recoverable());
    }

    #[test]
    fn test_import_error_recoverable() {
        let err = AppError::ImportError("invalid epub structure".to_string());
        assert!(err.recoverable());
    }

    #[test]
    fn test_thumbnail_fail_recoverable() {
        let err = AppError::ThumbnailFail("image too small".to_string());
        assert!(err.recoverable());
    }

    #[test]
    fn test_invalid_input_recoverable() {
        let err = AppError::InvalidInput("missing required field".to_string());
        assert!(err.recoverable());
    }

    #[test]
    fn test_database_not_recoverable() {
        let err = AppError::Database(rusqlite::Error::QueryReturnedNoRows);
        assert!(!err.recoverable());
    }
}
