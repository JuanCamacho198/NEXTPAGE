use std::time::Instant;

use serde_json::json;

use crate::error::AppResult;
use crate::queue::repository::QueueRepository;
use crate::queue::types::NewJob;

pub fn benchmark_enqueue(
    repository: &mut QueueRepository,
    count: usize,
) -> AppResult<BenchmarkResult> {
    let mut ids = Vec::with_capacity(count);
    let start = Instant::now();

    for i in 0..count {
        let job = NewJob::now(
            format!("bench-{}", i),
            "benchmark".to_string(),
            json!({"index": i}),
            None,
        )?;
        let id = repository.enqueue(job)?;
        ids.push(id);
    }

    let elapsed = start.elapsed();
    let per_second = count as f64 / elapsed.as_secs_f64();

    Ok(BenchmarkResult {
        total_items: count,
        elapsed_ms: elapsed.as_millis() as u64,
        items_per_second: per_second as u64,
    })
}

pub fn benchmark_claim_due_jobs(
    repository: &mut QueueRepository,
    count: usize,
) -> AppResult<BenchmarkResult> {
    for i in 0..count {
        let job = NewJob::now(
            format!("claim-bench-{}", i),
            "benchmark".to_string(),
            json!({"index": i}),
            None,
        )?;
        let _ = repository.enqueue(job)?;
    }

    let start = Instant::now();
    let claimed = repository.claim_due_jobs(count as i64, 30)?;
    let elapsed = start.elapsed();
    let per_second = count as f64 / elapsed.as_secs_f64();

    Ok(BenchmarkResult {
        total_items: claimed.len(),
        elapsed_ms: elapsed.as_millis() as u64,
        items_per_second: per_second as u64,
    })
}

#[derive(Debug, Clone)]
pub struct BenchmarkResult {
    pub total_items: usize,
    pub elapsed_ms: u64,
    pub items_per_second: u64,
}

impl std::fmt::Display for BenchmarkResult {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "Benchmark: {} items in {}ms ({} items/sec)",
            self.total_items, self.elapsed_ms, self.items_per_second
        )
    }
}
