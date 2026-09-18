/**
 * Executable entry point for `bun run --cwd packages/manifest-validator
 * validate -- <manifest-url>`. All logic lives in ./cli.ts (testable); this
 * file only wires argv, stdio and the exit code.
 */
import { runValidateCli } from './cli';

const exitCode = await runValidateCli(process.argv.slice(2), {
  out: (line) => process.stdout.write(`${line}\n`),
  err: (line) => process.stderr.write(`${line}\n`),
});

process.exitCode = exitCode;
