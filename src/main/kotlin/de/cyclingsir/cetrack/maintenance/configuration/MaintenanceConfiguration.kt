package de.cyclingsir.cetrack.maintenance.configuration

import de.cyclingsir.cetrack.maintenance.rest.MaintenanceDomain2ApiMapper
import de.cyclingsir.cetrack.maintenance.storage.MaintenanceDomain2StorageMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MaintenanceConfiguration {

    @Bean
    fun getMaintenanceDomain2ApiMapper(): MaintenanceDomain2ApiMapper = MaintenanceDomain2ApiMapper()

    @Bean
    fun getMaintenanceDomain2StorageMapper(): MaintenanceDomain2StorageMapper = MaintenanceDomain2StorageMapper()
}
