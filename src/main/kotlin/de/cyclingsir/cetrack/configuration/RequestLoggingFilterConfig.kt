package de.cyclingsir.cetrack.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.filter.CommonsRequestLoggingFilter

/**
 * Initially created on 3/6/23.
 */
@Configuration
class RequestLoggingFilterConfig
{
    @Bean
    fun logFilter() : CommonsRequestLoggingFilter {
        var filter = CommonsRequestLoggingFilter()
        filter.setIncludeClientInfo(true)
        filter.setIncludePayload(true)
        filter.setMaxPayloadLength(10000)
        filter.setIncludeHeaders(true)
        filter.setIncludeQueryString(true)
        filter.setAfterMessagePrefix("REQUEST DATA: ")
        return filter;
    }
}
