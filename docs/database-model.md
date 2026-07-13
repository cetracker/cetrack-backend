# CETracker Database Model

## Modeling language & visualisation

**Notation:** relational model in **crow's-foot ER notation** — the de-facto standard for relational schemas and directly expressible as Mermaid `erDiagram` **Engine: PostgreSQL 17** (partial unique indexes and exclusion constraints enforce the temporal invariants declaratively; see integrity rules below).

## ER diagram

```mermaid
erDiagram
    component_type {
        id id PK
        string name
    }
    position {
        id id PK
        string name
    }
    bike {
        id id PK
        string name "NULL allowed"
        string model
        string manufacturer
        date purchase_date
        string price
        char3 price_currency
        datetime retired_at "NULL = active"
    }
    mount_point {
        id id PK
        id bike_id FK
        id component_type_id FK
        id position_id FK "NULL allowed"
        string name
        bool mandatory
    }
    component {
        id id PK
        id component_type_id FK
        string label
        string manufacturer
        string model
        string serial_number
        string vendor
        date purchase_date
        string price
        char3 price_currency
        datetime retired_at
        string retirement_kind
    }
    component_assembly {
        id id PK
        id position_id FK "NULL allowed"
        string name
    }
    assembly_slot {
        id id PK
        id assembly_id FK
        id component_type_id FK
        string name
        datetime valid_from
        datetime valid_to
    }
    mounting {
        id id PK
        id component_id FK
        id mount_point_id FK
        id assembly_mounting_id FK "NULL = direct mount"
        bool adopted "pre-existing direct mount adopted by assembly"
        datetime mounted_at
        datetime dismounted_at "NULL = active"
    }
    assembly_membership {
        id id PK
        id component_id FK
        id assembly_slot_id FK
        datetime member_from
        datetime member_to "NULL = active"
    }
    assembly_mounting {
        id id PK
        id assembly_id FK
        id bike_id FK
        datetime mounted_at
        datetime dismounted_at "NULL = active"
    }
    slot_mapping {
        id id PK
        id assembly_slot_id FK
        id bike_id FK
        id mount_point_id FK
    }
    tour {
        id id PK
        id bike_id FK
        string mt_tour_id
        string title
        string source
        datetime started_at
        smallint start_year "denormalized"
        smallint start_month "denormalized"
        smallint start_day "denormalized"
        bigint duration_moving "mileage-relevant"
        bigint duration_recorded
        bigint duration_elapsed
        int distance "m"
        int ascent "m"
        int descent "m"
        bigint power_total "J"
        datetime updated_at
    }
    maintenance_task {
        id id PK
        id bike_id FK
        string name
        bigint distance_interval
        bigint time_interval
    }
    maintenance_event {
        id id PK
        id maintenance_task_id FK
        datetime performed_at
    }

    bike ||--o{ mount_point : "has"
    component_type ||--o{ mount_point : "accepted by"
    position |o--o{ mount_point : "at"
    position |o--o{ component_assembly : "is for"
    assembly_slot ||--o{ slot_mapping : ""
    bike ||--o{ slot_mapping : ""
    mount_point ||--o{ slot_mapping : "resolves to"
    component_type ||--o{ component : "classifies"
    component_type ||--o{ assembly_slot : "accepted by"
    component_assembly ||--o{ assembly_slot : "has"
    component ||--o{ mounting : ""
    mount_point ||--o{ mounting : ""
    assembly_mounting |o--o{ mounting : "provenance"
    component ||--o{ assembly_membership : ""
    assembly_slot ||--o{ assembly_membership : ""
    component_assembly ||--o{ assembly_mounting : ""
    bike ||--o{ assembly_mounting : ""
    bike ||--o{ tour : "ridden on"
    bike ||--o{ maintenance_task : ""
    maintenance_task ||--o{ maintenance_event : ""
```
