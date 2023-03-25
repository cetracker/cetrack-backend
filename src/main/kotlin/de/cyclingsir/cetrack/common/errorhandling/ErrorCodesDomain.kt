package de.cyclingsir.cetrack.common.errorhandling

/**
 * Initially created on 1/31/23.
 */
enum class ErrorCodesDomain(
    override val code: Int,
    override val httpStatus: Int,
    private val description: String
) : ServiceError {
    PART_NOT_FOUND(100, 404, "Part not found"),
    PART_TYPE_NOT_FOUND(101, 404, "PartType not found"),
    PART_TYPE_NOT_PERISTED(102, 404, "PartType not found"),

    RELATION_NOT_VALID(200, 400, "Relation not valid"),

    PREVIOUS_RELATION_NOT_FOUND(201, 400, "Previous relation to finalize not found"),

    BIKE_NOT_FOUND(300, 404, "Bike not found"),
    BIKE_NOT_PERISTED(301, 404, "Unable to persist bike");

    override val reason: String?
        get() = description

}
