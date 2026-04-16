use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
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
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveHighlightInput {
    pub book_id: String,
    pub color: String,
    pub text: String,
    pub page: i32,
    pub rect_left: f64,
    pub rect_right: f64,
    pub rect_top: f64,
    pub rect_bottom: f64,
    pub cfi: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BookmarkDto {
    pub id: String,
    pub book_id: String,
    pub page: i32,
    pub position: f64,
    pub title: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveBookmarkInput {
    pub book_id: String,
    pub page: i32,
    pub position: f64,
    pub title: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BookDto {
    pub id: String,
    pub title: String,
    pub author: String,
    pub file_path: String,
    pub format: String,
    pub sync_status: String,
    pub current_page: i32,
    pub total_pages: i32,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BookImportInput {
    pub source_path: String,
    pub title: Option<String>,
    pub author: Option<String>,
    pub format: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ReadingProgressDto {
    pub id: String,
    pub book_id: String,
    pub cfi_location: String,
    pub percentage: f64,
    pub updated_at: String,
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SaveProgressInput {
    pub book_id: String,
    pub cfi_location: String,
    pub percentage: f64,
}
