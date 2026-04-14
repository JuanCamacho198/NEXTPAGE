use std::sync::{Arc, Mutex};

use crate::repository::LibraryRepository;

pub struct AppState {
    pub repository: Arc<Mutex<LibraryRepository>>,
}

impl AppState {
    pub fn new(repository: LibraryRepository) -> Self {
        Self {
            repository: Arc::new(Mutex::new(repository)),
        }
    }
}
