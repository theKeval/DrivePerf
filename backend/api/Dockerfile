# Runtime image for the Ktor server (ADR-008). Build the distribution first:
#   ./gradlew :backend:api:installDist
# backend.yml does this, then builds the image. Nothing is pushed or deployed until the Cloud Run job is
# enabled (Phase 6, after the Blaze upgrade); see docs/architecture/ci-cd.md.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/install/api/ /app/
ENV PORT=8080
EXPOSE 8080
USER 65532:65532
ENTRYPOINT ["/app/bin/api"]
