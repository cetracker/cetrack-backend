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

//  Tour domain
    TOUR_DUPLICATE(400, 409, "Tour already exists"),

//  MyTourbook DB import domain
    ARCHIVE_INVALID(500, 400, "Archive is invalid or unreadable"),
    ARCHIVE_EXCEEDS_SIZE_LIMIT(501, 400, "Archive exceeds size limit"),
    DERBY_SCHEMA_INCOMPATIBLE(502, 422, "Derby schema is incompatible with this backend release"),
    IMPORT_SESSION_NOT_FOUND(503, 404, "Import session not found"),
    IMPORT_SESSION_SUPERSEDED(504, 409, "Import session has been superseded or already committed"),
    IMPORT_RESOLUTION_REPLACE_AMBIGUOUS(505, 400, "REPLACE not allowed when multiple existing tours match the triple-key"),
    IMPORT_RESOLUTION_SAME_BIKE(506, 400, "IMPORT_NEW not allowed when incoming bike matches the existing tour bike"),
    IMPORT_TOUR_NOT_FOUND(507, 404, "Matched tour not found");

    override val reason: String?
        get() = description

}
