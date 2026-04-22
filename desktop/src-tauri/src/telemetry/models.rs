use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub enum MetricName {
    JobStarted,
    JobCompleted,
    JobFailed,
    ImportDuration,
    ThumbnailDuration,
    QueryDuration,
    CacheHit,
    CacheMiss,
}

impl MetricName {
    pub fn as_str(&self) -> &'static str {
        match self {
            MetricName::JobStarted => "job_started",
            MetricName::JobCompleted => "job_completed",
            MetricName::JobFailed => "job_failed",
            MetricName::ImportDuration => "import_duration_ms",
            MetricName::ThumbnailDuration => "thumbnail_duration_ms",
            MetricName::QueryDuration => "query_duration_ms",
            MetricName::CacheHit => "cache_hit",
            MetricName::CacheMiss => "cache_miss",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Metric {
    pub name: String,
    pub value: f64,
    pub tags: Vec<(String, String)>,
    pub timestamp: DateTime<Utc>,
}

impl Metric {
    pub fn new(name: MetricName, value: f64) -> Self {
        Self {
            name: name.as_str().to_string(),
            value,
            tags: Vec::new(),
            timestamp: Utc::now(),
        }
    }

    pub fn with_tag(mut self, key: impl Into<String>, value: impl Into<String>) -> Self {
        self.tags.push((key.into(), value.into()));
        self
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MetricSummary {
    pub name: String,
    pub count: i64,
    pub sum: f64,
    pub min: f64,
    pub max: f64,
    pub avg: f64,
}
