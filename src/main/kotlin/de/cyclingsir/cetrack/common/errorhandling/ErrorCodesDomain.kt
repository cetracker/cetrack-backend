package de.cyclingsir.cetrack.common.errorhandling

/**
 * Initially created on 1/31/23.
 */
enum class ErrorCodesDomain(
    override val code: Int,
    override val httpStatus: Int,
    private val description: String,
    private val wire: String? = null
) : ServiceError {
    BIKE_NOT_FOUND(300, 404, "Bike not found", "NOT_FOUND"),
    BIKE_DATA_INVALID(301, 400, "Bike data violates a constraint", "DATA_INVALID"),
    BIKE_HAS_FOREIGN_KEY_CONSTRAINT(302, 409, "Bike can't be deleted.", "IN_USE"),
    BIKE_ID_MISMATCH(303, 400, "Path id does not match the bike id in the body", "DATA_INVALID"),
    BIKE_NOT_IDENTIFIABLE(304, 400, "A bike must have at least a name or a model", "BIKE_NOT_IDENTIFIABLE"),
    BIKE_PRICE_CURRENCY_MISMATCH(305, 400, "Purchase price and currency code must be provided together", "PRICE_CURRENCY_MISMATCH"),
    BIKE_ALREADY_RETIRED(306, 409, "Bike is already retired", "BIKE_ALREADY_RETIRED"),

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
    COMPONENT_NOT_FOUND(600, 404, "Component not found", "NOT_FOUND"),
    COMPONENT_DATA_INVALID(601, 400, "Component data violates a constraint", "DATA_INVALID"),
    COMPONENT_PRICE_CURRENCY_MISMATCH(602, 400, "Purchase price and currency code must be provided together", "PRICE_CURRENCY_MISMATCH"),
    COMPONENT_IN_USE(603, 409, "Component was mounted or an assembly member; retire it instead of deleting", "IN_USE"),
    COMPONENT_RETIRED(604, 409, "Component is retired", "COMPONENT_RETIRED"),
    RETIRE_PRECONDITION_FAILED(605, 409, "Component still has an active mounting or assembly membership", "RETIRE_PRECONDITION_FAILED"),

//  Catalog domain (CE-0083)
    COMPONENT_TYPE_NOT_FOUND(700, 404, "Component type not found", "NOT_FOUND"),
    COMPONENT_TYPE_DATA_INVALID(701, 400, "Component type data violates a constraint", "DATA_INVALID"),
    COMPONENT_TYPE_IN_USE(702, 409, "Component type is referenced and can't be deleted", "IN_USE"),
    POSITION_NOT_FOUND(703, 404, "Position not found", "NOT_FOUND"),
    POSITION_DATA_INVALID(704, 400, "Position data violates a constraint", "DATA_INVALID"),
    POSITION_IN_USE(705, 409, "Position is referenced and can't be deleted", "IN_USE"),

//  Mounting domain (CE-0083, domain-model.md §4 / ADR-0001)
    MOUNT_POINT_NOT_FOUND(800, 404, "Mount point not found on this bike", "NOT_FOUND"),
    MOUNT_POINT_DATA_INVALID(801, 400, "Mount point data violates a constraint", "DATA_INVALID"),
    MOUNT_POINT_IN_USE(802, 409, "Mount point has mounting history or slot mappings", "IN_USE"),
    MOUNTING_NOT_FOUND(803, 404, "Mounting not found", "NOT_FOUND"),
    TYPE_MISMATCH(804, 409, "Component type does not match the mount point's accepted type", "TYPE_MISMATCH"),
    BIKE_RETIRED(805, 409, "Bike is retired and accepts no new mountings", "BIKE_RETIRED"),
    NOT_MOUNTED(806, 409, "Component has no active mounting", "NOT_MOUNTED"),
    MOUNTING_GOVERNED(807, 409, "Mounting is governed by an assembly mounting", "MOUNTING_GOVERNED"),
    MOUNTING_OVERLAP(808, 409, "Mounting would overlap an existing mounting interval", "MOUNTING_OVERLAP"),
    MOUNTING_BACKDATED(809, 400, "Time must be after the start of every mounting it closes", "MOUNTING_BACKDATED"),
    ASSEMBLY_MEMBER_GUIDED_CHOICE(810, 409, "Component is a member of a not-mounted assembly", "ASSEMBLY_MEMBER_GUIDED_CHOICE"),
    CORRECTION_INVALID(811, 400, "At least one of mountedAt/dismountedAt must be provided and form a valid interval", "CORRECTION_INVALID"),
    SLOT_MAPPING_NOT_FOUND(812, 404, "Slot mapping not found on this bike", "NOT_FOUND"),

//  Assembly domain (CE-0086, domain-model.md §4 / ADR-0001 / ADR-0003)
    ASSEMBLY_NOT_FOUND(813, 404, "Assembly not found", "NOT_FOUND"),
    ASSEMBLY_DATA_INVALID(814, 400, "Assembly data violates a constraint", "DATA_INVALID"),
    ASSEMBLY_SLOT_NOT_FOUND(815, 404, "Assembly slot not found", "NOT_FOUND"),
    ASSEMBLY_SLOT_DATA_INVALID(816, 400, "Assembly slot data violates a constraint", "DATA_INVALID"),
    ASSEMBLY_IN_USE(817, 409, "Assembly has membership or mounting history", "IN_USE"),
    ASSEMBLY_SLOT_IN_USE(818, 409, "Assembly slot has membership history or slot mappings", "IN_USE"),
    ASSEMBLY_INCOMPLETE(819, 409, "Assembly is not complete - every slot needs an active member to mount", "ASSEMBLY_INCOMPLETE"),
    ASSEMBLY_ALREADY_MOUNTED(820, 409, "Assembly already has an active mounting", "ASSEMBLY_ALREADY_MOUNTED"),
    ASSEMBLY_NOT_MOUNTED(821, 409, "Assembly has no active mounting", "ASSEMBLY_NOT_MOUNTED"),
    UNRESOLVED_SLOTS(822, 409, "One or more slots could not be resolved to a mount point", "UNRESOLVED_SLOTS"),
    SLOT_UNMOUNTABLE(823, 409, "A slot has no candidate mount point on this bike", "SLOT_UNMOUNTABLE"),
    SLOT_TARGET_COLLISION(824, 409, "Two slots resolved to the same mount point", "SLOT_TARGET_COLLISION"),
    MEMBER_MOUNTED_ELSEWHERE(826, 409, "Member is mounted at a different mount point", "MEMBER_MOUNTED_ELSEWHERE"),
    MEMBERSHIP_NOT_FOUND(827, 404, "Component has no active membership in this slot", "NOT_FOUND"),
    ALREADY_MEMBER(828, 409, "Component is already an active member of an assembly", "ALREADY_MEMBER"),
    ASSEMBLY_MEMBERSHIP_FILTER_REQUIRED(829, 400, "At least one of slotId/componentId is required", "ASSEMBLY_MEMBERSHIP_FILTER_REQUIRED"),

//  Report domain (CE-0083)
    REPORT_SCOPE_INVALID(900, 400, "scope must be one of: components, bikes", "DATA_INVALID"),

//  Maintenance domain (CE-0088)
    MAINTENANCE_TASK_NOT_FOUND(1000, 404, "Maintenance task not found", "NOT_FOUND"),
    MAINTENANCE_TASK_DATA_INVALID(1001, 400, "Maintenance task data violates a constraint", "DATA_INVALID"),
    MAINTENANCE_EVENT_NOT_FOUND(1002, 404, "Maintenance event not found", "NOT_FOUND");

    override val reason: String?
        get() = description

    override val wireCode: String?
        get() = wire

}
