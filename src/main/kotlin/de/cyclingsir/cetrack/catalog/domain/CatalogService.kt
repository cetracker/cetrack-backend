package de.cyclingsir.cetrack.catalog.domain

import de.cyclingsir.cetrack.catalog.storage.CatalogDomain2StorageMapper
import de.cyclingsir.cetrack.catalog.storage.ComponentTypeRepository
import de.cyclingsir.cetrack.catalog.storage.PositionRepository
import de.cyclingsir.cetrack.common.errorhandling.ErrorCodesDomain
import de.cyclingsir.cetrack.common.errorhandling.ServiceException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Reference data of the CUET model: ComponentType and Position (CE-0083).
 * Both are referenced from bike (mount points), component and - from CE-0086 -
 * assembly slots; deletion is guarded by the FK constraints (409 IN_USE).
 */
@Service
class CatalogService(
    private val componentTypeRepository: ComponentTypeRepository,
    private val positionRepository: PositionRepository,
    private val mapper: CatalogDomain2StorageMapper,
) {

    @Transactional(readOnly = true)
    fun getComponentTypes(): List<DomainComponentType> =
        componentTypeRepository.findAll().map(mapper::map)

    @Transactional(readOnly = true)
    fun getComponentType(id: UUID): DomainComponentType =
        componentTypeRepository.findById(id)
            .orElseThrow { ServiceException(ErrorCodesDomain.COMPONENT_TYPE_NOT_FOUND) }
            .let(mapper::map)

    @Transactional
    fun addComponentType(componentType: DomainComponentType): DomainComponentType = try {
        mapper.map(componentTypeRepository.saveAndFlush(mapper.map(componentType)))
    } catch (e: DataIntegrityViolationException) {
        throw ServiceException(ErrorCodesDomain.COMPONENT_TYPE_DATA_INVALID, "Name must be unique.", e)
    }

    @Transactional
    fun modifyComponentType(id: UUID, componentType: DomainComponentType): DomainComponentType {
        val existing = componentTypeRepository.findById(id)
            .orElseThrow { ServiceException(ErrorCodesDomain.COMPONENT_TYPE_NOT_FOUND) }
        existing.name = componentType.name
        existing.description = componentType.description
        return try {
            mapper.map(componentTypeRepository.saveAndFlush(existing))
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_TYPE_DATA_INVALID, "Name must be unique.", e)
        }
    }

    @Transactional
    fun deleteComponentType(id: UUID) {
        if (!componentTypeRepository.existsById(id)) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_TYPE_NOT_FOUND)
        }
        try {
            componentTypeRepository.deleteById(id)
            componentTypeRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.COMPONENT_TYPE_IN_USE,
                "Component type is referenced by components, mount points or assembly slots.")
        }
    }

    @Transactional(readOnly = true)
    fun getPositions(): List<DomainPosition> =
        positionRepository.findAll().map(mapper::map)

    @Transactional(readOnly = true)
    fun getPosition(id: UUID): DomainPosition =
        positionRepository.findById(id)
            .orElseThrow { ServiceException(ErrorCodesDomain.POSITION_NOT_FOUND) }
            .let(mapper::map)

    @Transactional
    fun addPosition(position: DomainPosition): DomainPosition = try {
        mapper.map(positionRepository.saveAndFlush(mapper.map(position)))
    } catch (e: DataIntegrityViolationException) {
        throw ServiceException(ErrorCodesDomain.POSITION_DATA_INVALID, "Name must be unique.", e)
    }

    @Transactional
    fun modifyPosition(id: UUID, position: DomainPosition): DomainPosition {
        val existing = positionRepository.findById(id)
            .orElseThrow { ServiceException(ErrorCodesDomain.POSITION_NOT_FOUND) }
        existing.name = position.name
        return try {
            mapper.map(positionRepository.saveAndFlush(existing))
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.POSITION_DATA_INVALID, "Name must be unique.", e)
        }
    }

    @Transactional
    fun deletePosition(id: UUID) {
        if (!positionRepository.existsById(id)) {
            throw ServiceException(ErrorCodesDomain.POSITION_NOT_FOUND)
        }
        try {
            positionRepository.deleteById(id)
            positionRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            throw ServiceException(ErrorCodesDomain.POSITION_IN_USE,
                "Position is referenced by mount points or assemblies.")
        }
    }
}
