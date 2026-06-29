package de.cyclingsir.cetrack.tour.configuration

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for the MyTourbook import feature.
 * Bound to the {@code app.mytourbook} prefix in application.yaml.
 */
@ConfigurationProperties(prefix = "app.mytourbook")
data class MyTourbookImportConfiguration(
    val workdir: String,
    val maxDecompressedBytes: Long,
    val tourPersonId: Int,
    val tourTypeIds: List<Int>
)
