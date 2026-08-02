/**
 * Decide whether the custom titlebar should render for a given OS type.
 *
 * `@tauri-apps/plugin-os` `type()` returns lowercase values (`'windows'`,
 * `'macos'`, `'linux'`, ...). Only an exact lowercase `'windows'` match
 * enables the custom titlebar; case variants and other values fail closed.
 */
export function isCustomTitlebarPlatform(osType: string | undefined): boolean {
  return osType === 'windows';
}
