package de.cyclingsir.cetrack.common.errorhandling

import de.cyclingsir.cetrack.infrastructure.api.model.Error
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.web.context.request.WebRequest

/**
 * initially created on 22.03.2025
 */
 class CentralExceptionHandlerTest {

  private val centralExceptionHandler = CentralExceptionHandler()
  private val webRequest = mockk<WebRequest>(relaxed = true)

  @Test
  fun `null as WebRequest rethrows serviceException`() {

   val serviceException = mockk<ServiceException>()

   val exception = Assertions.assertThrows(ServiceException::class.java) {
    centralExceptionHandler.handleServiceException(serviceException, null)
   }
   Assertions.assertEquals(serviceException, exception)
  }

  @Test
  fun `wire-coded error renders the shared Error shape with status from the code`() {
   val response = centralExceptionHandler.handleServiceException(
    ServiceException(ErrorCodesDomain.COMPONENT_RETIRED, "Retired components can't be mounted."),
    webRequest
   )

   Assertions.assertEquals(409, response.statusCode.value())
   val body = Assertions.assertInstanceOf(Error::class.java, response.body)
   Assertions.assertEquals("COMPONENT_RETIRED", body.code)
   Assertions.assertTrue(body.message.contains("Retired components can't be mounted."))
   Assertions.assertNull(body.details)
  }

  @Test
  fun `guided-choice details pass through unchanged`() {
   val details = mapOf(
    "assemblyId" to "a-1",
    "options" to listOf("MOUNT_ASSEMBLY_INSTEAD", "REMOVE_MEMBERSHIP_THEN_MOUNT")
   )
   val response = centralExceptionHandler.handleServiceException(
    ServiceException(ErrorCodesDomain.ASSEMBLY_MEMBER_GUIDED_CHOICE, "two options", details),
    webRequest
   )

   Assertions.assertEquals(409, response.statusCode.value())
   val body = Assertions.assertInstanceOf(Error::class.java, response.body)
   Assertions.assertEquals("ASSEMBLY_MEMBER_GUIDED_CHOICE", body.code)
   Assertions.assertEquals(details, body.details)
  }

  @Test
  fun `legacy error without wireCode keeps the old ErrorDetails shape`() {
   val response = centralExceptionHandler.handleServiceException(
    ServiceException(ErrorCodesDomain.TOUR_DUPLICATE),
    webRequest
   )

   Assertions.assertEquals(409, response.statusCode.value())
   val body = Assertions.assertInstanceOf(ErrorDetails::class.java, response.body)
   Assertions.assertEquals(ErrorCodesDomain.TOUR_DUPLICATE.code, body.code)
  }
 }
