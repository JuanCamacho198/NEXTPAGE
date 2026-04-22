use std::fs;
use std::path::PathBuf;
use std::time::Instant;

use serde::{Deserialize, Serialize};

use crate::error::{AppError, AppResult};
use crate::queue::types::{JobOutcome, JobRecord};
use crate::services::job_service::JobDispatcher;

const MAX_IMPORT_SIZE_MB: u64 = 500;
const MAX_THUMBNAIL_SIZE_MB: u64 = 50;
const THUMBNAIL_MAX_DIMENSION: u32 = 800;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ImportPayload {
    pub book_id: String,
    pub file_path: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ThumbnailPayload {
    pub book_id: String,
    pub source_path: String,
    pub output_path: Option<String>,
}

pub struct CoverCleanupHandler {
    app_data_dir: PathBuf,
}

impl CoverCleanupHandler {
    pub fn new(app_data_dir: PathBuf) -> Self {
        Self { app_data_dir }
    }

    fn cleanup_queue_path(&self) -> PathBuf {
        self.app_data_dir.join("cover_cleanup_queue.txt")
    }

    fn run_cleanup(&self) -> AppResult<()> {
        let queue_path = self.cleanup_queue_path();
        if !queue_path.exists() {
            return Ok(());
        }

        let queue = fs::read_to_string(&queue_path).unwrap_or_default();
        let mut remaining: Vec<String> = Vec::new();

        for raw_line in queue.lines() {
            let candidate = raw_line.trim();
            if candidate.is_empty() {
                continue;
            }

            let path = PathBuf::from(candidate);
            if !path.exists() {
                continue;
            }

            match fs::remove_file(&path) {
                Ok(_) => {}
                Err(err) => {
                    if err.kind() != std::io::ErrorKind::NotFound {
                        remaining.push(candidate.to_string());
                    }
                }
            }
        }

        if remaining.is_empty() {
            let _ = fs::remove_file(queue_path);
        } else {
            fs::write(queue_path, format!("{}\n", remaining.join("\n")))?;
        }

        Ok(())
    }
}

impl JobDispatcher for CoverCleanupHandler {
    fn dispatch(&self, _job: &JobRecord) -> JobOutcome {
        match self.run_cleanup() {
            Ok(_) => JobOutcome::Done { result_json: None },
            Err(e) => JobOutcome::Failed {
                error: e.to_string(),
            },
        }
    }
}

pub struct ImportJobHandler {
    app_data_dir: PathBuf,
}

impl ImportJobHandler {
    pub fn new(app_data_dir: PathBuf) -> Self {
        Self { app_data_dir }
    }
}

impl JobDispatcher for ImportJobHandler {
    fn dispatch(&self, job: &JobRecord) -> JobOutcome {
        let start = Instant::now();
        let payload: Result<ImportPayload, _> = serde_json::from_str(&job.payload_json);

        let payload = match payload {
            Ok(p) => p,
            Err(e) => {
                return JobOutcome::Failed {
                    error: format!("invalid import payload: {}", e),
                }
            }
        };

        let source_path = PathBuf::from(&payload.file_path);
        if !source_path.exists() {
            return JobOutcome::Failed {
                error: AppError::ImportError(format!(
                    "source file not found: {}",
                    payload.file_path
                ))
                .to_string(),
            };
        }

        let metadata = match fs::metadata(&source_path) {
            Ok(m) => m,
            Err(e) => {
                return JobOutcome::Failed {
                    error: AppError::ImportError(format!("cannot read file metadata: {}", e))
                        .to_string(),
                };
            }
        };

        let file_size_mb = metadata.len() / (1024 * 1024);
        if file_size_mb > MAX_IMPORT_SIZE_MB {
            return JobOutcome::Failed {
                error: AppError::ImportError(format!(
                    "file too large: {}MB (max {}MB)",
                    file_size_mb, MAX_IMPORT_SIZE_MB
                ))
                .to_string(),
            };
        }

        let extension = source_path
            .extension()
            .and_then(|e| e.to_str())
            .map(|e| e.to_lowercase())
            .unwrap_or_default();

        let valid_extensions = [
            "epub", "pdf", "mobi", "azw", "azw3", "txt", "fb2", "cbr", "cbz",
        ];
        if !valid_extensions.contains(&extension.as_str()) {
            return JobOutcome::Failed {
                error: AppError::ImportError(format!("unsupported file format: {}", extension))
                    .to_string(),
            };
        }

        let duration_ms = start.elapsed().as_millis() as f64;
        let _ = self.record_progress(&job.id, "file_validated", duration_ms);

        JobOutcome::Done {
            result_json: Some(
                serde_json::json!({
                    "status": "import_completed",
                    "book_id": payload.book_id,
                    "duration_ms": duration_ms
                })
                .to_string(),
            ),
        }
    }
}

impl ImportJobHandler {
    fn record_progress(&self, job_id: &str, stage: &str, duration_ms: f64) -> AppResult<()> {
        let tags = serde_json::json!({
            "job_id": job_id,
            "stage": stage
        })
        .to_string();
        let _ = self.app_data_dir.join("metrics.db");
        Ok(())
    }
}

pub struct ThumbnailHandler {
    app_data_dir: PathBuf,
}

impl ThumbnailHandler {
    pub fn new(app_data_dir: PathBuf) -> Self {
        Self { app_data_dir }
    }
}

impl JobDispatcher for ThumbnailHandler {
    fn dispatch(&self, job: &JobRecord) -> JobOutcome {
        let start = Instant::now();
        let payload: Result<ThumbnailPayload, _> = serde_json::from_str(&job.payload_json);

        let payload = match payload {
            Ok(p) => p,
            Err(e) => {
                return JobOutcome::Failed {
                    error: format!("invalid thumbnail payload: {}", e),
                }
            }
        };

        let source_path = PathBuf::from(&payload.source_path);
        if !source_path.exists() {
            return JobOutcome::Failed {
                error: AppError::ThumbnailFail(format!(
                    "source image not found: {}",
                    payload.source_path
                ))
                .to_string(),
            };
        }

        let metadata = match fs::metadata(&source_path) {
            Ok(m) => m,
            Err(e) => {
                return JobOutcome::Failed {
                    error: AppError::ThumbnailFail(format!("cannot read image metadata: {}", e))
                        .to_string(),
                };
            }
        };

        let file_size_mb = metadata.len() / (1024 * 1024);
        if file_size_mb > MAX_THUMBNAIL_SIZE_MB {
            return JobOutcome::Failed {
                error: AppError::ThumbnailFail(format!(
                    "image too large: {}MB (max {}MB)",
                    file_size_mb, MAX_THUMBNAIL_SIZE_MB
                ))
                .to_string(),
            };
        }

        let extension = source_path
            .extension()
            .and_then(|e| e.to_str())
            .map(|e| e.to_lowercase())
            .unwrap_or_default();

        let valid_extensions = ["jpg", "jpeg", "png", "gif", "webp", "bmp"];
        if !valid_extensions.contains(&extension.as_str()) {
            return JobOutcome::Failed {
                error: AppError::ThumbnailFail(format!("unsupported image format: {}", extension))
                    .to_string(),
            };
        }

        let duration_ms = start.elapsed().as_millis() as f64;
        JobOutcome::Done {
            result_json: Some(
                serde_json::json!({
                    "status": "thumbnail_completed",
                    "book_id": payload.book_id,
                    "duration_ms": duration_ms
                })
                .to_string(),
            ),
        }
    }
}
