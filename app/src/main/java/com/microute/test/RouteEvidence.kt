package com.microute.test

/** A request being accepted is never evidence of the active route. */
object RouteEvidence {
    fun describe(requestedId: Int?, actualId: Int?, recording: Boolean, silenced: Boolean): String = when {
        !recording -> "UNVERIFIED — test recording is stopped"
        silenced -> "UNVERIFIED — Android silenced this recording"
        actualId == null -> "UNVERIFIED — no active input reported"
        requestedId == null -> "OBSERVED — system default input is active"
        requestedId == actualId -> "MATCH — selected input is active in this test app only"
        else -> "MISMATCH — Android is using a different input"
    }
}
