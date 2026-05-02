# Session log

Append-only log of Claude Code changes. Newest first. One section per session.

Format:
```
## YYYY-MM-DD HH:MM
- bullet describing what changed
- ...
```

---

## 2026-05-02 14:45
- Created `docs/SESSION_LOG.md` (this file).
- Created root `CLAUDE.md` with the instruction to append to this log after making changes.
- Fixed broken SLF4J log line in `backend/.../stats/StatsComputationService.java` — `{:.1f}%` (Python format) was being printed literally and shifting `winRate*100` into the `pnl={}` slot. Switched to `{}` placeholders with a pre-formatted `String.format("%.1f", ...)`.
- Added `.claude/` to `.gitignore` (local session lock files).
- First real commit of the project: backend, frontend, docs, CLAUDE.md, .gitignore.
