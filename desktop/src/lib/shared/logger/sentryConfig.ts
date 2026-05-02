export interface SentrySettings {
  dsn: string;
  tracesSampleRate: number;
  enabled: boolean;
}

const ENV_DSN_KEY = "SENTRY_DSN";
const SETTINGS_DSN_KEY = "sentry.dsn";

const DEFAULT_TRACES_SAMPLE_RATE = 0.1;

export const getSentryDsn = (): string | undefined => {
  const envDsn = import.meta.env[ENV_DSN_KEY];
  if (envDsn && envDsn.length > 0) {
    return envDsn;
  }
  return undefined;
};

export const getSentrySettings = async (): Promise<SentrySettings> => {
  const envDsn = getSentryDsn();

  let settingsDsn: string | undefined;
  let settingsEnabled: boolean | undefined;
  let settingsSampleRate: number | undefined;

  try {
    const stored = localStorage.getItem(SETTINGS_DSN_KEY);
    if (stored) {
      const parsed = JSON.parse(stored);
      settingsDsn = parsed.dsn;
      settingsEnabled = parsed.enabled;
      settingsSampleRate = parsed.tracesSampleRate;
    }
  } catch {
    // ignore parse errors
  }

  return {
    dsn: settingsDsn ?? envDsn ?? "",
    enabled: settingsEnabled ?? !!envDsn,
    tracesSampleRate: settingsSampleRate ?? DEFAULT_TRACES_SAMPLE_RATE,
  };
};

export const createSentrySettings = (
  dsn?: string,
  enabled?: boolean,
  tracesSampleRate?: number
): SentrySettings => {
  return {
    dsn: dsn ?? getSentryDsn() ?? "",
    enabled: enabled ?? !!dsn,
    tracesSampleRate: tracesSampleRate ?? DEFAULT_TRACES_SAMPLE_RATE,
  };
};