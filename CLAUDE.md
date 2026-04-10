## Project Shell Conventions (Windows)

- Shell environment is Windows PowerShell.
- Prefer PowerShell-native commands for filesystem operations.
- Use `Get-ChildItem` or `dir` instead of `ls`.
- Do not use bash-specific chaining such as `&&`.
- For sequential commands in PowerShell, use `;`.
- For conditional sequencing, use PowerShell checks like:
  - `command1; if ($?) { command2 }`

## Gradle Command Conventions

- Run Android Gradle commands from the `android` directory with `./gradlew.bat`.
- Prefer flags that reduce daemon-related stalls in CI/debug sessions:
  - `--no-daemon`
  - `--console=plain`
  - Add `--max-workers=2` and `--info` when diagnosing stalls.

## SDD Archive Conventions

- When finishing an SDD change with archive, prefer **granular commits** (group by concern/phase instead of one large commit).
- After archive + successful verification/build, **push the commits** to remote if not already pushed.
- If there are warnings but no critical blockers, complete fixes first, re-verify, then archive and push.
