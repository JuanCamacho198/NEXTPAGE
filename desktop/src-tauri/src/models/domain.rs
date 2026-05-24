#[derive(Debug, Clone, PartialEq)]
pub struct ReadingProgress {
    pub id: String,
    pub book_id: String,
    pub locator: String,
    pub percent: f64,
    pub updated_at: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Highlight {
    pub id: String,
    pub book_id: String,
    pub color: String,
    pub text: String,
    pub page: i32,
    pub created_at: String,
    pub updated_at: String,
}

#[derive(Debug, Clone, PartialEq)]
pub struct Bookmark {
    pub id: String,
    pub book_id: String,
    pub page: i32,
    pub position: f64,
    pub title: Option<String>,
    pub created_at: String,
}
