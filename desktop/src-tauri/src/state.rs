use std::path::PathBuf;
use std::sync::{Arc, Mutex};

use crate::logger::Logger;
use crate::repository::LibraryRepository;

pub struct AppState {
    pub repository: Arc<Mutex<LibraryRepository>>,
    pub logger: Arc<Mutex<Logger>>,
}

impl AppState {
    pub fn new(repository: LibraryRepository, app_data_dir: PathBuf) -> Self {
        Self {
            repository: Arc::new(Mutex::new(repository)),
            logger: Arc::new(Mutex::new(Logger::new(app_data_dir))),
        }
    }
}
