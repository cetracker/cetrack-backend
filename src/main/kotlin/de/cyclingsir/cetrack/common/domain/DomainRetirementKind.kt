package de.cyclingsir.cetrack.common.domain

/**
 * Why an item (Component or Bike) left the fleet. Shared vocabulary - neither
 * aggregate owns it (CE-0126).
 */
enum class DomainRetirementKind {
    SCRAPPED, SOLD, GIFTED, BROKEN, LOST, STOLEN, WORN_OUT, OTHER
}
