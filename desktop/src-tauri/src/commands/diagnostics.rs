use crate::logger::{ErrorEventDto, LogEventDto, DEFAULT_MAX_LOG_LINES, SETTING_MAX_LOG_LINES_KEY};
use crate::state::AppState;
use tauri::State;

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn reportErrorEvent(state: State<'_, AppState>, event: ErrorEventDto) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let max_lines = get_max_log_lines_internal(&repository).unwrap_or(DEFAULT_MAX_LOG_LINES);
    let logger = state.logger.lock().map_err(|e| format!("{}", e))?;
    logger.log_to_file(&event, max_lines)?;

    // Forward to Sentry when initialized. The PII scrubber is already wired
    // via `before_send` in `sentry_init` (reuses `Logger::redact_json_value`),
    // so we attach the DTO as raw extras — `before_send` runs before egress.
    // The severity string is mapped to a Sentry Level; `code` and `source`
    // become tags for Sentry UI filtering.
    if crate::sentry_init::is_enabled() {
        let level = match event.severity.to_lowercase().as_str() {
            "debug" => sentry::Level::Debug,
            "info" => sentry::Level::Info,
            "warning" | "warn" => sentry::Level::Warning,
            "critical" | "fatal" => sentry::Level::Fatal,
            _ => sentry::Level::Error,
        };
        let mut sentry_event = sentry::protocol::Event::new();
        sentry_event.level = level;
        sentry_event.logger = Some(event.source.clone());
        sentry_event.message = Some(format!("[{}] {}", event.code, event.message));
        sentry_event.tags.insert("code".to_string(), event.code.clone());
        sentry_event.tags.insert("source".to_string(), event.source.clone());
        sentry_event.tags.insert("category".to_string(), event.category.clone());
        sentry_event.tags.insert("correlation_id".to_string(), event.correlation_id.clone());
        sentry_event.extra.insert("context".to_string(), event.context.clone());
        sentry_event.extra.insert("recoverable".to_string(), serde_json::json!(event.recoverable));
        sentry::capture_event(sentry_event);
    }

    Ok(())
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn logEvent(state: State<'_, AppState>, event: LogEventDto) -> Result<(), String> {
    let repository = state.repository.lock().map_err(|e| format!("{}", e))?;
    let max_lines = get_max_log_lines_internal(&repository).unwrap_or(DEFAULT_MAX_LOG_LINES);
    let logger = state.logger.lock().map_err(|e| format!("{}", e))?;
    logger.log_generic(&event, max_lines)
}

#[tauri::command(rename_all = "camelCase")]
pub fn diagnose(state: State<'_, AppState>) -> crate::services::diagnostics::DiagnoseResult {
    crate::services::diagnostics::run_diagnose(&state)
}

#[allow(non_snake_case)]
#[tauri::command(rename_all = "camelCase")]
pub fn getLogs(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let logger = state.logger.lock().map_err(|e| format!("{}", e))?;
    logger.read_all_logs()
}

fn get_max_log_lines_internal(
    repository: &crate::repository::LibraryRepository,
) -> Result<usize, String> {
    let settings = repository.get_settings().map_err(|e| format!("{}", e))?;
    let item = settings.iter().find(|s| s.key == SETTING_MAX_LOG_LINES_KEY);
    match item {
        Some(setting) => match serde_json::from_str::<serde_json::Value>(&setting.value_json) {
            Ok(val) => {
                if let Some(n) = val.as_u64() {
                    Ok(n as usize)
                } else {
                    Ok(DEFAULT_MAX_LOG_LINES)
                }
            }
            Err(_) => Ok(DEFAULT_MAX_LOG_LINES),
        },
        None => Ok(DEFAULT_MAX_LOG_LINES),
    }
}
