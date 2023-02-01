package de.cyclingsir.cetrack.bike.configuration

import de.cyclingsir.cetrack.bike.rest.BikeDomain2ApiMapper
import de.cyclingsir.cetrack.bike.rest.BikeDomain2ApiMapperImpl
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapper
import de.cyclingsir.cetrack.bike.storage.BikeDomain2StorageMapperImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Initially created on 2/1/23.
 */
@Configuration
class BikeMapperConfiguration {

    @Bean
    fun getBikeDomain2ApiMapper() : BikeDomain2ApiMapper = BikeDomain2ApiMapperImpl()

    @Bean
    fun getBikeDomain2StorageMapper() : BikeDomain2StorageMapper = BikeDomain2StorageMapperImpl()
}
