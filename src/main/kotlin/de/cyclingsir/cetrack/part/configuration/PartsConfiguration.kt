package de.cyclingsir.cetrack.part.configuration

import de.cyclingsir.cetrack.part.rest.PartDomain2ApiMapper
import de.cyclingsir.cetrack.part.rest.PartDomain2ApiMapperImpl
import de.cyclingsir.cetrack.part.rest.PartPartTypeRelationDomain2ApiMapper
import de.cyclingsir.cetrack.part.rest.PartPartTypeRelationDomain2ApiMapperImpl
import de.cyclingsir.cetrack.part.rest.PartTypeDomain2ApiMapper
import de.cyclingsir.cetrack.part.rest.PartTypeDomain2ApiMapperImpl
import de.cyclingsir.cetrack.part.rest.ReportDomain2ApiMapper
import de.cyclingsir.cetrack.part.rest.ReportDomain2ApiMapperImpl
import de.cyclingsir.cetrack.part.storage.PartDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartDomain2StorageMapperImpl
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartPartTypeRelationDomain2StorageMapperImpl
import de.cyclingsir.cetrack.part.storage.PartTypeDomain2StorageMapper
import de.cyclingsir.cetrack.part.storage.PartTypeDomain2StorageMapperImpl
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

    @Bean
    fun getParPartTypeRelationDomain2StorageMapper() : PartPartTypeRelationDomain2StorageMapper
        = PartPartTypeRelationDomain2StorageMapperImpl()

    @Bean
    fun getParPartTypeRelationDomain2ApiMapper() : PartPartTypeRelationDomain2ApiMapper
        = PartPartTypeRelationDomain2ApiMapperImpl()

    @Bean
    fun getPartTypeDomain2ApiMapper() : PartTypeDomain2ApiMapper
        = PartTypeDomain2ApiMapperImpl()

    @Bean
    fun getPartTypeDomain2StorageMapper() : PartTypeDomain2StorageMapper = PartTypeDomain2StorageMapperImpl()

    @Bean
    fun getReportDomain2ApiMapper() : ReportDomain2ApiMapper = ReportDomain2ApiMapperImpl()

}
