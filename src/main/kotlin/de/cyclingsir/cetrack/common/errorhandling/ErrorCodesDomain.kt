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
    PART_TYPE_DATA_INVALID(102, 400, "PartType data violates a constraint"),
    PART_NOT_IDENTIFIABLE(103, 400, "A part must have at least a label or a model"),
    PART_PRICE_CURRENCY_MISMATCH(104, 400, "Purchase price and currency code must be provided together"),
    PART_ID_MISMATCH(105, 400, "Path id does not match the part id in the body"),
    PART_TYPE_ID_MISMATCH(106, 400, "Path id does not match the part type id in the body"),
    PART_HAS_FOREIGN_KEY_CONSTRAINT(107, 400, "Part can't be deleted."),
    PART_TYPE_HAS_FOREIGN_KEY_CONSTRAINT(108, 400, "PartType can't be deleted."),
    PART_DATA_INVALID(109, 400, "Part data violates a constraint"),

    RELATION_NOT_VALID(200, 400, "Relation not valid"),

    PREVIOUS_RELATION_NOT_FOUND(201, 400, "Previous relation to finalize not found"),

    BIKE_NOT_FOUND(300, 404, "Bike not found"),
    BIKE_DATA_INVALID(301, 400, "Bike data violates a constraint"),
    BIKE_HAS_FOREIGN_KEY_CONSTRAINT(302, 400, "Bike can't be deleted."),
    BIKE_ID_MISMATCH(303, 400, "Path id does not match the bike id in the body"),

    TOUR_DUPLICATE(400, 409, "Tour already exists");

    override val reason: String?
        get() = description

}
