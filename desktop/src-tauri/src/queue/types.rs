use chrono::{DateTime, Duration, Utc};
use serde_json::Value;

use crate::error::{AppError, AppResult};

#[derive(Debug, Clone)]
pub enum QueueError {
    InvalidTransition { from: JobState, to: JobState },
    JobNotFound(String),
    LeaseConflict(String),
    DuplicateDedupeKey,
    Storage(String),
}

impl From<QueueError> for AppError {
    fn from(value: QueueError) -> Self {
        match value {
            QueueError::InvalidTransition { from, to } => AppError::InvalidInput(format!(
                "invalid queue transition {} -> {}",
                from.as_str(),
                to.as_str()
            )),
            QueueError::JobNotFound(id) => {
                AppError::InvalidInput(format!("job not found for id {}", id))
            }
            QueueError::LeaseConflict(id) => {
                AppError::InvalidInput(format!("lease mismatch for job {}", id))
            }
            QueueError::DuplicateDedupeKey => {
                AppError::InvalidInput("active job already exists for dedupe key".to_string())
            }
            QueueError::Storage(message) => AppError::InvalidInput(message),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum JobState {
    Queued,
    Running,
    Failed,
    Done,
}

impl JobState {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Queued => "queued",
            Self::Running => "running",
            Self::Failed => "failed",
            Self::Done => "done",
        }
    }

    pub fn from_db(value: &str) -> AppResult<Self> {
        match value {
            "queued" => Ok(Self::Queued),
            "running" => Ok(Self::Running),
            "failed" => Ok(Self::Failed),
            "done" => Ok(Self::Done),
            other => Err(AppError::InvalidInput(format!(
                "unsupported job state '{}'",
                other
            ))),
        }
    }
}

#[derive(Debug, Clone)]
pub struct NewJob {
    pub id: String,
    pub job_type: String,
    pub payload_json: String,
    pub dedupe_key: Option<String>,
    pub max_attempts: i32,
    pub next_run_at: String,
    pub created_at: String,
}

impl NewJob {
    pub fn now(
        id: String,
        job_type: String,
        payload: Value,
        dedupe_key: Option<String>,
    ) -> AppResult<Self> {
        let now = Utc::now().to_rfc3339();
        let payload_json = serde_json::to_string(&payload)
            .map_err(|err| AppError::InvalidInput(format!("invalid job payload: {}", err)))?;

        Ok(Self {
            id,
            job_type,
            payload_json,
            dedupe_key,
            max_attempts: 3,
            next_run_at: now.clone(),
            created_at: now,
        })
    }
}

#[derive(Debug, Clone)]
pub struct JobRecord {
    pub id: String,
    pub job_type: String,
    pub payload_json: String,
    pub state: JobState,
    pub attempt: i32,
    pub max_attempts: i32,
    pub next_run_at: String,
    pub lease_expires_at: Option<String>,
    pub dedupe_key: Option<String>,
    pub last_error: Option<String>,
}

impl JobRecord {
    pub fn has_remaining_attempts(&self) -> bool {
        self.attempt < self.max_attempts
    }
}

#[derive(Debug, Clone)]
pub enum JobOutcome {
    Done { result_json: Option<String> },
    Retry { error: String, retry_at: String },
    Failed { error: String },
}

#[derive(Debug, Clone)]
pub struct WorkerConfig {
    pub poll_interval_ms: u64,
    pub lease_seconds: i64,
    pub max_concurrency: usize,
    pub batch_size: i64,
}

impl Default for WorkerConfig {
    fn default() -> Self {
        Self {
            poll_interval_ms: 750,
            lease_seconds: 30,
            max_concurrency: 2,
            batch_size: 8,
        }
    }
}

pub fn compute_retry_at(attempt: i32) -> String {
    let capped_attempt = attempt.clamp(1, 6);
    let backoff_seconds = 2_i64.pow(capped_attempt as u32);
    (Utc::now() + Duration::seconds(backoff_seconds)).to_rfc3339()
}

pub fn compute_lease_expiration(lease_seconds: i64) -> String {
    (Utc::now() + Duration::seconds(lease_seconds.max(5))).to_rfc3339()
}

pub fn parse_rfc3339(value: &str) -> AppResult<DateTime<Utc>> {
    let parsed = DateTime::parse_from_rfc3339(value)
        .map_err(|_| AppError::InvalidInput(format!("invalid RFC3339 timestamp '{}'", value)))?;
    Ok(parsed.with_timezone(&Utc))
}

pub const JOB_TYPE_COVER_CLEANUP: &str = "cover_cleanup";
pub const JOB_TYPE_IMPORT: &str = "import";
pub const JOB_TYPE_THUMBNAIL: &str = "thumbnail";
pub const JOB_TYPE_REINDEX: &str = "reindex";
pub const JOB_TYPE_SYNC: &str = "sync";
