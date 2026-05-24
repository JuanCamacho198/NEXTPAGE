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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_new_flag_disabled_by_default() {
        let flag = FeatureFlag::new("test_flag", false);
        assert_eq!(flag.name, "test_flag");
        assert!(!flag.enabled);
        assert!(flag.value_json.is_none());
    }

    #[test]
    fn test_new_flag_enabled() {
        let flag = FeatureFlag::new("test_flag", true);
        assert!(flag.enabled);
        assert!(flag.value_json.is_none());
    }

    #[test]
    fn test_with_value_creates_enabled_flag() {
        let flag = FeatureFlag::with_value("config_flag", r#"{"limit": 50}"#);
        assert!(flag.enabled);
        assert_eq!(flag.value_json, Some(r#"{"limit": 50}"#.to_string()));
    }

    #[test]
    fn test_feature_flag_constants() {
        assert_eq!(FeatureFlags::IMPORT_HANDLER, "feature_import_handler");
        assert_eq!(FeatureFlags::THUMBNAIL_HANDLER, "feature_thumbnail_handler");
        assert_eq!(FeatureFlags::TELEMETRY_ENABLED, "feature_telemetry_enabled");
        assert_eq!(FeatureFlags::SYNC_ENABLED, "feature_sync_enabled");
        assert_eq!(FeatureFlags::DEBUG_MODE, "feature_debug_mode");
    }
}
