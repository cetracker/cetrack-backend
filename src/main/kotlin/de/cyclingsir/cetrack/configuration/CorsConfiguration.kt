package de.cyclingsir.cetrack.configuration

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Configuration
import org.springframework.ui.ModelMap
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.context.request.WebRequestInterceptor
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer


/**
 * Initially created on 2/28/23.
 *
 * Inspired by
 * https://docs.spring.io/spring-boot/docs/3.0.3/reference/htmlsingle/#web.servlet.spring-mvc.cors
 *
 * https://www.baeldung.com/spring-cors
 * https://enable-cors.org/server_spring-boot_kotlin.html
 */

private val logger = KotlinLogging.logger {}

@Configuration(proxyBeanMethods = false)
class CorsConfiguration {

//    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/api/**")
                    .allowedOriginPatterns("*")
            }

            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addWebRequestInterceptor(Interceptor())
                super.addInterceptors(registry)
            }
        }
    }
}

class Interceptor: WebRequestInterceptor {
    override fun preHandle(request: WebRequest) {
        if( request is ServletWebRequest ) {
            logger.info { "uri: ${request.request.requestURI}" }
        }
        logger.info { "contextPath: ${request.contextPath}" }
        logger.info { "-------- HEADER ----------------------" }
        request.headerNames.forEach {
            logger.info {  " $it : ${request.getHeader(it)}" }
        }
        logger.info { "-------- PARAMETER ----------------------" }
        request.parameterNames.forEach {
            logger.info {  "$it : ${request.getParameter(it)}" }
        }
        logger.info { "-----------------------------------------" }
    }

    override fun postHandle(request: WebRequest, model: ModelMap?) {
        logger.debug { "postHandle" }
        logger.info { "model keys: ${model?.keys}" }
    }

    override fun afterCompletion(request: WebRequest, ex: Exception?) {
        logger.debug { "afterCompletion" }
    }

}
