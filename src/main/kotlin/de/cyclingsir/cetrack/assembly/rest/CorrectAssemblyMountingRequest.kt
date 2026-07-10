package de.cyclingsir.cetrack.assembly.rest

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.OffsetDateTime

/**
 * Hand-written replacement for the generated CorrectAssemblyMountingRequest, wired via
 * schemaMappings in build.gradle.kts: mirrors CorrectMembershipRequest's tri-state
 * dismountedAt (absent = keep, explicit null = re-open, value = set).
 */
class CorrectAssemblyMountingRequest {

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
