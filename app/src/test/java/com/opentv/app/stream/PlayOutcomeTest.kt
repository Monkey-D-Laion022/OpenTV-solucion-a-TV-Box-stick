package com.opentv.app.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayOutcomeTest {
    @Test
    fun confirmedWins() {
        assertEquals(PlayOutcome.CONFIRMED, PlayOutcome.decide(accepted = true, tvIsPulling = false))
        assertEquals(PlayOutcome.CONFIRMED, PlayOutcome.decide(accepted = true, tvIsPulling = true))
    }

    @Test
    fun errorWhileTvDownloadsKeepsStreaming() {
        // Caso real: LG NetCast contesta «Unreachable» al Play pero ya lee el video.
        assertEquals(PlayOutcome.TV_IS_PULLING, PlayOutcome.decide(accepted = false, tvIsPulling = true))
    }

    @Test
    fun errorWithoutDownloadFails() {
        assertEquals(PlayOutcome.FAILED, PlayOutcome.decide(accepted = false, tvIsPulling = false))
    }
}
