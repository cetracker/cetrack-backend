package de.cyclingsir.cetrack.support

import org.junit.jupiter.api.Tag
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest
@Testcontainers
@ActiveProfiles("mysql-it")
@Tag("integration")
abstract class MySQLContainerIT {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql: MySQLContainer = MySQLContainer("mysql:8.0.32")
    }
}
