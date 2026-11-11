# backend/api (Kotlin / Ktor)

The DrivePerf HTTP server ([ADR-002](../../docs/architecture/adr/ADR-002-monorepo-modules-and-clean-architecture.md) section 6, [ADR-004](../../docs/architecture/adr/ADR-004-backend-v1-and-migration-seam.md)). Layers: `routes` (Ktor) → `application` (use cases) → `ports` ← `adapters` (Firestore, signers, verifiers). It shares DTOs with the apps through `:shared:api-contract` and `../openapi/v1.yaml` is the HTTP contract. Today it serves `GET /healthz`. DRIVE-76 added the Blaze-scope use cases behind ports, with no routes and no live adapters yet (they need Firestore, R2 and Secret Manager, which arrive with Phase 6): the account-deletion cascade and its hourly job, `blobs:prepare` and `blobs:commit`, and an S3 SigV4 presigner. See [docs/architecture/r2-trip-files.md](../../docs/architecture/r2-trip-files.md).

```bash
./gradlew :backend:api:run      # http://localhost:8080/healthz
./gradlew :backend:api:test
```

The Docker image (`Dockerfile`) is built by `.github/workflows/backend.yml`; nothing is pushed or deployed until Phase 6 (docs/architecture/ci-cd.md).
