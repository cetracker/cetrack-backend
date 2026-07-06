package de.cyclingsir.cetrack.catalog.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ComponentTypeRepository : JpaRepository<ComponentTypeEntity, UUID>

@Repository
interface PositionRepository : JpaRepository<PositionEntity, UUID>
