package de.cyclingsir.cetrack.common.errorhandling

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

class ErrorCodesRangeTest {

    @Test
    fun `domain error codes are client-side statuses below 500`() {
        assertAll(
            ErrorCodesDomain.entries.map { code ->
                Executable {
                    assertTrue(
                        code.httpStatus < 500,
                        "${code.name} has technical httpStatus=${code.httpStatus}; " +
                            "technical errors belong in ErrorCodesService",
                    )
                }
            },
        )
    }

    @Test
    fun `service error codes are server-side statuses 500 and above`() {
        assertAll(
            ErrorCodesService.entries.map { code ->
                Executable {
                    assertTrue(
                        code.httpStatus >= 500,
                        "${code.name} has non-technical httpStatus=${code.httpStatus}; " +
                            "client/domain errors belong in ErrorCodesDomain",
                    )
                }
            },
        )
    }
}
