use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct ReadingProgressDto {
    pub id: String,
    pub book_id: String,
    pub cfi_location: String,
    pub percentage: f64,
    pub updated_at: String,
}

#[derive(Debug, Clone, Deserialize, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct SaveProgressInput {
    pub book_id: String,
    pub cfi_location: String,
    pub percentage: f64,
}

#[derive(Debug, Clone, Deserialize, Serialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct ReadingSessionInput {
    pub book_id: String,
    pub started_at: String,
    pub ended_at: Option<String>,
    pub duration_seconds: i64,
    pub start_percentage: Option<f64>,
    pub end_percentage: Option<f64>,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct ReadingStatsSummaryDto {
    pub total_minutes_read: i64,
    pub total_sessions: i64,
    pub books_started: i64,
    pub books_completed: i64,
    pub avg_progress_percentage: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct ActivityPoint {
    pub bucket: String,
    pub minutes: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct HighlightDto {
    pub id: String,
    pub book_id: String,
    pub color: String,
    pub text: String,
    pub page: i32,
    pub rect_left: f64,
    pub rect_right: f64,
    pub rect_top: f64,
    pub rect_bottom: f64,
    pub cfi: Option<String>,
    pub note: Option<String>,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct BookmarkDto {
    pub id: String,
    pub book_id: String,
    pub page: i32,
    pub position: f64,
    pub title: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct CollectionDto {
    pub id: i64,
    pub name: String,
    pub color: Option<String>,
    pub is_system: bool,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct SearchResultDto {
    pub chunk_id: String,
    pub book_id: String,
    pub locator: String,
    pub snippet: String,
    pub rank: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct SyncOutboxRowDto {
    pub id: String,
    pub entity_type: String,
    pub entity_id: Option<String>,
    pub operation: String,
    pub payload_json: String,
    pub retry_count: i32,
    pub last_error: Option<String>,
    pub created_at: String,
    pub next_retry_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct SearchBookTextResponse {
    pub items: Vec<SearchResultDto>,
    pub total: i64,
    pub page: i64,
    pub page_size: i64,
}
