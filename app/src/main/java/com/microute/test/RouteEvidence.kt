package com.microute.test

data class CaptureRead(val routeId: Int?, val atMs: Long, val peak: Int)

/** Device routing and delivery of audio data are separate observations. */
object RouteEvidence {
    fun describe(
        requestedId: Int?, actualId: Int?, recording: Boolean, silenced: Boolean,
        read: CaptureRead?, nowMs: Long, evidenceSinceMs: Long,
    ): String = when {
        !recording -> "UNVERIFIED — test recording is stopped"
        silenced -> "UNVERIFIED — Android silenced this recording"
        actualId == null -> "UNVERIFIED — no active input reported"
        requestedId != null && requestedId != actualId -> "MISMATCH — Android reports a different input"
        read == null || read.routeId != actualId || read.atMs < evidenceSinceMs ||
            nowMs - read.atMs > 1000 -> "WAITING — route reported, but no recent audio data from it"
        read.peak == 0 -> "DATA ONLY — silent samples received; speak and listen to confirm"
        requestedId == null -> "OBSERVED — audio data arriving on the system default route; listen to confirm"
        else -> "ROUTE + DATA — selected route reports audio in this test app only; listen to confirm"
    }
}
