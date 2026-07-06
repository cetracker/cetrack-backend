package de.cyclingsir.cetrack.common.errorhandling

import java.io.Serial

/**
 * Initially created on 1/31/23.
 */
class ServiceException : RuntimeException {
    @Transient
    private val error: ServiceError

    @Transient
    var details: Map<String, Any>? = null
        private set

    /**
     * Recommended constructor for yielding a complete error without detail message.
     *
     * @param error describing the exception details
     */
    constructor(error: ServiceError) : super(UNSPECIFIED) {
        this.error = error
    }

    /**
     * Constructor for yielding a complete error including a detail message.
     *
     * @param error describing the exception details
     * @param message for adding dynamic exception details
     */
    constructor(error: ServiceError, message: String?) : super(message) {
        this.error = error
    }

    /**
     * Constructor for yielding a complete error including a cause.
     *
     * @param error describing the exception details
     * @param cause of the exception
     */
    constructor(error: ServiceError, cause: Throwable?) : super(UNSPECIFIED, cause) {
        this.error = error
    }

    /**
     * Constructor for yielding a complete error including a detail message and the cause.
     *
     * @param error describing the exception details
     * @param message for adding dynamic exception details
     * @param cause of the exception
     */
    constructor(error: ServiceError, message: String?, cause: Throwable?) : super(message, cause) {
        this.error = error
    }

    /**
     * Constructor for errors carrying a structured payload for the shared
     * `Error.details` field (e.g. the ADR-0001 §3 guided-choice options).
     *
     * @param error describing the exception details
     * @param message for adding dynamic exception details
     * @param details flow-specific structured payload
     */
    constructor(error: ServiceError, message: String?, details: Map<String, Any>) : super(message) {
        this.error = error
        this.details = details
    }

    /**
     * Gets the standardized error including code, http status, and message.
     *
     * @return the error
     */
    fun getError(): ServiceError {
        return error
    }

    companion object {
        const val UNSPECIFIED = "unspecified"

        @Serial
        private val serialVersionUID = 1L
    }
}
