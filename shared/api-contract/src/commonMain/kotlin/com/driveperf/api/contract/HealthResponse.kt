package com.driveperf.api.contract

import kotlinx.serialization.Serializable

/** Body of `GET /healthz` in backend/openapi/v1.yaml. */
@Serializable
public data class HealthResponse(val status: String)
