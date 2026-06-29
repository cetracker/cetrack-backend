package de.cyclingsir.cetrack

import de.cyclingsir.cetrack.tour.configuration.MyTourbookImportConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication
@EnableJpaRepositories
@EnableJpaAuditing
@EnableConfigurationProperties(MyTourbookImportConfiguration::class)
class MainApplication

fun main(args: Array<String>) {
    runApplication<MainApplication>(*args)
}
