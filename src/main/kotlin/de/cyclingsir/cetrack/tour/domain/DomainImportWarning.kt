package de.cyclingsir.cetrack.tour.domain

data class DomainImportWarning(
    val type: String,
    val mtTourId: String?,
    val message: String
)
