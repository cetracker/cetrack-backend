package de.cyclingsir.cetrack.common.errorhandling

import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * initially created on 22.03.2025
 */
 class CentralExceptionHandlerTest {

  private val centralExceptionHandler = CentralExceptionHandler()

  @Test
  fun `null as WebRequest rethrows serviceException`() {

   val serviceException = mockk<ServiceException>()

   val exception = Assertions.assertThrows(ServiceException::class.java) {
    centralExceptionHandler.handleServiceException(serviceException, null)
   }
   Assertions.assertEquals(serviceException, exception)
  }
 }
