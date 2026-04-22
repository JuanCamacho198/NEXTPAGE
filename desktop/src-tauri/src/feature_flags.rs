use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FeatureFlag {
    pub name: String,
    pub enabled: bool,
    pub value_json: Option<String>,
}

impl FeatureFlag {
    pub fn new(name: impl Into<String>, enabled: bool) -> Self {
        Self {
            name: name.into(),
            enabled,
            value_json: None,
        }
    }

    pub fn with_value(name: impl Into<String>, value_json: impl Into<String>) -> Self {
        Self {
            name: name.into(),
            enabled: true,
            value_json: Some(value_json.into()),
        }
    }
}

pub struct FeatureFlags;

impl FeatureFlags {
    pub const IMPORT_HANDLER: &'static str = "feature_import_handler";
    pub const THUMBNAIL_HANDLER: &'static str = "feature_thumbnail_handler";
    pub const TELEMETRY_ENABLED: &'static str = "feature_telemetry_enabled";
    pub const SYNC_ENABLED: &'static str = "feature_sync_enabled";
    pub const DEBUG_MODE: &'static str = "feature_debug_mode";
}
