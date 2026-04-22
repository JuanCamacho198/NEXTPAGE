use std::sync::{Arc, Mutex};

use serde_json::json;
use tauri::AppHandle;

use crate::error::AppResult;
use crate::models::BookImportInput;
use crate::queue::types::JOB_TYPE_IMPORT;
use crate::repository::LibraryRepository;
use crate::services::job_service::{JobEnqueuer, JobService};

pub struct LibraryService {
    repository: Arc<Mutex<LibraryRepository>>,
    job_service: Arc<JobService>,
}

impl LibraryService {
    pub fn new(repository: Arc<Mutex<LibraryRepository>>, job_service: Arc<JobService>) -> Self {
        Self {
            repository,
            job_service,
        }
    }

    pub fn import_book_as_job(&self, _app: AppHandle, input: BookImportInput) -> AppResult<String> {
        let payload = json!({
            "source_path": input.source_path,
            "title": input.title,
            "author": input.author,
            "format": input.format,
        });

        let job_id =
            self.job_service
                .enqueue_json(JOB_TYPE_IMPORT, payload, Some(input.source_path))?;

        Ok(job_id)
    }
}
