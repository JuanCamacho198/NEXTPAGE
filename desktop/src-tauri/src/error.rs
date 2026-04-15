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
}

pub type AppResult<T> = Result<T, AppError>;
