# CETRacker Domain Model

## Visualisation

```mermaid
classDiagram
    direction LR

    class Bike {
        name (optional)
        model
        manufacturer
        purchaseDate
        price + currency
        retiredAt
    }
    class MountPoint {
        name
        mandatory : bool
    }
    class ComponentType {
        name
    }
    class Position {
        name
    }
    class SlotMapping {
    }
    class Component {
        label
        manufacturer
        model
        serialNumber
        vendor
        purchaseDate
        price + currency
        lifecycleState
    }
    class ComponentAssembly {
        name
    }
    class AssemblySlot {
        name
    }
    class Mounting {
        mountedAt
        dismountedAt
        provenance
        adopted
    }
    class AssemblyMembership {
        from
        to
    }
    class AssemblyMounting {
        from
        to
    }
    class Tour {
        title
        startedAt
        durationMoving
        durationRecorded
        durationElapsed
        distance
        ascent
        descent
        powerTotal
        externalRef
        source
    }
    class MaintenanceTask {
        name
        distanceInterval
        timeInterval
    }
    class MaintenanceEvent {
        performedAt
    }

    Bike "1" *-- "0..*" MountPoint : BikeComposition
    MountPoint "0..*" --> "1" ComponentType : accepts
    Component "0..*" --> "1" ComponentType : is of
    AssemblySlot "0..*" --> "1" ComponentType : accepts
    ComponentAssembly "1" *-- "0..*" AssemblySlot
    MountPoint "0..*" --> "0..1" Position : at
    ComponentAssembly "0..*" --> "0..1" Position : is for
    SlotMapping "0..*" --> "1" AssemblySlot
    SlotMapping "0..*" --> "1" Bike
    SlotMapping "0..*" --> "1" MountPoint : resolves to

    Mounting "0..*" --> "1" Component
    Mounting "0..*" --> "1" MountPoint
    AssemblyMembership "0..*" --> "1" Component
    AssemblyMembership "0..*" --> "1" AssemblySlot
    AssemblyMounting "0..*" --> "1" ComponentAssembly
    AssemblyMounting "0..*" --> "1" Bike
    Mounting "0..*" --> "0..1" AssemblyMounting : provenance

    Tour "0..*" --> "1" Bike
    MaintenanceTask "0..*" --> "1" Bike
    MaintenanceTask "1" *-- "0..*" MaintenanceEvent
```

Type-compatibility rule (not expressible in the diagram): `Mounting.component.componentType == Mounting.mountPoint.acceptsType`, and likewise for AssemblyMembership/AssemblySlot.
