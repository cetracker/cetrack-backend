package de.cyclingsir.cetrack.mounting.configuration

import de.cyclingsir.cetrack.mounting.rest.MountingDomain2ApiMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MountingConfiguration {

    @Bean
    fun getMountingDomain2ApiMapper(): MountingDomain2ApiMapper = MountingDomain2ApiMapper()
}
