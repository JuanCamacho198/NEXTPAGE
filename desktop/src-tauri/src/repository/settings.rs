use rusqlite::params;

use crate::error::{AppError, AppResult};
use crate::models::AppSettingDto;

use super::{LibraryRepository, MAX_SETTING_BATCH};

pub fn get_settings(repo: &LibraryRepository) -> AppResult<Vec<AppSettingDto>> {
    let mut statement = repo.connection.prepare(
        "SELECT key, value_json, updated_at
         FROM app_settings
         ORDER BY key ASC",
    )?;

    let rows = statement.query_map([], |row| {
        Ok(AppSettingDto { key: row.get(0)?, value_json: row.get(1)?, updated_at: row.get(2)? })
    })?;

    let settings = rows.collect::<Result<Vec<_>, _>>()?;
    Ok(settings)
}

pub fn upsert_settings(
    repo: &mut LibraryRepository,
    settings: Vec<AppSettingDto>,
) -> AppResult<()> {
    if settings.len() > MAX_SETTING_BATCH {
        return Err(AppError::InvalidInput(format!(
            "Too many settings in one request (max {})",
            MAX_SETTING_BATCH
        )));
    }

    for setting in &settings {
        LibraryRepository::validate_setting(setting)?;
    }

    let now = chrono::Utc::now().to_rfc3339();
    let tx = repo.connection.transaction()?;
    for setting in settings {
        tx.execute(
            "INSERT INTO app_settings (key, value_json, updated_at)
             VALUES (?1, ?2, ?3)
             ON CONFLICT(key) DO UPDATE SET
               value_json = excluded.value_json,
               updated_at = excluded.updated_at",
            params![setting.key, setting.value_json, now],
        )?;
    }
    tx.commit()?;

    Ok(())
}
