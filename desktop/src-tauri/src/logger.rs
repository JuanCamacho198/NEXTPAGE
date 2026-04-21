use serde::{Deserialize, Serialize};
use std::fs::{self, OpenOptions};
use std::io::{BufRead, BufReader, Write};
use std::path::PathBuf;
use std::sync::Mutex;

const MAX_LOG_LINES: usize = 1000;

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ErrorEventDto {
    pub timestamp: String,
    pub severity: String,
    pub category: String,
    pub code: String,
    pub message: String,
    pub context: serde_json::Value,
    pub correlation_id: String,
    pub source: String,
    pub recoverable: bool,
}

pub struct Logger {
    log_path: PathBuf,
    redaction_patterns: Vec<String>,
}

impl Logger {
    pub fn new(app_data_dir: PathBuf) -> Self {
        let log_path = app_data_dir.join("recent-errors.jsonl");
        Self {
            log_path,
            redaction_patterns: vec![
                "password".to_string(),
                "token".to_string(),
                "secret".to_string(),
                "api_key".to_string(),
            ],
        }
    }

    pub fn log_to_file(&self, event: &ErrorEventDto) -> Result<(), String> {
        if let Some(parent) = self.log_path.parent() {
            fs::create_dir_all(parent).map_err(|e| format!("Failed to create log dir: {}", e))?;
        }

        let redacted_event = self.redact_event(event);
        let json_line = serde_json::to_string(&redacted_event)
            .map_err(|e| format!("Failed to serialize event: {}", e))?;

        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.log_path)
            .map_err(|e| format!("Failed to open log file: {}", e))?;

        writeln!(file, "{}", json_line).map_err(|e| format!("Failed to write to log: {}", e))?;

        self.trim_old_lines()?;

        Ok(())
    }

    fn redact_event(&self, event: &ErrorEventDto) -> ErrorEventDto {
        let redacted_message = self.redact_string(&event.message);
        let redacted_context = self.redact_value(&event.context);

        ErrorEventDto {
            timestamp: event.timestamp.clone(),
            severity: event.severity.clone(),
            category: event.category.clone(),
            code: event.code.clone(),
            message: redacted_message,
            context: redacted_context,
            correlation_id: event.correlation_id.clone(),
            source: event.source.clone(),
            recoverable: event.recoverable,
        }
    }

    fn redact_string(&self, input: &str) -> String {
        let mut result = input.to_string();
        for pattern in &self.redaction_patterns {
            let pattern_escaped = regex::escape(pattern);
            let regex_pattern = format!(r"(?i){}:[^\s,}}]+", pattern_escaped);
            if let Ok(re) = regex::Regex::new(&regex_pattern) {
                result = re
                    .replace_all(&result, format!("{}:[REDACTED]", pattern))
                    .to_string();
            }
        }
        result
    }

    fn redact_value(&self, value: &serde_json::Value) -> serde_json::Value {
        match value {
            serde_json::Value::Object(map) => {
                let mut new_map = serde_json::Map::new();
                for (k, v) in map {
                    let lower_key = k.to_lowercase();
                    let should_redact = self
                        .redaction_patterns
                        .iter()
                        .any(|p| lower_key.contains(p));
                    new_map.insert(
                        k.clone(),
                        if should_redact {
                            serde_json::Value::String("[REDACTED]".to_string())
                        } else {
                            self.redact_value(v)
                        },
                    );
                }
                serde_json::Value::Object(new_map)
            }
            serde_json::Value::Array(arr) => {
                serde_json::Value::Array(arr.iter().map(|v| self.redact_value(v)).collect())
            }
            serde_json::Value::String(s) => serde_json::Value::String(self.redact_string(s)),
            _ => value.clone(),
        }
    }

    fn trim_old_lines(&self) -> Result<(), String> {
        if !self.log_path.exists() {
            return Ok(());
        }

        let file = fs::File::open(&self.log_path)
            .map_err(|e| format!("Failed to open log file for trimming: {}", e))?;
        let reader = BufReader::new(file);

        let lines: Vec<String> = reader.lines().filter_map(|l| l.ok()).collect();
        let total_lines = lines.len();

        if total_lines > MAX_LOG_LINES {
            let skip_count = total_lines - MAX_LOG_LINES;
            let lines_to_keep: Vec<String> = lines.into_iter().skip(skip_count).collect();

            let mut file = OpenOptions::new()
                .write(true)
                .truncate(true)
                .open(&self.log_path)
                .map_err(|e| format!("Failed to open log file for trimming: {}", e))?;

            for line in lines_to_keep {
                writeln!(file, "{}", line)
                    .map_err(|e| format!("Failed to write trimmed log: {}", e))?;
            }
        }

        Ok(())
    }

    pub fn get_recent_errors(&self, limit: usize) -> Result<Vec<ErrorEventDto>, String> {
        if !self.log_path.exists() {
            return Ok(vec![]);
        }

        let file = fs::File::open(&self.log_path)
            .map_err(|e| format!("Failed to open log file: {}", e))?;
        let reader = BufReader::new(file);

        let mut events: Vec<ErrorEventDto> = reader
            .lines()
            .filter_map(|l| l.ok())
            .filter_map(|line| serde_json::from_str(&line).ok())
            .collect();

        events.reverse();
        events.truncate(limit);
        Ok(events)
    }
}

pub struct LoggerState {
    pub logger: Mutex<Logger>,
}

impl LoggerState {
    pub fn new(app_data_dir: PathBuf) -> Self {
        Self {
            logger: Mutex::new(Logger::new(app_data_dir)),
        }
    }
}
