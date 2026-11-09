/**
 * Trip state machine, DSP, detectors, scoring and route matching (ADR-002). Pure: no I/O, no clocks except an
 * injected `Clock`. Content arrives with DRIVE-62 from docs/architecture/pipeline-and-scoring.md.
 */
package com.driveperf.pipeline
