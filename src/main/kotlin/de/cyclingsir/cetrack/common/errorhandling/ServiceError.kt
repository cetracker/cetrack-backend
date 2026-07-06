package de.cyclingsir.cetrack.common.errorhandling

import java.util.function.Supplier

/**
 * Initially created on 1/31/23.
 */

/**
 * Standardized interface for error definitions.
 *
 *
 * Meant to be implemented by an enum which defines error conditions.
 *
 * @see ErrorCodesDomain
 * @see ErrorCodesService
 */
interface ServiceError : Supplier<ServiceException> {
    /**
     * Gets the `ERR` error code.
     *
     * @return the error code
     */
    val code: Int

    /**
     * Gets the HTTP status code for the error.
     *
     * @return the http status code
     */
    val httpStatus: Int

    /**
     * Gets the human-readable explanation for the cause of this error.
     *
     * @return the reason
     */
    val reason: String?

    /**
     * Stable machine-readable code of the shared `Error` schema (common-api.yaml).
     * Non-null on errors of the new domain model: the central handler then emits
     * the generated `Error {code, message, details}` body. Null keeps the legacy
     * `ErrorDetails` shape (old tour endpoints; CE-0085 retires it).
     */
    val wireCode: String?
        get() = null

    /**
     * Stream-Api Support.
     *
     * @return ServiceException containing the given Error
     */
    override fun get(): ServiceException {
        return ServiceException(this, reason)
    }
}
