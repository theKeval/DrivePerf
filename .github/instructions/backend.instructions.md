---
applyTo: "backend/api/**,backend/openapi/**,backend/ops/**"
---

# Backend rules (DrivePerf `backend/api`, Kotlin / Ktor)

Read ADR-002 section 6 and ADR-004. `backend/firebase/` is owned by the website workflow (`web.yml`): leave it alone.

- **Layers:** `routes` (Ktor, validation, auth) → `application` (use cases) → `ports` ← `adapters` (Firestore, signers,
  verifiers). `application` and `ports` import no Ktor and no adapters; `routes` never touch adapters directly
  (Konsist enforces this). Wiring happens in `Application.module()` only.
- **Contract first:** `backend/openapi/v1.yaml` is the HTTP contract; DTOs live in `:shared:api-contract` and are the
  only `shared/` module the server may use. Change the spec and the DTOs together; CI lints the spec.
- **No secrets in the repo.** Cloud Run deploys are inert until Phase 6 (`docs/architecture/ci-cd.md`).
- **Tests:** Ktor `testApplication` for routes, fakes for ports. Run `./gradlew :backend:api:detekt :backend:api:test`.
