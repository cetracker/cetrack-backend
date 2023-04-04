package de.cyclingsir.cetrack.common.errorhandling

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

/**
 * Initially created on 1/31/23.
 */

@ControllerAdvice
class CentralExceptionHandler : ResponseEntityExceptionHandler() {

    @ExceptionHandler
    fun handleServiceException(ex: ServiceException, req: WebRequest?) : ResponseEntity<Any> {
        return req?.let{ wr ->

            ErrorDetails(ex.getError().code,
                ex.javaClass.name,
                "${ex.getError().reason}: ${ex.message}",
                wr.contextPath,
                ex.getError().httpStatus)
        }
        .let {ed ->
            letSpringHandlePreparedError(ed!!, ex, req!!)
        }
        ?: throw ex

    }

    /*
   * @see ResponseEntityExceptionHandler#handleExceptionInternal
   */
    private fun letSpringHandlePreparedError(
        ed: ErrorDetails, ex: Exception, req: WebRequest
    ): ResponseEntity<Any>? {
        return handleExceptionInternal(
            ex, ed, supplyHeaders(), HttpStatus.valueOf(ed.status), req
        )
    }

    private fun supplyHeaders(): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = MediaType("application", "de.cyclingsir.v1+json")
        return headers
    }
/*

    protected fun handleServiceException(ex: ServiceException, req: WebRequest?): ResponseEntity<Any> {
        return Optional.ofNullable(req)
            .map(
                Function<WebRequest, Any> { wr: WebRequest? ->
                    ErrorDetails.builder()
                        .withError(ex.getError().getCode())
                        .withException(ex.getClass().getName())
                        .withMessage(
                            if (ex.getMessage().equals(ServiceException.UNSPECIFIED))
                                ex.getError().getReason()
                            else
                                String.format("%s: %s", ex.getError().getReason(), ex.getMessage())
                        )
                        .withPath(this.parsePath(wr))
                        .withStatus(ex.getError().getHttpStatus())
                        .build()
                })
            .map<Any> { ed: Any? ->
                this.logException(
                    ed,
                    ex
                )
            }
            .map(Function<Any, ResponseEntity<Any>> { ed: Any? ->
                this.letSpringHandlePreparedError(
                    ed,
                    ex,
                    req
                )
            })
            .orElseThrow<RuntimeException> { ex }
    }
*/
}
