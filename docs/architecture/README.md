# Architecture

- `overview.md`: C4 context and container diagrams, module dependencies (A10)
- `adr/`: architecture decision records (MADR format), `ADR-001` onward (A10)
- `data-model.md`: domain entities, local and cloud schemas, sync, privacy data flow (A11)
- `pipeline-and-scoring.md`: trip state machine, signal processing, scoring and route matching (A12)
- `r2-trip-files.md`: trip-file object layout on Cloudflare R2 and the presigned-URL contract (DRIVE-76)
- `sync-repository-firebase-adapter.md`: how the apps bind `SyncRepository`, `BlobStore` and `AccountService` to the `/v1` API (DRIVE-76)
- `migration-seam.md`: what must stay portable so the VPS move is server-only, and the tests that prove it (DRIVE-76)
- `backend-cost-estimate.md`: monthly backend cost at 1k and 5k DAU, including R2 (DRIVE-76)

These documents are written in parallel with prototyping. No application code is written before the prototypes are approved.
