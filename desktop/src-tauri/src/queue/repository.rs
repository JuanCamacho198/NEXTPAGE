use chrono::Utc;
use rusqlite::{params, Connection, ErrorCode, OptionalExtension};

use crate::error::AppResult;
use crate::queue::types::{
    compute_lease_expiration, JobOutcome, JobRecord, JobState, NewJob, QueueError,
};

pub struct QueueRepository {
    connection: Connection,
}

impl QueueRepository {
    pub fn new(connection: Connection) -> Self {
        Self { connection }
    }

    pub fn enqueue(&mut self, job: NewJob) -> AppResult<String> {
        let dedupe_key = job.dedupe_key.clone();
        let result = self.connection.execute(
            "INSERT INTO jobs (id, job_type, payload_json, state, attempt, max_attempts, next_run_at, lease_expires_at, dedupe_key, last_error, result_json, started_at, finished_at, created_at, updated_at)
             VALUES (?1, ?2, ?3, 'queued', 0, ?4, ?5, NULL, ?6, NULL, NULL, NULL, NULL, ?7, ?7)",
            params![
                job.id,
                job.job_type,
                job.payload_json,
                job.max_attempts,
                job.next_run_at,
                job.dedupe_key,
                job.created_at,
            ],
        );

        match result {
            Ok(_) => Ok(job.id),
            Err(rusqlite::Error::SqliteFailure(error, _))
                if error.code == ErrorCode::ConstraintViolation =>
            {
                let existing_id: Option<String> = self
                    .connection
                    .query_row(
                        "SELECT id FROM jobs WHERE dedupe_key = ?1 AND state IN ('queued', 'running') LIMIT 1",
                        params![dedupe_key],
                        |row| row.get(0),
                    )
                    .optional()?;

                if let Some(id) = existing_id {
                    Ok(id)
                } else {
                    Err(QueueError::DuplicateDedupeKey.into())
                }
            }
            Err(error) => Err(error.into()),
        }
    }

    pub fn claim_due_jobs(&mut self, limit: i64, lease_seconds: i64) -> AppResult<Vec<JobRecord>> {
        let tx = self.connection.transaction()?;
        let now = Utc::now().to_rfc3339();

        let mut statement = tx.prepare(
            "SELECT id
             FROM jobs
             WHERE state = 'queued'
               AND next_run_at <= ?1
             ORDER BY next_run_at ASC, created_at ASC
             LIMIT ?2",
        )?;

        let ids = statement
            .query_map(params![now, limit], |row| row.get::<_, String>(0))?
            .collect::<Result<Vec<_>, _>>()?;
        drop(statement);

        let lease_expires_at = compute_lease_expiration(lease_seconds);
        let mut claimed = Vec::new();

        for id in ids {
            let affected = tx.execute(
                "UPDATE jobs
                 SET state = 'running',
                     lease_expires_at = ?1,
                     started_at = COALESCE(started_at, ?2),
                     updated_at = ?2
                 WHERE id = ?3
                   AND state = 'queued'",
                params![lease_expires_at, now, id],
            )?;

            if affected == 1 {
                let record = Self::fetch_job_record_tx(&tx, &id)?.ok_or_else(|| {
                    QueueError::Storage(format!("claimed job {} not found for readback", id))
                })?;
                claimed.push(record);
            }
        }

        tx.commit()?;
        Ok(claimed)
    }

    pub fn mark_running(&self, id: &str, lease_expires_at: &str) -> AppResult<()> {
        self.transition(id, JobState::Queued, JobState::Running, |connection| {
            let now = Utc::now().to_rfc3339();
            connection.execute(
                "UPDATE jobs
                 SET state = 'running',
                     lease_expires_at = ?1,
                     started_at = COALESCE(started_at, ?2),
                     updated_at = ?2
                 WHERE id = ?3",
                params![lease_expires_at, now, id],
            )?;
            Ok(())
        })
    }

    pub fn mark_done(&self, id: &str, result_json: Option<&str>) -> AppResult<()> {
        self.transition(id, JobState::Running, JobState::Done, |connection| {
            let now = Utc::now().to_rfc3339();
            connection.execute(
                "UPDATE jobs
                 SET state = 'done',
                     result_json = ?1,
                     lease_expires_at = NULL,
                     finished_at = ?2,
                     updated_at = ?2
                 WHERE id = ?3",
                params![result_json, now, id],
            )?;
            Ok(())
        })
    }

    pub fn mark_retry(&self, id: &str, error: &str, retry_at: &str) -> AppResult<()> {
        self.transition(id, JobState::Running, JobState::Queued, |connection| {
            let now = Utc::now().to_rfc3339();
            connection.execute(
                "UPDATE jobs
                 SET state = 'queued',
                     attempt = attempt + 1,
                     next_run_at = ?1,
                     lease_expires_at = NULL,
                     last_error = ?2,
                     updated_at = ?3
                 WHERE id = ?4",
                params![retry_at, error, now, id],
            )?;
            Ok(())
        })
    }

    pub fn mark_failed(&self, id: &str, error: &str) -> AppResult<()> {
        self.transition(id, JobState::Running, JobState::Failed, |connection| {
            let now = Utc::now().to_rfc3339();
            connection.execute(
                "UPDATE jobs
                 SET state = 'failed',
                     attempt = attempt + 1,
                     lease_expires_at = NULL,
                     last_error = ?1,
                     finished_at = ?2,
                     updated_at = ?2
                 WHERE id = ?3",
                params![error, now, id],
            )?;
            Ok(())
        })
    }

    pub fn apply_outcome(&self, id: &str, outcome: JobOutcome) -> AppResult<()> {
        match outcome {
            JobOutcome::Done { result_json } => self.mark_done(id, result_json.as_deref()),
            JobOutcome::Retry { error, retry_at } => self.mark_retry(id, &error, &retry_at),
            JobOutcome::Failed { error } => self.mark_failed(id, &error),
        }
    }

    pub fn recover_expired_running_jobs(&self) -> AppResult<usize> {
        let now = Utc::now().to_rfc3339();
        let changed = self.connection.execute(
            "UPDATE jobs
             SET state = 'queued',
                 lease_expires_at = NULL,
                 next_run_at = ?1,
                 updated_at = ?1,
                 last_error = COALESCE(last_error, 'lease expired during worker downtime')
             WHERE state = 'running'
               AND lease_expires_at IS NOT NULL
               AND lease_expires_at <= ?1",
            params![now],
        )?;
        Ok(changed)
    }

    pub fn get_job(&self, id: &str) -> AppResult<Option<JobRecord>> {
        Self::fetch_job_record(&self.connection, id)
    }

    pub fn has_queue_schema(&self) -> AppResult<bool> {
        let has_jobs_table: Option<i32> = self
            .connection
            .query_row(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name='jobs' LIMIT 1",
                [],
                |row| row.get(0),
            )
            .optional()?;
        Ok(has_jobs_table.is_some())
    }

    fn transition<F>(
        &self,
        id: &str,
        from: JobState,
        to: JobState,
        mut update_fn: F,
    ) -> AppResult<()>
    where
        F: FnMut(&Connection) -> AppResult<()>,
    {
        let current = Self::fetch_job_record(&self.connection, id)?
            .ok_or_else(|| QueueError::JobNotFound(id.to_string()))?;

        if current.state != from {
            return Err(QueueError::InvalidTransition {
                from: current.state,
                to,
            }
            .into());
        }

        update_fn(&self.connection)?;
        Ok(())
    }

    fn fetch_job_record(connection: &Connection, id: &str) -> AppResult<Option<JobRecord>> {
        connection
            .query_row(
                "SELECT id, job_type, payload_json, state, attempt, max_attempts, next_run_at, lease_expires_at, dedupe_key, last_error
                 FROM jobs
                 WHERE id = ?1
                 LIMIT 1",
                params![id],
                |row| {
                    let state: String = row.get(3)?;
                    let parsed_state = JobState::from_db(&state)
                        .map_err(|err| rusqlite::Error::FromSqlConversionFailure(3, rusqlite::types::Type::Text, Box::new(err)))?;
                    Ok(JobRecord {
                        id: row.get(0)?,
                        job_type: row.get(1)?,
                        payload_json: row.get(2)?,
                        state: parsed_state,
                        attempt: row.get(4)?,
                        max_attempts: row.get(5)?,
                        next_run_at: row.get(6)?,
                        lease_expires_at: row.get(7)?,
                        dedupe_key: row.get(8)?,
                        last_error: row.get(9)?,
                    })
                },
            )
            .optional()
            .map_err(Into::into)
    }

    fn fetch_job_record_tx(
        tx: &rusqlite::Transaction<'_>,
        id: &str,
    ) -> AppResult<Option<JobRecord>> {
        tx.query_row(
            "SELECT id, job_type, payload_json, state, attempt, max_attempts, next_run_at, lease_expires_at, dedupe_key, last_error
             FROM jobs
             WHERE id = ?1
             LIMIT 1",
            params![id],
            |row| {
                let state: String = row.get(3)?;
                let parsed_state = JobState::from_db(&state)
                    .map_err(|err| rusqlite::Error::FromSqlConversionFailure(3, rusqlite::types::Type::Text, Box::new(err)))?;
                Ok(JobRecord {
                    id: row.get(0)?,
                    job_type: row.get(1)?,
                    payload_json: row.get(2)?,
                    state: parsed_state,
                    attempt: row.get(4)?,
                    max_attempts: row.get(5)?,
                    next_run_at: row.get(6)?,
                    lease_expires_at: row.get(7)?,
                    dedupe_key: row.get(8)?,
                    last_error: row.get(9)?,
                })
            },
        )
        .optional()
        .map_err(Into::into)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn build_repo() -> QueueRepository {
        let connection = Connection::open_in_memory().unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0001_init.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0002_books.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0003_highlights.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!(
                "../../migrations/0004_desktop_feature_parity.sql"
            ))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0005_hidden_books.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!("../../migrations/0006_collections.sql"))
            .unwrap();
        connection
            .execute_batch(include_str!(
                "../../migrations/0007_highlight_note_and_page_contract.sql"
            ))
            .unwrap();
        connection
            .execute_batch(include_str!(
                "../../migrations/0008_queue_and_perf_indexes.sql"
            ))
            .unwrap();
        QueueRepository::new(connection)
    }

    #[test]
    fn enqueue_with_same_dedupe_returns_existing_job_id() {
        let mut repo = build_repo();
        let created = NewJob::now(
            "job-a".to_string(),
            "cover.cleanup".to_string(),
            serde_json::json!({"x": 1}),
            Some("dedupe-a".to_string()),
        )
        .unwrap();
        let duplicate = NewJob::now(
            "job-b".to_string(),
            "cover.cleanup".to_string(),
            serde_json::json!({"x": 2}),
            Some("dedupe-a".to_string()),
        )
        .unwrap();

        let first_id = repo.enqueue(created).unwrap();
        let second_id = repo.enqueue(duplicate).unwrap();
        assert_eq!(first_id, second_id);
    }

    #[test]
    fn claim_then_mark_done_transitions_job_state() {
        let mut repo = build_repo();
        let created = NewJob::now(
            "job-state".to_string(),
            "cover.cleanup".to_string(),
            serde_json::json!({"x": 1}),
            None,
        )
        .unwrap();
        repo.enqueue(created).unwrap();

        let claimed = repo.claim_due_jobs(1, 30).unwrap();
        assert_eq!(claimed.len(), 1);
        assert_eq!(claimed[0].state, JobState::Running);

        repo.mark_done(&claimed[0].id, Some("{}")).unwrap();
        let updated = repo.get_job(&claimed[0].id).unwrap().unwrap();
        assert_eq!(updated.state, JobState::Done);
    }

    #[test]
    fn recover_expired_jobs_requeues_running_work() {
        let mut repo = build_repo();
        let created = NewJob::now(
            "job-expired".to_string(),
            "cover.cleanup".to_string(),
            serde_json::json!({"x": 1}),
            None,
        )
        .unwrap();
        repo.enqueue(created).unwrap();
        repo.mark_running("job-expired", "2001-01-01T00:00:00Z")
            .unwrap();

        let recovered = repo.recover_expired_running_jobs().unwrap();
        assert_eq!(recovered, 1);
        let job = repo.get_job("job-expired").unwrap().unwrap();
        assert_eq!(job.state, JobState::Queued);
        assert!(job.lease_expires_at.is_none());
    }
}
