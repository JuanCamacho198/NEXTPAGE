# Skill Registry

**Orchestrator use only.** Read this registry once per session to resolve skill paths, then pass pre-resolved paths directly to each sub-agent's launch prompt. Sub-agents receive the path and load the skill directly — they do NOT read this registry.

## User Skills

| Trigger | Skill | Path |
|---------|-------|------|
| spreadsheet file, .xlsx, .csv, .xlsm, .tsv | excel | C:\Users\Juan Camacho\.config\opencode-profiles\default\skills\excel\SKILL.md |

## Project-Level Skills

| Trigger | Skill | Path |
|---------|-------|------|
| design-taste-frontend | design-taste-frontend | C:\Users\Juan Camacho\Documents\PROYECTOS\NEXTPAGE\desktop\.opencode\skills\taste-skill\SKILL.md |

## Project Conventions

| File | Path | Notes |
|------|------|-------|
| None found | | |

Read the convention files listed above for project-specific patterns and rules. All referenced paths have been extracted — no need to read index files to discover more.