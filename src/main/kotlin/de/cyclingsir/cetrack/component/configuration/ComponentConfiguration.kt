package de.cyclingsir.cetrack.component.configuration

import de.cyclingsir.cetrack.component.rest.ComponentDomain2ApiMapper
import de.cyclingsir.cetrack.component.storage.ComponentDomain2StorageMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ComponentConfiguration {

    @Bean
    fun getComponentDomain2ApiMapper(): ComponentDomain2ApiMapper = ComponentDomain2ApiMapper()

    @Bean
    fun getComponentDomain2StorageMapper(): ComponentDomain2StorageMapper = ComponentDomain2StorageMapper()
}
