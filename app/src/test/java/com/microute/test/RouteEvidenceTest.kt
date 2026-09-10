package com.microute.test

import org.junit.Assert.assertTrue
import org.junit.Test

class RouteEvidenceTest {
    private fun evidence(
        requested: Int? = 2, actual: Int? = 2, recording: Boolean = true,
        silenced: Boolean = false, read: CaptureRead? = CaptureRead(2, 2000, 400),
        now: Long = 2100, since: Long = 1000,
    ) = RouteEvidence.describe(requested, actual, recording, silenced, read, now, since)

    @Test fun stoppedRecordingCannotConfirmAnOldRoute() {
        assertTrue(evidence(recording = false).startsWith("UNVERIFIED"))
    }
    @Test fun silencedRecordingIsNotSuccess() {
        assertTrue(evidence(silenced = true).startsWith("UNVERIFIED"))
    }
    @Test fun missingRouteIsNotSuccess() {
        assertTrue(evidence(actual = null).startsWith("UNVERIFIED"))
    }
    @Test fun oppoBluetoothRouteWithoutSamplesNeverShowsSuccess() {
        assertTrue(evidence(requested = 7553, actual = 7553, read = null).startsWith("WAITING"))
    }
    @Test fun zeroAmplitudeSamplesAreNotReportedAsWorkingAudio() {
        assertTrue(evidence(read = CaptureRead(2, 2000, 0)).startsWith("DATA ONLY"))
    }
    @Test fun stalledStreamLosesDataConfirmation() {
        assertTrue(evidence(now = 3101).startsWith("WAITING"))
    }
    @Test fun oldPhoneSamplesCannotValidateBluetoothAfterSwitch() {
        assertTrue(evidence(requested = 7553, actual = 7553, read = CaptureRead(17, 2000, 600)).startsWith("WAITING"))
    }
    @Test fun samplesBeforeNewSelectionAreNotReused() {
        assertTrue(evidence(since = 2050).startsWith("WAITING"))
    }
    @Test fun samplesAcrossRouteTransitionAreNotAttributed() {
        assertTrue(evidence(read = CaptureRead(null, 2000, 600)).startsWith("WAITING"))
    }
    @Test fun systemDefaultIsObservationNotSelectionSuccess() {
        assertTrue(evidence(requested = null).startsWith("OBSERVED"))
    }
    @Test fun rejectedOrOverriddenRoutingIsVisible() {
        assertTrue(evidence(actual = 3).startsWith("MISMATCH"))
    }
    @Test fun routeAndFramesStillRequireListeningAndDoNotProveGlobalControl() {
        val result = evidence()
        assertTrue(result.startsWith("ROUTE + DATA"))
        assertTrue(result.contains("this test app only; listen to confirm"))
    }
}
