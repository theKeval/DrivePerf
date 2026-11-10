package com.driveperf.foundation

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * A new record ID: UUIDv7 in lower-case canonical text (data-model.md P3, ADR-003). Time-ordered, so IDs created
 * later sort later, and generated on the device so records can be created offline.
 */
@OptIn(ExperimentalUuidApi::class)
public fun newId(): String = Uuid.generateV7().toString()
