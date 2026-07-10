package de.cyclingsir.cetrack.assembly.rest

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.OffsetDateTime

/**
 * Hand-written replacement for the generated CorrectMembershipRequest, wired via
 * schemaMappings in build.gradle.kts: the kotlin-spring generator cannot express
 * the spec's tri-state memberTo (absent = keep, explicit null = re-open,
 * value = set). Jackson invokes a setter only when the property occurs in the
 * JSON - explicit null included - so the presence flags distinguish absent from
 * null without a Jackson-version-specific module.
 */
class CorrectMembershipRequest {

    @JsonIgnore
    var memberFromPresent: Boolean = false
        private set

    var memberFrom: OffsetDateTime? = null
        set(value) {
            field = value
            memberFromPresent = true
        }

    @JsonIgnore
    var memberToPresent: Boolean = false
        private set

    var memberTo: OffsetDateTime? = null
        set(value) {
            field = value
            memberToPresent = true
        }
}
