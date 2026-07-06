package de.cyclingsir.cetrack.common.errorhandling

/**
 * Initially created on 1/31/23.
 */
enum class ErrorCodesDomain(
    override val code: Int,
    override val httpStatus: Int,
    private val description: String
) : ServiceError {
    BIKE_NOT_FOUND(300, 404, "Bike not found"),
    BIKE_DATA_INVALID(301, 400, "Bike data violates a constraint"),
    BIKE_HAS_FOREIGN_KEY_CONSTRAINT(302, 409, "Bike can't be deleted."),
    BIKE_ID_MISMATCH(303, 400, "Path id does not match the bike id in the body"),
    BIKE_NOT_IDENTIFIABLE(304, 400, "A bike must have at least a name or a model"),
    BIKE_PRICE_CURRENCY_MISMATCH(305, 400, "Purchase price and currency code must be provided together"),

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
    IMPORT_TOUR_NOT_FOUND(507, 404, "Matched tour not found"),
    IMPORT_BIKE_NOT_FOUND(508, 400, "Bike referenced in import not found"),

//  Component domain (CE-0083)
    COMPONENT_NOT_FOUND(600, 404, "Component not found"),
    COMPONENT_DATA_INVALID(601, 400, "Component data violates a constraint"),
    COMPONENT_PRICE_CURRENCY_MISMATCH(602, 400, "Purchase price and currency code must be provided together"),
    COMPONENT_IN_USE(603, 409, "Component was mounted or an assembly member; retire it instead of deleting"),
    COMPONENT_RETIRED(604, 409, "Component is retired"),
    RETIRE_PRECONDITION_FAILED(605, 409, "Component still has an active mounting or assembly membership"),

//  Catalog domain (CE-0083)
    COMPONENT_TYPE_NOT_FOUND(700, 404, "Component type not found"),
    COMPONENT_TYPE_DATA_INVALID(701, 400, "Component type data violates a constraint"),
    COMPONENT_TYPE_IN_USE(702, 409, "Component type is referenced and can't be deleted"),
    POSITION_NOT_FOUND(703, 404, "Position not found"),
    POSITION_DATA_INVALID(704, 400, "Position data violates a constraint"),
    POSITION_IN_USE(705, 409, "Position is referenced and can't be deleted");

    override val reason: String?
        get() = description

}
