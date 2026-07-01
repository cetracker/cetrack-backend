package de.cyclingsir.cetrack.configuration

import org.springframework.boot.Banner
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource
import java.io.PrintStream
import java.util.Properties

/**
 * Spring prints the banner before the ApplicationContext refreshes, so a
 * banner.txt with ${git.*} placeholders can't resolve them (no @PropertySource
 * bean is active yet). Read git.properties / build-info.properties directly
 * from the classpath instead, same as this project's reference MBanner.java.
 */
private val ASCII_ART = """
  ____ _____ _____               _
 / ___| ____|_   _| __ __ _  ___| | _____ _ __
| |   |  _|   | || '__/ _` |/ __| |/ / _ \ '__|
| |___| |___  | || | | (_| | (__|   <  __/ |
 \____|_____| |_||_|  \__,_|\___|_|\_\___|_|
""".trimIndent()

class CetrackBanner : Banner {

    override fun printBanner(environment: Environment, sourceClass: Class<*>?, out: PrintStream) {
        out.println(ASCII_ART)

        val build = loadProperties("META-INF/build-info.properties")
        val version = build.getProperty("build.version") ?: "unknown"
        out.println(":: CETracker Backend $version ::")

        val git = loadProperties("git.properties")
        val branch = git.getProperty("git.branch")
        val commitId = git.getProperty("git.commit.id")
        if (branch != null && commitId != null) {
            out.println(":: git branch: $branch | commit: $commitId (${git.getProperty("git.commit.time")}) ::")
        }
        out.println()
    }

    private fun loadProperties(classpathLocation: String): Properties {
        val properties = Properties()
        val resource = ClassPathResource(classpathLocation)
        if (resource.exists()) {
            resource.inputStream.use { properties.load(it) }
        }
        return properties
    }
}
