package de.cyclingsir.cetrack.common.errorhandling

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class ErrorCodesRangeTest {

    @Test
    fun `domain error codes are client-side statuses in the 4xx range`() {
        assertAll(
            ErrorCodesDomain.entries.map { code ->
                Executable {
                    assertTrue(
                        code.httpStatus in 400..499,
                        "${code.name} has httpStatus=${code.httpStatus}; " +
                            "domain errors must be 4xx; technical errors belong in ErrorCodesService",
                    )
                }
            },
        )
    }

    @Test
    fun `service error codes are server-side statuses in the 5xx range`() {
        assertAll(
            ErrorCodesService.entries.map { code ->
                Executable {
                    assertTrue(
                        code.httpStatus in 500..599,
                        "${code.name} has httpStatus=${code.httpStatus}; " +
                            "technical errors must be 5xx; client/domain errors belong in ErrorCodesDomain",
                    )
                }
            },
        )
    }
}
