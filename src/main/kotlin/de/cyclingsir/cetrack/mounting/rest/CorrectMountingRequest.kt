package de.cyclingsir.cetrack.mounting.rest

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.OffsetDateTime

/**
 * Hand-written replacement for the generated CorrectMountingRequest, wired via
 * schemaMappings in build.gradle.kts: the kotlin-spring generator cannot express
 * the spec's tri-state dismountedAt (absent = keep, explicit null = re-open,
 * value = set). Jackson invokes a setter only when the property occurs in the
 * JSON - explicit null included - so the presence flags distinguish absent from
 * null without a Jackson-version-specific module.
 */
class CorrectMountingRequest {

    @JsonIgnore
    var mountedAtPresent: Boolean = false
        private set

    var mountedAt: OffsetDateTime? = null
        set(value) {
            field = value
            mountedAtPresent = true
        }

    @JsonIgnore
    var dismountedAtPresent: Boolean = false
        private set

    var dismountedAt: OffsetDateTime? = null
        set(value) {
            field = value
            dismountedAtPresent = true
        }
}
