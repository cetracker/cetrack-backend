package de.cyclingsir.cetrack.tour.storage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ImportStateRepository : JpaRepository<ImportStateEntity, Int>
