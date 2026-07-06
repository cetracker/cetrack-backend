package de.cyclingsir.cetrack.catalog.configuration

import de.cyclingsir.cetrack.catalog.rest.CatalogDomain2ApiMapper
import de.cyclingsir.cetrack.catalog.rest.CatalogDomain2ApiMapperImpl
import de.cyclingsir.cetrack.catalog.storage.CatalogDomain2StorageMapper
import de.cyclingsir.cetrack.catalog.storage.CatalogDomain2StorageMapperImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class CatalogConfiguration {

    @Bean
    fun getCatalogDomain2ApiMapper(): CatalogDomain2ApiMapper = CatalogDomain2ApiMapperImpl()

    @Bean
    fun getCatalogDomain2StorageMapper(): CatalogDomain2StorageMapper = CatalogDomain2StorageMapperImpl()
}
