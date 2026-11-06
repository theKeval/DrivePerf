# DrivePerf — rebrand and validation record

**17 September 2026.** Requested product identity is now **DrivePerf**, domain **driveperf.com**, Android release package **com.driveperf** and subpackages **com.driveperf.***.

## Current locations and external metadata

| Item | Current value |
|---|---|
| Outer workspace | `/Users/keval/data/projects/DrivePerf` |
| Android Git repository | `/Users/keval/data/projects/DrivePerf/DrivePerf` |
| Research | `/Users/keval/data/projects/DrivePerf/resources/research/drive-perf/2026-09-17` |
| Associated historical-branch worktree | `/Users/keval/data/projects/copilot-worktrees/DrivePerf` |
| GitHub repository | [theKeval/DrivePerf](https://github.com/theKeval/DrivePerf), original repository ID `1261720533` preserved |
| Jira space/project | [DrivePerf](https://thekeval.atlassian.net/jira/software/projects/DRIVE/boards/2), project ID `10001`, key **DRIVE**, board ID **2** preserved |
| Project domain | `https://driveperf.com`, set in GitHub/Jira metadata; no website deployment performed |

The outer workspace had already been renamed in the first research turn. This update completes the nested app folder, editor module and source/package changes. The linked worktree's pointers were repaired after the move, then its directory was renamed with Git. It was clean before and after; its historical branch contents were not rebased or rewritten.

## Changes made

- Android namespace/application ID, 62 Kotlin source/test file paths and packages, app/navigation/theme class names, component/action identifiers, labels, resources and ProGuard rules.
- Application and workspace README files, design specifications/HTML, and the branded About mockup PNG. The PNG was regenerated from source at its existing 1080×2400 size. The other mockup had no visible legacy branding.
- Domain identity in the About footer and project documentation; app GitHub links now use the renamed repository.
- Room database filename is `driveperf.db`; schema and preference keys are unchanged.
- Workflow master instructions, installer templates, listener default/example/local repository configuration and project references across `agentic-workflow`.
- Regenerated app agent instructions and `pm-workspace` instructions/README from the masters. Jira `DRIVE`, existing statuses, board ID and Slack identities remain intact.
- Local Git remote updated. The idle workflow listener was restarted to load the new repository setting. No implementation tickets or Slack handoff messages were created.
- Research rewritten around the two confirmed audiences, with additional indie/enthusiast evidence and Cosworth PDR as a future comparison.

The source and documentation changes remain **local and uncommitted** in the Android and workflow repositories. The GitHub repository name/homepage and Jira name/URL changes are already live. No source commits, pushes, merges or application release were performed.

## Validation

Using **Gradle 8.6 and JDK 17**, these existing tasks completed successfully:

```text
:app:testDebugUnitTest :app:assembleDebug :app:lintDebug --continue
```

- **49 unit tests passed**, zero failures/errors/skips; confirmed from the resulting XML reports.
- Debug APK built; inspected metadata confirms label **DrivePerf**, package **com.driveperf.debug**, launcher **com.driveperf.MainActivity**. The release application ID in Gradle is **com.driveperf**.
- Android lint completed with **0 errors and 87 warnings**. Existing source/resource warnings matched the unchanged baseline; other warnings concern dependency-version advisories. This is not a claim of a warning-free production app.
- Two pre-existing test setup issues were repaired so the suite could run: the missing `advanceUntilIdle` import and the missing Kotlin test dependency used by an existing assertion. An unchanged baseline reproduced the original test compilation failure. No test behavior or app feature logic was changed for those repairs.
- Workflow shell syntax, listener JavaScript syntax and Git whitespace checks passed. Local research links and source/package branding were checked.

No device-based road test, release signing/distribution, website hosting or iPhone validation was performed.

## Migration consequence

Changing application ID creates a **separate Android installation and private storage sandbox**. Existing trips, settings, permissions and backups do not transfer automatically. No cross-app migration was implemented; preserve any existing installation that contains needed data until a deliberate export/import approach is chosen.

The domain registration does not configure a backend, email service, verified Android App Links or a Play Store listing. Reopen editors at the new repository path after the filesystem move. Historical commits and the separate historical-branch checkout remain history, not rebranded source revisions.
