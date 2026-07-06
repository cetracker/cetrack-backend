package de.cyclingsir.cetrack.assembly.configuration

import de.cyclingsir.cetrack.assembly.storage.AssemblyDomain2StorageMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AssemblyConfiguration {

    @Bean
    fun getAssemblyDomain2StorageMapper(): AssemblyDomain2StorageMapper = AssemblyDomain2StorageMapper()
}
