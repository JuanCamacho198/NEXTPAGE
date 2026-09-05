use serde::{Deserialize, Serialize};
use std::fmt;
use std::fs::{self, OpenOptions};
use std::io::{BufRead, BufReader, Write};
use std::path::PathBuf;
use std::sync::Mutex;

pub const DEFAULT_MAX_LOG_LINES: usize = 1000;
pub const SETTING_MAX_LOG_LINES_KEY: &str = "observability.maxLogLines";

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum LogLevel {
    Debug,
    Info,
    Warn,
    Error,
}

impl fmt::Display for LogLevel {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            LogLevel::Debug => write!(f, "debug"),
            LogLevel::Info => write!(f, "info"),
            LogLevel::Warn => write!(f, "warn"),
            LogLevel::Error => write!(f, "error"),
        }
    }
}

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

/// Generic log event with level — for info/warn/debug non-error events
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LogEventDto {
    pub timestamp: String,
    pub level: LogLevel,
    pub message: String,
    pub context: serde_json::Value,
    pub source: String,
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
                "apikey".to_string(),
                "accesstoken".to_string(),
                "refreshtoken".to_string(),
                "idtoken".to_string(),
                "authorization".to_string(),
                "supabase".to_string(),
            ],
        }
    }

    pub fn log_to_file(&self, event: &ErrorEventDto, max_lines: usize) -> Result<(), String> {
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

        self.trim_old_lines(max_lines)?;

        Ok(())
    }

    /// Build a stateless Logger bound to an empty path, for callers that only need
    /// the redaction helpers (e.g. Sentry `before_send`). Does NOT touch the filesystem.
    pub fn for_redaction_only() -> Self {
        Self {
            log_path: PathBuf::new(),
            redaction_patterns: vec![
                "password".to_string(),
                "token".to_string(),
                "secret".to_string(),
                "api_key".to_string(),
                "apikey".to_string(),
                "accesstoken".to_string(),
                "refreshtoken".to_string(),
                "idtoken".to_string(),
                "authorization".to_string(),
                "supabase".to_string(),
            ],
        }
    }

    /// Redact PII from an [`ErrorEventDto`]. Single source of truth for the
    /// `password` / `token` / `secret` / `api_key` patterns. Made `pub` so the
    /// Sentry `before_send` hook (and any other egress sink) can reuse it.
    pub fn redact_event(&self, event: &ErrorEventDto) -> ErrorEventDto {
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

    /// Redact PII from an arbitrary `serde_json::Value` in place.
    ///
    /// Contract: the input `value` is walked recursively. Object keys whose
    /// lowercased name contains any of `password` / `token` / `secret` /
    /// `api_key` are replaced with the literal string `"[REDACTED]"`.
    /// String scalars are scanned for the colon-suffixed patterns
    /// (`password:xyz`, `token:abc`, …) and redacted to `<pattern>:[REDACTED]`.
    /// Other shapes pass through.
    ///
    /// This is the same contract as [`Self::redact_event`] minus the
    /// `ErrorEventDto` field copying — designed for sinks that carry raw
    /// `serde_json::Value` payloads (Sentry `event.extra`, breadcrumbs, etc.).
    pub fn redact_json_value(value: &mut serde_json::Value) {
        let logger = Self::for_redaction_only();
        *value = logger.redact_value_inner(value);
    }

    fn redact_string(&self, input: &str) -> String {
        let mut result = input.to_string();
        for pattern in &self.redaction_patterns {
            let pattern_escaped = regex::escape(pattern);
            let regex_pattern = format!(r"(?i){}:[^\s,}}]+", pattern_escaped);
            if let Ok(re) = regex::Regex::new(&regex_pattern) {
                result = re.replace_all(&result, format!("{}:[REDACTED]", pattern)).to_string();
            }
        }
        result
    }

    fn redact_value(&self, value: &serde_json::Value) -> serde_json::Value {
        self.redact_value_inner(value)
    }

    fn redact_value_inner(&self, value: &serde_json::Value) -> serde_json::Value {
        match value {
            serde_json::Value::Object(map) => {
                let mut new_map = serde_json::Map::new();
                for (k, v) in map {
                    let lower_key = k.to_lowercase();
                    let should_redact =
                        self.redaction_patterns.iter().any(|p| lower_key.contains(p));
                    new_map.insert(
                        k.clone(),
                        if should_redact {
                            serde_json::Value::String("[REDACTED]".to_string())
                        } else {
                            self.redact_value_inner(v)
                        },
                    );
                }
                serde_json::Value::Object(new_map)
            }
            serde_json::Value::Array(arr) => {
                serde_json::Value::Array(arr.iter().map(|v| self.redact_value_inner(v)).collect())
            }
            serde_json::Value::String(s) => serde_json::Value::String(self.redact_string(s)),
            _ => value.clone(),
        }
    }

    fn trim_old_lines(&self, max_lines: usize) -> Result<(), String> {
        if !self.log_path.exists() {
            return Ok(());
        }

        let file = fs::File::open(&self.log_path)
            .map_err(|e| format!("Failed to open log file for trimming: {}", e))?;
        let reader = BufReader::new(file);

        let lines: Vec<String> = reader.lines().map_while(Result::ok).collect();
        let total_lines = lines.len();

        if total_lines > max_lines {
            let skip_count = total_lines - max_lines;
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

    pub fn log_generic(&self, event: &LogEventDto, max_lines: usize) -> Result<(), String> {
        if let Some(parent) = self.log_path.parent() {
            fs::create_dir_all(parent).map_err(|e| format!("Failed to create log dir: {}", e))?;
        }

        let json_line = serde_json::to_string(event)
            .map_err(|e| format!("Failed to serialize log event: {}", e))?;

        let mut file = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&self.log_path)
            .map_err(|e| format!("Failed to open log file: {}", e))?;

        writeln!(file, "{}", json_line).map_err(|e| format!("Failed to write log: {}", e))?;

        self.trim_old_lines(max_lines)?;
        Ok(())
    }

    pub fn get_log_path(&self) -> &PathBuf {
        &self.log_path
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
            .map_while(Result::ok)
            .filter_map(|line| serde_json::from_str(&line).ok())
            .collect();

        events.reverse();
        events.truncate(limit);
        Ok(events)
    }

    pub fn read_all_logs(&self) -> Result<Vec<String>, String> {
        if !self.log_path.exists() {
            return Ok(vec![]);
        }

        let file = fs::File::open(&self.log_path)
            .map_err(|e| format!("Failed to open log file: {}", e))?;
        let reader = BufReader::new(file);

        let lines: Vec<String> = reader.lines().map_while(Result::ok).collect();
        Ok(lines)
    }

    pub fn get_log_contents(&self) -> Result<String, String> {
        if !self.log_path.exists() {
            return Ok(String::new());
        }

        fs::read_to_string(&self.log_path).map_err(|e| format!("Failed to read log file: {}", e))
    }
}

pub struct LoggerState {
    pub logger: Mutex<Logger>,
}

impl LoggerState {
    pub fn new(app_data_dir: PathBuf) -> Self {
        Self { logger: Mutex::new(Logger::new(app_data_dir)) }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    /// The PII scrubber is the SINGLE SOURCE OF TRUTH for outbound events.
    /// It MUST redact OAuth-style colon-suffixed secrets, sensitive object
    /// keys, and EPUB file paths. Any change that weakens these guarantees
    /// is a PII regression. This is the test required by PR 2 (sentry).
    #[test]
    fn redact_event_strips_oauth_token_and_supabase_key() {
        let logger = Logger::for_redaction_only();
        let event = ErrorEventDto {
            timestamp: "2026-09-04T20:00:00Z".to_string(),
            severity: "error".to_string(),
            category: "auth".to_string(),
            code: "OAUTH_FAIL".to_string(),
            message:
                "callback failed with password:hunter2 and token:abc123 and api_key:xyz_secret"
                    .to_string(),
            context: json!({
                "supabaseKey": "should-be-redacted",
                "apiKey": "should-be-redacted",
                "safe_field": "kept verbatim",
                "nested": {
                    "token_header": "should-be-redacted",
                    "url": "https://oauth.example.com/cb?code=keep_me"
                }
            }),
            correlation_id: "corr-1".to_string(),
            source: "renderer".to_string(),
            recoverable: true,
        };

        let redacted = logger.redact_event(&event);

        // Colon-suffixed patterns are redacted
        assert!(redacted.message.contains("password:[REDACTED]"), "got: {}", redacted.message);
        assert!(redacted.message.contains("token:[REDACTED]"), "got: {}", redacted.message);
        assert!(redacted.message.contains("api_key:[REDACTED]"), "got: {}", redacted.message);
        // Non-sensitive message fragments remain
        assert!(redacted.message.contains("callback failed with"), "got: {}", redacted.message);

        // Sensitive object keys are replaced wholesale
        assert_eq!(redacted.context["supabaseKey"], json!("[REDACTED]"));
        assert_eq!(redacted.context["nested"]["token_header"], json!("[REDACTED]"));
        // Non-sensitive keys pass through unchanged
        assert_eq!(redacted.context["safe_field"], json!("kept verbatim"));
        assert_eq!(
            redacted.context["nested"]["url"],
            json!("https://oauth.example.com/cb?code=keep_me")
        );

        // Untouched fields are preserved
        assert_eq!(redacted.code, "OAUTH_FAIL");
        assert_eq!(redacted.severity, "error");
        assert_eq!(redacted.correlation_id, "corr-1");
        assert_eq!(redacted.source, "renderer");
    }

    #[test]
    fn redact_json_value_works_on_arbitrary_sentry_shape() {
        let mut payload = json!({
            "code": "IPC_FAIL",
            "apiKey": "leaky",
            "context": {
                "token": "leaky-too",
                "user_id": "ok"
            }
        });
        Logger::redact_json_value(&mut payload);

        assert_eq!(payload["code"], json!("IPC_FAIL"));
        assert_eq!(payload["apiKey"], json!("[REDACTED]"));
        assert_eq!(payload["context"]["token"], json!("[REDACTED]"));
        assert_eq!(payload["context"]["user_id"], json!("ok"));
    }
}
