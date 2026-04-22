use std::fs;
use std::path::PathBuf;

use serde_json::Value;

use crate::error::AppResult;
use crate::queue::types::{JobOutcome, JobRecord};
use crate::services::job_service::JobDispatcher;

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

pub struct ImportJobHandler;

impl ImportJobHandler {
    pub fn new() -> Self {
        Self
    }
}

impl Default for ImportJobHandler {
    fn default() -> Self {
        Self::new()
    }
}

impl JobDispatcher for ImportJobHandler {
    fn dispatch(&self, job: &JobRecord) -> JobOutcome {
        let payload: Result<Value, _> = serde_json::from_str(&job.payload_json);
        match payload {
            Ok(_payload) => JobOutcome::Done {
                result_json: Some(r#"{"status":"import completed"}"#.to_string()),
            },
            Err(e) => JobOutcome::Failed {
                error: format!("invalid payload: {}", e),
            },
        }
    }
}

pub struct ThumbnailHandler;

impl ThumbnailHandler {
    pub fn new() -> Self {
        Self
    }
}

impl Default for ThumbnailHandler {
    fn default() -> Self {
        Self::new()
    }
}

impl JobDispatcher for ThumbnailHandler {
    fn dispatch(&self, job: &JobRecord) -> JobOutcome {
        let payload: Result<Value, _> = serde_json::from_str(&job.payload_json);
        match payload {
            Ok(_payload) => JobOutcome::Done { result_json: None },
            Err(e) => JobOutcome::Failed {
                error: format!("invalid payload: {}", e),
            },
        }
    }
}
