package de.cyclingsir.cetrack.common.errorhandling

/**
 * Initially created on 1/31/23.
 */
enum class ErrorCodesService(
    override val code: Int,
    override val httpStatus: Int,
    private val description: String
) : ServiceError {
    INTERNAL_SERVER_ERROR(500, 500, "The backend encountered an unexpected error.");

    override val reason: String?
        get() = description
}
