package com.microute.test

import org.junit.Assert.assertTrue
import org.junit.Test

class RouteEvidenceTest {
    @Test fun stoppedRecordingCannotConfirmAnOldRoute() {
        assertTrue(RouteEvidence.describe(2, 2, false, false).startsWith("UNVERIFIED"))
    }
    @Test fun silencedRecordingIsNotSuccess() {
        assertTrue(RouteEvidence.describe(2, 2, true, true).startsWith("UNVERIFIED"))
    }
    @Test fun missingRouteIsNotSuccess() {
        assertTrue(RouteEvidence.describe(2, null, true, false).startsWith("UNVERIFIED"))
    }
    @Test fun systemDefaultIsObservationNotSelectionSuccess() {
        assertTrue(RouteEvidence.describe(null, 2, true, false).startsWith("OBSERVED"))
    }
    @Test fun rejectedOrOverriddenRoutingIsVisible() {
        assertTrue(RouteEvidence.describe(2, 3, true, false).startsWith("MISMATCH"))
    }
    @Test fun matchingRouteIsLimitedToOurRecording() {
        assertTrue(RouteEvidence.describe(2, 2, true, false).endsWith("this test app only"))
    }
}
