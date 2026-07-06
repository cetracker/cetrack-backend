package de.cyclingsir.cetrack.assembly.configuration

import de.cyclingsir.cetrack.assembly.rest.AssemblyDomain2ApiMapper
import de.cyclingsir.cetrack.assembly.storage.AssemblyDomain2StorageMapper
import de.cyclingsir.cetrack.mounting.rest.MountingDomain2ApiMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AssemblyConfiguration {

    @Bean
    fun getAssemblyDomain2StorageMapper(): AssemblyDomain2StorageMapper = AssemblyDomain2StorageMapper()

    @Bean
    fun getAssemblyDomain2ApiMapper(mountingMapper: MountingDomain2ApiMapper): AssemblyDomain2ApiMapper =
        AssemblyDomain2ApiMapper(mountingMapper)
}
