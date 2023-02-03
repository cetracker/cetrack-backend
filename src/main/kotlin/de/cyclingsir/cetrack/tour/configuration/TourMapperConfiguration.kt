package de.cyclingsir.cetrack.tour.configuration

import de.cyclingsir.cetrack.tour.rest.TourDomain2ApiMapper
import de.cyclingsir.cetrack.tour.rest.TourDomain2ApiMapperImpl
import de.cyclingsir.cetrack.tour.storage.TourDomain2StorageMapper
import de.cyclingsir.cetrack.tour.storage.TourDomain2StorageMapperImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Initially created on 2/1/23.
 */
@Configuration
class TourMapperConfiguration {

    @Bean
    fun getTourDomain2ApiMapper() : TourDomain2ApiMapper = TourDomain2ApiMapperImpl()

    @Bean
    fun getTourDomain2StorageMapper() : TourDomain2StorageMapper = TourDomain2StorageMapperImpl()
}
