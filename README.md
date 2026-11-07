# DrivePerf

**Every drive, decoded.** DrivePerf is a phone-based driving analytics app for iOS and Android. It is built for everyday driving enthusiasts and for drivers who want smoother, more efficient driving.

[driveperf.com](https://driveperf.com) · [Jira DRIVE](https://thekeval.atlassian.net/jira/software/projects/DRIVE/summary) · App ID `com.driveperf.app`

## Status

The project is in **v1 preparation**. The cross-platform v1 is being specified from scratch, in this order:

1. **Product requirements**: research, then the PRD in `docs/product/`
2. **Jira backlog**: epics and stories in DRIVE, created from the approved PRD
3. **Prototypes**: HTML/CSS/JS prototypes for iOS (Liquid Glass), Android (Material 3 Expressive) and web, in `prototypes/`
4. **Development**: starts only after the prototypes are approved

See the [v1 kickoff plan](docs/planning/DrivePerf-v1-kickoff-plan.md) for the phases, gates and agent prompts.

## Repository layout

| Path | Purpose |
|---|---|
| `docs/planning/` | Kickoff plan, phase gates, agent prompts |
| `docs/research/` | Market, competitor and platform research (`2026-09` Android pass, `2026-11` v1 refresh) |
| `docs/product/` | PRD, strategy, decisions log, Jira map |
| `docs/design/` | Design system, motion language, per-ticket design artifacts (`docs/design/<JIRA-KEY>/`) |
| `docs/architecture/` | Architecture overview, ADRs, data model, pipeline and scoring spec |
| `prototypes/` | Clickable HTML/CSS/JS prototypes: `ios/`, `android/`, `web/` |
| `legacy/android-prototype/` | The frozen Android-only prototype (Kotlin/Compose). For reference only; it is not the v1 codebase. Also tagged `android-prototype-v0`. |

Application code (`shared/`, `apps/ios/`, `apps/android/`, `backend/`, `web/`) is added during the development phase, after the prototypes are approved.

## Conventions

Every branch, commit, issue and PR carries a Jira key (`DRIVE-<n>`). See [AGENTS.md](AGENTS.md).
