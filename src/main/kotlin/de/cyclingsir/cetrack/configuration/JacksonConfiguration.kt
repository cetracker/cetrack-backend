package de.cyclingsir.cetrack.configuration

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Configuration

/**
 * Initially created on 3/23/23.
 */
@Configuration
class JacksonConfiguration(objectMapper: ObjectMapper) {

    init {
        /*
         * This setting will preserve the timezone sent to the api.
         * When setting to true a date-time sent to the API with "2023-03-01T00:00:00+01"
         * will be converted to                   OffsetDateTime("2023-02-28T23:00:00Z")
         * Preserving the timezone information is essential when calculating the end of the previous day
         * within the given timezone!
         * which - in the example above - should result in "2023-02-28T22:59:59Z" or "2023-02-28T23:59:59+01"
         */
        objectMapper.configure(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE, false)
    }

}
