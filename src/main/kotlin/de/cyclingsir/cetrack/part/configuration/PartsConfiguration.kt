package de.cyclingsir.cetrack.part.configuration

import de.cyclingsir.cetrack.part.rest.PartDomain2ApiMapper
import de.cyclingsir.cetrack.part.rest.PartDomain2ApiMapperImpl
import de.cyclingsir.cetrack.part.storage.PartDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartDomain2StorageMapperImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Initially created on 1/27/23.
 */
@Configuration
class PartsConfiguration {

    @Bean
    fun getPartDomain2ApiMapper() : PartDomain2ApiMapper = PartDomain2ApiMapperImpl()

    @Bean
    fun getPartDomain2StorageMapper() : PartDomain2StorageMapper = PartDomain2StorageMapperImpl()

}
