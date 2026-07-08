#!/usr/bin/env python3
"""CE-0043: generates src/main/resources/data-demo.sql (PostgreSQL, CUET schema).

Deterministic: fixed RNG seed, uuid5-derived IDs (legacy IDs from the old
data-demo.sql are kept verbatim). Re-running produces an identical file.

The script mirrors what AssemblyMountingService would write: governed member
mountings are the intersection of a slot membership interval with the
assembly's mounting period, carry assembly_mounting_id, and target the bike's
mount point resolved by (component type, assembly position). All gist EXCLUDE
invariants (V1.0 + V1.1) are asserted before the SQL is emitted.

Usage: uv run tools/demo-data/generate_demo_data.py   (from backend/)
"""

from __future__ import annotations

import random
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

SEED = 20260708
NS = uuid.uuid5(uuid.NAMESPACE_URL, "cetrack-demo")
OUT = Path(__file__).resolve().parents[2] / "src" / "main" / "resources" / "data-demo.sql"

rng = random.Random(SEED)


def uid(key: str) -> str:
    return str(uuid.uuid5(NS, key))


def dt(y: int, m: int, d: int, hh: int = 12, mm: int = 0, ss: int = 0) -> datetime:
    return datetime(y, m, d, hh, mm, ss, tzinfo=timezone.utc)


def sql_ts(t: datetime | None) -> str:
    return "null" if t is None else "'" + t.strftime("%Y-%m-%d %H:%M:%S+00") + "'"


def sql_date(t: datetime | None) -> str:
    return "null" if t is None else "'" + t.strftime("%Y-%m-%d") + "'"


def sql_str(s: str | None) -> str:
    return "null" if s is None else "'" + s.replace("'", "''") + "'"


def sql_id(s: str | None) -> str:
    """Quote-or-null for values that never contain quotes (UUIDs, currency codes)."""
    return "null" if s is None else f"'{s}'"


# --------------------------------------------------------------------------
# Legacy IDs carried over from the pre-CE-0080 data-demo.sql
# --------------------------------------------------------------------------
LEG_BIKE_FRAGOLA = "70556a9d-bf01-42eb-b882-b5938fff7023"
LEG_BIKE_TT = "99a133db-4f9b-4a5b-b6f9-8a5ec96d9db7"
LEG = {
    "brake_f": "ca68d5a1-cb85-4e4a-a719-4aa380b76325",
    "brake_r": "f8d98a1d-ab33-4df1-b6c5-eefdf4aa3dca",
    "fd_chorus": "f351b44c-a49b-4871-bd0f-50f7af790b8c",
    "rd_veloce": "d33a6e96-eb63-4279-8a0c-521b8a4f25d4",
    "cassette1": "4bbf00a2-7539-4d5f-944f-aa524aa1323a",
    "chain1": "75efe2d4-bc85-430b-af26-1deb58409b43",
    "tire_stelvio_f": "9355e25b-78b4-44b3-9a13-7597657f988c",
    "tire_stelvio_r": "e7781636-a761-40b4-b588-c26925a071ec",
    "tire_gp4k_f": "d972d4e5-1c73-4c8e-be50-354c9742c22a",
    "tire_gp4k_r": "4a634d19-0c24-4f4e-a262-04b5e3251913",
    "dt_f": "8d8e2e59-645d-4228-a007-cd17e5d62149",
    "dt_r": "5cb5afb9-3e1a-4f10-8082-6d72b7b27335",
    "zonda_f": "dcbdcfc6-8c0c-4677-a5e4-61b4dc01f526",
    "zonda_r": "80bbc300-5475-4932-8296-c00aaeaa9e5f",
    "saddle_si": "5fce029f-1e0d-47d0-b220-e604cafc5451",
    "hed_f": "35300fd6-751c-42eb-8632-30ce2646958c",
}
LEG_TOUR_GIAU = "8e449d84-5373-4285-9f65-bcd49f86c67e"
LEG_TOUR_TOURMALET = "2bc77a21-e0b7-4600-a5fa-385b0a5c5e21"

# --------------------------------------------------------------------------
# Catalog
# --------------------------------------------------------------------------
POSITIONS = ["Front", "Rear"]
TYPES = [
    "Wheel", "Tire", "Cassette", "Chain", "Front Derailleur", "Rear Derailleur",
    "Rim Brake", "Disc Brake", "Brake Disc", "Saddle", "Handlebar", "Pedals",
]
pos_id = {p: uid(f"position:{p}") for p in POSITIONS}
type_id = {t: uid(f"type:{t}") for t in TYPES}

# --------------------------------------------------------------------------
# Bikes
# --------------------------------------------------------------------------
@dataclass
class Bike:
    key: str
    id: str
    name: str
    manufacturer: str
    model: str
    purchase: datetime
    price: str
    currency: str
    retired_at: datetime | None = None


BIKES = [
    Bike("b1", uid("bike:b1"), "Steel Classic", "Koga-Miyata", "Full Pro Road",
         dt(1995, 4, 12), "2799", "DEM", retired_at=dt(2011, 9, 30)),
    Bike("b2", LEG_BIKE_FRAGOLA, "Road Racer", "Vaust", "Fragola",
         dt(2006, 6, 15, 14), "2199", "EUR"),
    Bike("b3", LEG_BIKE_TT, "TT Machine", "Cycle", "TT",
         dt(2010, 8, 15), "2799", "EUR"),
    Bike("b4", uid("bike:b4"), "Gravel Explorer", "Specialized", "Diverge Comp DSW",
         dt(2016, 4, 2), "2499", "EUR"),
]
bike_by_key = {b.key: b for b in BIKES}

# Mount points: (type, position, mandatory)
ROAD_MPS = [
    ("Front Wheel", "Wheel", "Front", True), ("Rear Wheel", "Wheel", "Rear", True),
    ("Front Tire", "Tire", "Front", True), ("Rear Tire", "Tire", "Rear", True),
    ("Cassette", "Cassette", "Rear", True), ("Chain", "Chain", None, True),
    ("Saddle", "Saddle", None, True),
    ("Front Derailleur", "Front Derailleur", None, False),
    ("Rear Derailleur", "Rear Derailleur", None, False),
    ("Front Rim Brake", "Rim Brake", "Front", False),
    ("Rear Rim Brake", "Rim Brake", "Rear", False),
    ("Handlebar", "Handlebar", None, False), ("Pedals", "Pedals", None, False),
]
DISC_MPS = [
    ("Front Wheel", "Wheel", "Front", True), ("Rear Wheel", "Wheel", "Rear", True),
    ("Front Tire", "Tire", "Front", True), ("Rear Tire", "Tire", "Rear", True),
    ("Front Brake Disc", "Brake Disc", "Front", True),
    ("Rear Brake Disc", "Brake Disc", "Rear", True),
    ("Cassette", "Cassette", "Rear", True), ("Chain", "Chain", None, True),
    ("Saddle", "Saddle", None, True),
    ("Front Derailleur", "Front Derailleur", None, False),
    ("Rear Derailleur", "Rear Derailleur", None, False),
    ("Front Disc Brake", "Disc Brake", "Front", False),
    ("Rear Disc Brake", "Disc Brake", "Rear", False),
    ("Handlebar", "Handlebar", None, False), ("Pedals", "Pedals", None, False),
]

mount_points: list[tuple[str, str, str, str, str | None, bool]] = []  # id, bike, name, type, pos, mandatory
mp_lookup: dict[tuple[str, str, str | None], str] = {}
for bike_key, mps in (("b1", ROAD_MPS), ("b2", ROAD_MPS), ("b3", ROAD_MPS), ("b4", DISC_MPS)):
    for name, typ, pos, mandatory in mps:
        mp = uid(f"mp:{bike_key}:{name}")
        mount_points.append((mp, bike_key, name, typ, pos, mandatory))
        mp_lookup[(bike_key, typ, pos)] = mp


def resolve_mp(bike_key: str, typ: str, assembly_pos: str | None) -> str:
    """Slot resolution the way SlotResolver does: by type, filtered by position."""
    exact = mp_lookup.get((bike_key, typ, assembly_pos))
    if exact:
        return exact
    unpositioned = mp_lookup.get((bike_key, typ, None))
    if unpositioned:
        return unpositioned
    # cassette slots live in Rear assemblies; MP position matches
    raise AssertionError(f"no mount point for {bike_key}/{typ}/{assembly_pos}")


# --------------------------------------------------------------------------
# Components
# --------------------------------------------------------------------------
@dataclass
class Component:
    key: str
    typ: str
    label: str
    manufacturer: str | None
    model: str | None
    purchase: datetime | None
    price: str | None
    vendor: str | None = None
    serial: str | None = None
    retired_at: datetime | None = None
    retirement_kind: str | None = None  # 'scrapped' | 'sold'
    id: str = ""

    def __post_init__(self):
        if not self.id:
            self.id = LEG.get(self.key) or uid(f"component:{self.key}")


VENDORS = ["Bike24", "Rose Versand", "bike-components.de", "Local Bike Shop", "Chain Reaction Cycles"]


def vend() -> str:
    return rng.choice(VENDORS)


def serial() -> str:
    return "SN-" + "".join(rng.choices("0123456789ABCDEF", k=8))


COMPONENTS = [
    # --- legacy B2 (Vaust Fragola) parts, enriched with CE-0003 attributes ---
    Component("brake_f", "Rim Brake", "Veloce brake front", "Campagnolo", "Veloce Skeleton", dt(2006, 6, 15), "39.90", vend()),
    Component("brake_r", "Rim Brake", "Veloce brake rear", "Campagnolo", "Veloce Skeleton", dt(2006, 6, 15), "39.90", vend()),
    Component("fd_chorus", "Front Derailleur", "Chorus front derailleur", "Campagnolo", "Chorus 10s", dt(2006, 6, 15), "79.00", vend(), serial()),
    Component("rd_veloce", "Rear Derailleur", "Veloce rear derailleur", "Campagnolo", "Veloce 10s", dt(2006, 6, 15), "69.00", vend(), serial()),
    Component("cassette1", "Cassette", "Veloce cassette 12-25", "Campagnolo", "Veloce UD 10s 12-25", dt(2006, 6, 15), "54.90", vend(),
              retired_at=dt(2016, 6, 1), retirement_kind="scrapped"),
    Component("chain1", "Chain", "Veloce chain", "Campagnolo", "Veloce C10", dt(2006, 6, 15), "24.90", vend(),
              retired_at=dt(2010, 6, 1), retirement_kind="scrapped"),
    Component("tire_stelvio_f", "Tire", "Schwalbe Stelvio 23mm front", "Schwalbe", "Stelvio 23-622", dt(2006, 6, 15), "29.90", vend(),
              retired_at=dt(2013, 4, 1), retirement_kind="scrapped"),
    Component("tire_stelvio_r", "Tire", "Schwalbe Stelvio 23mm rear", "Schwalbe", "Stelvio 23-622", dt(2006, 6, 15), "29.90", vend(),
              retired_at=dt(2013, 4, 1), retirement_kind="scrapped"),
    Component("tire_gp4k_f", "Tire", "Conti GP4000S 25mm front", "Continental", "Grand Prix 4000S 25-622", dt(2010, 8, 6), "34.90", vend(),
              retired_at=dt(2017, 11, 1), retirement_kind="scrapped"),
    Component("tire_gp4k_r", "Tire", "Conti GP4000S 25mm rear", "Continental", "Grand Prix 4000S 25-622", dt(2010, 8, 6), "34.90", vend(),
              retired_at=dt(2017, 11, 1), retirement_kind="scrapped"),
    Component("dt_f", "Wheel", "DT Swiss front wheel", "DT Swiss", "R 1.1", dt(2006, 6, 15), "179.00", vend(), serial()),
    Component("dt_r", "Wheel", "DT Swiss rear wheel", "DT Swiss", "R 1.1", dt(2006, 6, 15), "219.00", vend(), serial()),
    Component("zonda_f", "Wheel", "Campa Zonda front wheel", "Campagnolo", "Zonda", dt(2008, 6, 7), "199.00", vend(), serial()),
    Component("zonda_r", "Wheel", "Campa Zonda rear wheel", "Campagnolo", "Zonda", dt(2008, 6, 7), "259.00", vend(), serial()),
    Component("saddle_si", "Saddle", "Selle Italia SLR", "Selle Italia", "SLR", dt(2006, 6, 15), "119.00", vend()),
    Component("hed_f", "Wheel", "TT front wheel", "HED", "H3 Tri-Spoke", dt(2010, 8, 15), "899.00", vend(), serial()),
    # --- B1 steel classic (1995, retired 2011) ---
    Component("mavic_f", "Wheel", "Mavic front wheel", "Mavic", "Open 4 CD", dt(1995, 4, 12), "129.00", vend(),
              retired_at=dt(2012, 5, 1), retirement_kind="sold"),
    Component("mavic_r", "Wheel", "Mavic rear wheel", "Mavic", "Open 4 CD", dt(1995, 4, 12), "149.00", vend(),
              retired_at=dt(2012, 5, 1), retirement_kind="sold"),
    Component("ultra2000_f", "Tire", "Conti Ultra 2000 front", "Continental", "Ultra 2000 23-622", dt(1995, 4, 12), "19.90", vend(),
              retired_at=dt(2001, 5, 1), retirement_kind="scrapped"),
    Component("ultra2000_r", "Tire", "Conti Ultra 2000 rear", "Continental", "Ultra 2000 23-622", dt(1995, 4, 12), "19.90", vend(),
              retired_at=dt(2001, 5, 1), retirement_kind="scrapped"),
    Component("gp3000_f", "Tire", "Conti GP 3000 front", "Continental", "Grand Prix 3000 23-622", dt(2001, 5, 1), "27.90", vend()),
    Component("gp3000_r", "Tire", "Conti GP 3000 rear", "Continental", "Grand Prix 3000 23-622", dt(2001, 5, 1), "27.90", vend()),
    Component("hg70", "Chain", "Shimano HG-70 chain", "Shimano", "CN-HG70", dt(1995, 4, 12), "14.90", vend(),
              retired_at=dt(2005, 6, 1), retirement_kind="scrapped"),
    Component("hg53", "Chain", "Shimano HG-53 chain", "Shimano", "CN-HG53", dt(2005, 6, 1), "12.90", vend()),
    Component("rolls", "Saddle", "San Marco Rolls", "Selle San Marco", "Rolls", dt(1995, 4, 12), "59.00", vend(),
              retired_at=dt(2012, 5, 1), retirement_kind="sold"),
    Component("cs600", "Cassette", "Shimano 600 cassette", "Shimano", "600 7s 13-24", dt(1995, 4, 12), "29.00", vend()),
    Component("rd600", "Rear Derailleur", "Shimano 600 rear derailleur", "Shimano", "RD-6400", dt(1995, 4, 12), "49.00", vend()),
    # --- B2 later additions ---
    Component("rubino_f", "Tire", "Vittoria Rubino Pro front", "Vittoria", "Rubino Pro 23-622", dt(2008, 6, 7), "24.90", vend(),
              retired_at=dt(2010, 8, 6), retirement_kind="scrapped"),
    Component("rubino_r", "Tire", "Vittoria Rubino Pro rear", "Vittoria", "Rubino Pro 23-622", dt(2008, 6, 7), "24.90", vend(),
              retired_at=dt(2010, 8, 6), retirement_kind="scrapped"),
    Component("gp4k2_f", "Tire", "Conti GP4000S II front", "Continental", "Grand Prix 4000S II 25-622", dt(2013, 4, 1), "36.90", vend(),
              retired_at=dt(2019, 3, 1), retirement_kind="scrapped"),
    Component("gp4k2_r", "Tire", "Conti GP4000S II rear", "Continental", "Grand Prix 4000S II 25-622", dt(2013, 4, 1), "36.90", vend(),
              retired_at=dt(2019, 3, 1), retirement_kind="scrapped"),
    Component("gp5k_f", "Tire", "Conti GP 5000 front", "Continental", "Grand Prix 5000 25-622", dt(2019, 3, 1), "44.90", vend()),
    Component("gp5k_r", "Tire", "Conti GP 5000 rear", "Continental", "Grand Prix 5000 25-622", dt(2019, 3, 1), "44.90", vend()),
    Component("durano_f", "Tire", "Durano Plus winter front", "Schwalbe", "Durano Plus 25-622", dt(2017, 11, 1), "31.90", vend()),
    Component("durano_r", "Tire", "Durano Plus winter rear", "Schwalbe", "Durano Plus 25-622", dt(2017, 11, 1), "31.90", vend()),
    Component("cn10a", "Chain", "Record chain #2", "Campagnolo", "Record C10", dt(2010, 6, 1), "32.90", vend(),
              retired_at=dt(2014, 5, 1), retirement_kind="scrapped"),
    Component("cn10b", "Chain", "Record chain #3", "Campagnolo", "Record C10", dt(2014, 5, 1), "34.90", vend(),
              retired_at=dt(2018, 4, 1), retirement_kind="scrapped"),
    Component("cn10c", "Chain", "Record chain #4", "Campagnolo", "Record C10", dt(2018, 4, 1), "36.90", vend()),
    Component("cassette2", "Cassette", "Veloce cassette 13-26 (winter)", "Campagnolo", "Veloce UD 10s 13-26", dt(2010, 11, 1), "49.90", vend()),
    Component("cassette3", "Cassette", "Chorus cassette 12-27", "Campagnolo", "Chorus 10s 12-27", dt(2016, 6, 1), "89.00", vend()),
    Component("fizik", "Saddle", "Fizik Arione", "Fizik", "Arione", dt(2011, 8, 10), "99.00", vend()),
    Component("deda", "Handlebar", "Deda handlebar", "Deda", "Zero 100", dt(2006, 6, 15), "69.00", vend()),
    Component("keo", "Pedals", "Look Keo pedals", "Look", "Keo Classic", dt(2006, 6, 15), "49.00", vend()),
    # --- B3 TT ---
    Component("zipp_r", "Wheel", "Zipp disc rear wheel", "Zipp", "900 Disc", dt(2011, 5, 1), "1499.00", vend(), serial()),
    Component("comp_f", "Tire", "Conti Competition front", "Continental", "Competition 22-622", dt(2010, 8, 15), "39.90", vend(),
              retired_at=dt(2016, 5, 1), retirement_kind="scrapped"),
    Component("comp_r", "Tire", "Conti Competition rear", "Continental", "Competition 22-622", dt(2011, 5, 1), "39.90", vend(),
              retired_at=dt(2016, 5, 1), retirement_kind="scrapped"),
    Component("podium_f", "Tire", "Conti Podium TT front", "Continental", "Podium TT 22-622", dt(2016, 5, 1), "49.90", vend()),
    Component("podium_r", "Tire", "Conti Podium TT rear", "Continental", "Podium TT 22-622", dt(2016, 5, 1), "49.90", vend()),
    Component("cs6700", "Cassette", "Ultegra cassette 11-25", "Shimano", "CS-6700 11-25", dt(2011, 5, 1), "59.90", vend(),
              retired_at=dt(2017, 5, 1), retirement_kind="scrapped"),
    Component("csr8000", "Cassette", "Ultegra cassette 11-28", "Shimano", "CS-R8000 11-28", dt(2017, 5, 1), "64.90", vend()),
    Component("cn6701", "Chain", "Ultegra chain", "Shimano", "CN-6701", dt(2010, 8, 15), "24.90", vend(),
              retired_at=dt(2016, 5, 1), retirement_kind="scrapped"),
    Component("cnhg701", "Chain", "Ultegra chain #2", "Shimano", "CN-HG701", dt(2016, 5, 1), "27.90", vend()),
    Component("rd6700", "Rear Derailleur", "Ultegra rear derailleur", "Shimano", "RD-6700", dt(2010, 8, 15), "89.00", vend(), serial()),
    Component("br6700_f", "Rim Brake", "Ultegra brake front", "Shimano", "BR-6700", dt(2010, 8, 15), "59.00", vend()),
    Component("br6700_r", "Rim Brake", "Ultegra brake rear", "Shimano", "BR-6700", dt(2010, 8, 15), "59.00", vend()),
    Component("aerobar", "Handlebar", "Profile Design aerobar", "Profile Design", "T2 Plus", dt(2010, 8, 15), "159.00", vend()),
    Component("saddle_stock", "Saddle", "Selle Royal stock saddle", "Selle Royal", "Seta", dt(2010, 8, 15), "39.00", vend(),
              retired_at=dt(2011, 9, 1), retirement_kind="sold"),
    # --- B4 gravel ---
    Component("dtdb_f", "Wheel", "DT Swiss db front wheel", "DT Swiss", "R 23 Spline db", dt(2016, 4, 2), "249.00", vend(), serial()),
    Component("dtdb_r", "Wheel", "DT Swiss db rear wheel", "DT Swiss", "R 23 Spline db", dt(2016, 4, 2), "299.00", vend(), serial()),
    Component("goa_f", "Tire", "G-One Allround front", "Schwalbe", "G-One Allround 38-622", dt(2016, 4, 2), "39.90", vend(),
              retired_at=dt(2019, 6, 1), retirement_kind="scrapped"),
    Component("goa_r", "Tire", "G-One Allround rear", "Schwalbe", "G-One Allround 38-622", dt(2016, 4, 2), "39.90", vend(),
              retired_at=dt(2019, 6, 1), retirement_kind="scrapped"),
    Component("gos_f", "Tire", "G-One Speed front", "Schwalbe", "G-One Speed 38-622", dt(2019, 6, 1), "42.90", vend()),
    Component("gos_r", "Tire", "G-One Speed rear", "Schwalbe", "G-One Speed 38-622", dt(2019, 6, 1), "42.90", vend()),
    Component("rt70_f", "Brake Disc", "Front brake rotor 160mm", "Shimano", "SM-RT70 160", dt(2016, 4, 2), "19.90", vend()),
    Component("rt70_r", "Brake Disc", "Rear brake rotor 160mm", "Shimano", "SM-RT70 160", dt(2016, 4, 2), "19.90", vend()),
    Component("cs5800", "Cassette", "105 cassette 11-32", "Shimano", "CS-5800 11-32", dt(2016, 4, 2), "39.90", vend()),
    Component("hg601a", "Chain", "105 chain", "Shimano", "CN-HG601", dt(2016, 4, 2), "22.90", vend(),
              retired_at=dt(2018, 9, 1), retirement_kind="scrapped"),
    Component("hg601b", "Chain", "105 chain #2", "Shimano", "CN-HG601", dt(2018, 9, 1), "22.90", vend()),
    Component("fd5800", "Front Derailleur", "105 front derailleur", "Shimano", "FD-5800", dt(2016, 4, 2), "34.90", vend()),
    Component("rd5800", "Rear Derailleur", "105 rear derailleur", "Shimano", "RD-5800 GS", dt(2016, 4, 2), "49.90", vend(), serial()),
    Component("rs505_f", "Disc Brake", "Hydraulic caliper front", "Shimano", "BR-RS505", dt(2016, 4, 2), "89.00", vend()),
    Component("rs505_r", "Disc Brake", "Hydraulic caliper rear", "Shimano", "BR-RS505", dt(2016, 4, 2), "89.00", vend()),
    Component("phenom", "Saddle", "Specialized Phenom", "Specialized", "Phenom Comp", dt(2016, 4, 2), "89.00", vend()),
    Component("pdm8000", "Pedals", "XT pedals", "Shimano", "PD-M8000", dt(2016, 4, 2), "79.00", vend()),
]
comp_by_key = {c.key: c for c in COMPONENTS}

# --------------------------------------------------------------------------
# Assemblies: 4 wheelsets = 8 wheel assemblies
# --------------------------------------------------------------------------
@dataclass
class Slot:
    key: str
    assembly_key: str
    typ: str
    name: str
    valid_from: datetime
    valid_to: datetime | None = None
    id: str = ""

    def __post_init__(self):
        self.id = uid(f"slot:{self.key}")


@dataclass
class Assembly:
    key: str
    name: str
    position: str  # Front | Rear
    id: str = ""

    def __post_init__(self):
        self.id = uid(f"assembly:{self.key}")


ASSEMBLIES = [
    Assembly("ws1f", "DT Swiss wheel front", "Front"),
    Assembly("ws1r", "DT Swiss wheel rear", "Rear"),
    Assembly("ws2f", "Zonda wheel front", "Front"),
    Assembly("ws2r", "Zonda wheel rear", "Rear"),
    Assembly("ws3f", "TT wheel front", "Front"),
    Assembly("ws3r", "TT disc wheel rear", "Rear"),
    Assembly("ws4f", "Gravel wheel front", "Front"),
    Assembly("ws4r", "Gravel wheel rear", "Rear"),
]
asm_by_key = {a.key: a for a in ASSEMBLIES}

SLOTS = [
    Slot("ws1f:wheel", "ws1f", "Wheel", "Wheel", dt(2006, 6, 15)),
    Slot("ws1f:tire", "ws1f", "Tire", "Tire", dt(2006, 6, 15)),
    Slot("ws1r:wheel", "ws1r", "Wheel", "Wheel", dt(2006, 6, 15)),
    Slot("ws1r:tire", "ws1r", "Tire", "Tire", dt(2006, 6, 15)),
    # cassette moved to the Zonda wheel in 2010; slot closed until a winter
    # cassette was bought — exercises slot valid_from/valid_to
    Slot("ws1r:cassette", "ws1r", "Cassette", "Cassette", dt(2006, 6, 15), dt(2010, 6, 7)),
    Slot("ws1r:cassette2", "ws1r", "Cassette", "Cassette", dt(2010, 11, 1)),
    Slot("ws2f:wheel", "ws2f", "Wheel", "Wheel", dt(2008, 6, 7)),
    Slot("ws2f:tire", "ws2f", "Tire", "Tire", dt(2008, 6, 7)),
    Slot("ws2r:wheel", "ws2r", "Wheel", "Wheel", dt(2008, 6, 7)),
    Slot("ws2r:tire", "ws2r", "Tire", "Tire", dt(2008, 6, 7)),
    Slot("ws2r:cassette", "ws2r", "Cassette", "Cassette", dt(2010, 6, 7)),
    Slot("ws3f:wheel", "ws3f", "Wheel", "Wheel", dt(2010, 8, 15)),
    Slot("ws3f:tire", "ws3f", "Tire", "Tire", dt(2010, 8, 15)),
    Slot("ws3r:wheel", "ws3r", "Wheel", "Wheel", dt(2011, 5, 1)),
    Slot("ws3r:tire", "ws3r", "Tire", "Tire", dt(2011, 5, 1)),
    Slot("ws3r:cassette", "ws3r", "Cassette", "Cassette", dt(2011, 5, 1)),
    Slot("ws4f:wheel", "ws4f", "Wheel", "Wheel", dt(2016, 4, 2)),
    Slot("ws4f:tire", "ws4f", "Tire", "Tire", dt(2016, 4, 2)),
    Slot("ws4f:disc", "ws4f", "Brake Disc", "Brake Disc", dt(2016, 4, 2)),
    Slot("ws4r:wheel", "ws4r", "Wheel", "Wheel", dt(2016, 4, 2)),
    Slot("ws4r:tire", "ws4r", "Tire", "Tire", dt(2016, 4, 2)),
    Slot("ws4r:disc", "ws4r", "Brake Disc", "Brake Disc", dt(2016, 4, 2)),
    Slot("ws4r:cassette", "ws4r", "Cassette", "Cassette", dt(2016, 4, 2)),
]
slot_by_key = {s.key: s for s in SLOTS}

# membership: (slot key, component key, from, to)
MEMBERSHIPS: list[tuple[str, str, datetime, datetime | None]] = [
    ("ws1f:wheel", "dt_f", dt(2006, 6, 15), None),
    ("ws1f:tire", "tire_stelvio_f", dt(2006, 6, 15), dt(2013, 4, 1)),
    ("ws1f:tire", "tire_gp4k_f", dt(2013, 4, 1), dt(2017, 11, 1)),   # cross-mounted from ws2f
    ("ws1f:tire", "durano_f", dt(2017, 11, 1), None),
    ("ws1r:wheel", "dt_r", dt(2006, 6, 15), None),
    ("ws1r:tire", "tire_stelvio_r", dt(2006, 6, 15), dt(2013, 4, 1)),
    ("ws1r:tire", "tire_gp4k_r", dt(2013, 4, 1), dt(2017, 11, 1)),   # cross-mounted from ws2r
    ("ws1r:tire", "durano_r", dt(2017, 11, 1), None),
    ("ws1r:cassette", "cassette1", dt(2006, 6, 15), dt(2010, 6, 7)),
    ("ws1r:cassette2", "cassette2", dt(2010, 11, 1), None),
    ("ws2f:wheel", "zonda_f", dt(2008, 6, 7), None),
    ("ws2f:tire", "rubino_f", dt(2008, 6, 7), dt(2010, 8, 6)),
    ("ws2f:tire", "tire_gp4k_f", dt(2010, 8, 6), dt(2013, 4, 1)),
    ("ws2f:tire", "gp4k2_f", dt(2013, 4, 1), dt(2019, 3, 1)),
    ("ws2f:tire", "gp5k_f", dt(2019, 3, 1), None),
    ("ws2r:wheel", "zonda_r", dt(2008, 6, 7), None),
    ("ws2r:tire", "rubino_r", dt(2008, 6, 7), dt(2010, 8, 6)),
    ("ws2r:tire", "tire_gp4k_r", dt(2010, 8, 6), dt(2013, 4, 1)),
    ("ws2r:tire", "gp4k2_r", dt(2013, 4, 1), dt(2019, 3, 1)),
    ("ws2r:tire", "gp5k_r", dt(2019, 3, 1), None),
    ("ws2r:cassette", "cassette1", dt(2010, 6, 7), dt(2016, 6, 1)),  # cross-mounted from ws1r
    ("ws2r:cassette", "cassette3", dt(2016, 6, 1), None),
    ("ws3f:wheel", "hed_f", dt(2010, 8, 15), None),
    ("ws3f:tire", "comp_f", dt(2010, 8, 15), dt(2016, 5, 1)),
    ("ws3f:tire", "podium_f", dt(2016, 5, 1), None),
    ("ws3r:wheel", "zipp_r", dt(2011, 5, 1), None),
    ("ws3r:tire", "comp_r", dt(2011, 5, 1), dt(2016, 5, 1)),
    ("ws3r:tire", "podium_r", dt(2016, 5, 1), None),
    ("ws3r:cassette", "cs6700", dt(2011, 5, 1), dt(2017, 5, 1)),
    ("ws3r:cassette", "csr8000", dt(2017, 5, 1), None),
    ("ws4f:wheel", "dtdb_f", dt(2016, 4, 2), None),
    ("ws4f:tire", "goa_f", dt(2016, 4, 2), dt(2019, 6, 1)),
    ("ws4f:tire", "gos_f", dt(2019, 6, 1), None),
    ("ws4f:disc", "rt70_f", dt(2016, 4, 2), None),
    ("ws4r:wheel", "dtdb_r", dt(2016, 4, 2), None),
    ("ws4r:tire", "goa_r", dt(2016, 4, 2), dt(2019, 6, 1)),
    ("ws4r:tire", "gos_r", dt(2019, 6, 1), None),
    ("ws4r:disc", "rt70_r", dt(2016, 4, 2), None),
    ("ws4r:cassette", "cs5800", dt(2016, 4, 2), None),
]

# assembly mounting periods: (assembly key, bike key, from, to)
def _summer_winter_periods() -> tuple[list, list]:
    """B2 alternates: Zonda in summer, DT Swiss in winter (legacy alternation kept)."""
    ws1 = [
        ("b2", dt(2006, 6, 15), dt(2008, 6, 7)),
        ("b2", dt(2008, 10, 11), dt(2009, 6, 1)),
        ("b2", dt(2009, 10, 11), dt(2010, 6, 7)),
        ("b1", dt(2010, 7, 1), dt(2010, 9, 1)),  # loaner while B1's wheels were in repair
    ]
    ws2 = [
        ("b2", dt(2008, 6, 7), dt(2008, 10, 11)),
        ("b2", dt(2009, 6, 1), dt(2009, 10, 11)),
        ("b2", dt(2010, 6, 7), dt(2012, 11, 1)),
    ]
    for y in range(2012, 2019):
        ws1.append(("b2", dt(y, 11, 1), dt(y + 1, 4, 1)))
    for y in range(2013, 2019):
        ws2.append(("b2", dt(y, 4, 1), dt(y, 11, 1)))
    ws2.append(("b2", dt(2019, 4, 1), None))
    return ws1, ws2


_ws1, _ws2 = _summer_winter_periods()
ASSEMBLY_PERIODS: list[tuple[str, str, datetime, datetime | None]] = []
for asm_key, periods in (("ws1f", _ws1), ("ws1r", _ws1), ("ws2f", _ws2), ("ws2r", _ws2)):
    for bike_key, start, end in periods:
        ASSEMBLY_PERIODS.append((asm_key, bike_key, start, end))
ASSEMBLY_PERIODS.append(("ws3f", "b3", dt(2010, 8, 15), dt(2010, 10, 15)))
for y in range(2011, 2020):
    ASSEMBLY_PERIODS.append(("ws3f", "b3", dt(y, 5, 1), dt(y, 9, 30)))
    ASSEMBLY_PERIODS.append(("ws3r", "b3", dt(y, 5, 1), dt(y, 9, 30)))
ASSEMBLY_PERIODS.append(("ws4f", "b4", dt(2016, 4, 2), None))
ASSEMBLY_PERIODS.append(("ws4r", "b4", dt(2016, 4, 2), None))

# --------------------------------------------------------------------------
# Direct mountings: (component key, bike key, mount point name, from, to)
# --------------------------------------------------------------------------
B1_RET = bike_by_key["b1"].retired_at
DIRECT_MOUNTINGS: list[tuple[str, str, str, datetime, datetime | None]] = [
    # B1 steel classic; wheels dismounted Jul-Sep 2010 while the DT Swiss set was on loan
    ("mavic_f", "b1", "Front Wheel", dt(1995, 4, 12), dt(2010, 7, 1)),
    ("mavic_f", "b1", "Front Wheel", dt(2010, 9, 1), B1_RET),
    ("mavic_r", "b1", "Rear Wheel", dt(1995, 4, 12), dt(2010, 7, 1)),
    ("mavic_r", "b1", "Rear Wheel", dt(2010, 9, 1), B1_RET),
    ("ultra2000_f", "b1", "Front Tire", dt(1995, 4, 12), dt(2001, 5, 1)),
    ("ultra2000_r", "b1", "Rear Tire", dt(1995, 4, 12), dt(2001, 5, 1)),
    ("gp3000_f", "b1", "Front Tire", dt(2001, 5, 1), dt(2010, 7, 1)),
    ("gp3000_f", "b1", "Front Tire", dt(2010, 9, 1), B1_RET),
    ("gp3000_r", "b1", "Rear Tire", dt(2001, 5, 1), dt(2010, 7, 1)),
    ("gp3000_r", "b1", "Rear Tire", dt(2010, 9, 1), B1_RET),
    ("hg70", "b1", "Chain", dt(1995, 4, 12), dt(2005, 6, 1)),
    ("hg53", "b1", "Chain", dt(2005, 6, 1), B1_RET),
    ("rolls", "b1", "Saddle", dt(1995, 4, 12), B1_RET),
    ("cs600", "b1", "Cassette", dt(1995, 4, 12), B1_RET),
    ("rd600", "b1", "Rear Derailleur", dt(1995, 4, 12), B1_RET),
    # B2 Vaust Fragola
    ("brake_f", "b2", "Front Rim Brake", dt(2006, 6, 15), None),
    ("brake_r", "b2", "Rear Rim Brake", dt(2006, 6, 15), None),
    ("fd_chorus", "b2", "Front Derailleur", dt(2006, 6, 15), None),
    ("rd_veloce", "b2", "Rear Derailleur", dt(2006, 6, 15), None),
    ("chain1", "b2", "Chain", dt(2006, 6, 15), dt(2010, 6, 1)),
    ("cn10a", "b2", "Chain", dt(2010, 6, 1), dt(2014, 5, 1)),
    ("cn10b", "b2", "Chain", dt(2014, 5, 1), dt(2018, 4, 1)),
    ("cn10c", "b2", "Chain", dt(2018, 4, 1), None),
    ("saddle_si", "b2", "Saddle", dt(2006, 6, 15), dt(2011, 8, 10)),  # moved to the TT bike
    ("fizik", "b2", "Saddle", dt(2011, 8, 10), None),
    ("deda", "b2", "Handlebar", dt(2006, 6, 15), None),
    ("keo", "b2", "Pedals", dt(2006, 6, 15), None),
    # B3 TT
    ("cn6701", "b3", "Chain", dt(2010, 8, 15), dt(2016, 5, 1)),
    ("cnhg701", "b3", "Chain", dt(2016, 5, 1), None),
    ("rd6700", "b3", "Rear Derailleur", dt(2010, 8, 15), None),
    ("br6700_f", "b3", "Front Rim Brake", dt(2010, 8, 15), None),
    ("br6700_r", "b3", "Rear Rim Brake", dt(2010, 8, 15), None),
    ("saddle_stock", "b3", "Saddle", dt(2010, 8, 15), dt(2011, 8, 10)),
    ("saddle_si", "b3", "Saddle", dt(2011, 8, 10), None),  # cross-bike move from B2
    ("aerobar", "b3", "Handlebar", dt(2010, 8, 15), None),
    # B4 gravel
    ("hg601a", "b4", "Chain", dt(2016, 4, 2), dt(2018, 9, 1)),
    ("hg601b", "b4", "Chain", dt(2018, 9, 1), None),
    ("fd5800", "b4", "Front Derailleur", dt(2016, 4, 2), None),
    ("rd5800", "b4", "Rear Derailleur", dt(2016, 4, 2), None),
    ("rs505_f", "b4", "Front Disc Brake", dt(2016, 4, 2), None),
    ("rs505_r", "b4", "Rear Disc Brake", dt(2016, 4, 2), None),
    ("phenom", "b4", "Saddle", dt(2016, 4, 2), None),
    ("pdm8000", "b4", "Pedals", dt(2016, 4, 2), None),
]

# --------------------------------------------------------------------------
# Derived rows: assembly mountings, governed mountings, slot mappings
# --------------------------------------------------------------------------
@dataclass
class Mounting:
    component: str  # component key
    bike: str
    mp_id: str
    start: datetime
    end: datetime | None
    assembly_mounting_id: str | None = None
    id: str = field(default="")

    def __post_init__(self):
        if not self.id:
            self.id = uid(f"mounting:{self.component}:{self.mp_id}:{self.start.isoformat()}")


mp_name_lookup = {(bk, name): mp for mp, bk, name, _t, _p, _m in mount_points}
mountings: list[Mounting] = [
    Mounting(c, b, mp_name_lookup[(b, mp)], s, e) for c, b, mp, s, e in DIRECT_MOUNTINGS
]

assembly_mountings = []  # (id, assembly key, bike key, from, to)
slot_mappings: dict[tuple[str, str], str] = {}  # (slot key, bike key) -> mp id

for asm_key, bike_key, p_start, p_end in ASSEMBLY_PERIODS:
    am_id = uid(f"am:{asm_key}:{bike_key}:{p_start.isoformat()}")
    assembly_mountings.append((am_id, asm_key, bike_key, p_start, p_end))
    asm = asm_by_key[asm_key]
    for slot in [s for s in SLOTS if s.assembly_key == asm_key]:
        for s_key, c_key, m_from, m_to in MEMBERSHIPS:
            if s_key != slot.key:
                continue
            g_start = max(m_from, p_start)
            g_end = m_to if p_end is None else (p_end if m_to is None else min(m_to, p_end))
            if g_end is not None and g_start >= g_end:
                continue
            mp = resolve_mp(bike_key, slot.typ, asm.position)
            mountings.append(Mounting(c_key, bike_key, mp, g_start, g_end, assembly_mounting_id=am_id))
            slot_mappings.setdefault((slot.key, bike_key), mp)

# --------------------------------------------------------------------------
# Validation: replicate the DB's EXCLUDE / partial-unique invariants
# --------------------------------------------------------------------------
def assert_no_overlap(kind: str, intervals: dict[str, list[tuple[datetime, datetime | None]]]):
    far = dt(2100, 1, 1)
    for owner, ivs in intervals.items():
        ivs = sorted(ivs, key=lambda t: t[0])
        opens = [iv for iv in ivs if iv[1] is None]
        assert len(opens) <= 1, f"{kind} {owner}: {len(opens)} open intervals"
        for (s1, e1), (s2, _e2) in zip(ivs, ivs[1:]):
            assert (e1 or far) <= s2, f"{kind} {owner}: [{s1},{e1}) overlaps [{s2},...)"


def validate():
    by_comp, by_mp, by_slot_m, by_comp_m, by_asm = {}, {}, {}, {}, {}
    for m in mountings:
        by_comp.setdefault(m.component, []).append((m.start, m.end))
        by_mp.setdefault(m.mp_id, []).append((m.start, m.end))
    for s_key, c_key, m_from, m_to in MEMBERSHIPS:
        by_slot_m.setdefault(s_key, []).append((m_from, m_to))
        by_comp_m.setdefault(c_key, []).append((m_from, m_to))
        slot = slot_by_key[s_key]
        assert m_from >= slot.valid_from, f"membership {c_key} before slot {s_key} valid_from"
        if slot.valid_to is not None:
            assert m_to is not None and m_to <= slot.valid_to, \
                f"membership {c_key} exceeds slot {s_key} valid_to"
    for am_id, asm_key, _bike, s, e in assembly_mountings:
        by_asm.setdefault(asm_key, []).append((s, e))
    assert_no_overlap("component mounting", by_comp)
    assert_no_overlap("mount point", by_mp)
    assert_no_overlap("slot membership", by_slot_m)
    assert_no_overlap("component membership", by_comp_m)
    assert_no_overlap("assembly mounting", by_asm)
    # retired components / bikes must have no open or later intervals
    for c in COMPONENTS:
        if c.retired_at is None:
            continue
        for s, e in by_comp.get(c.key, []) + by_comp_m.get(c.key, []):
            assert e is not None and e <= c.retired_at, \
                f"retired component {c.key} has interval open past retirement"
    for m in mountings:
        b = bike_by_key[m.bike]
        if b.retired_at is not None:
            assert m.end is not None and m.end <= b.retired_at, \
                f"mounting on retired bike {m.bike} still open ({m.component})"


# --------------------------------------------------------------------------
# Tours: ~20 per bike per active year, 2010-2020, European route names
# --------------------------------------------------------------------------
CLIMB_ROUTES = [
    "Passo Giau", "Passo dello Stelvio", "Col du Tourmalet", "Mont Ventoux",
    "Alpe d'Huez", "Col du Galibier", "Passo del Mortirolo", "Grossglockner Hochalpenstrasse",
    "Timmelsjoch", "Sa Calobra", "Transfagarasan", "Kitzbuheler Horn",
    "Sella Ronda", "Col d'Izoard", "Passo Gavia", "Monte Zoncolan",
]
ROLLING_ROUTES = [
    "Kaiserstuhl Loop", "Lago di Garda Loop", "Bodensee Circuit", "Tegernsee Loop",
    "Chiemgau Rounds", "Ballon d'Alsace", "Schwarzwald Feldberg", "Harz Brocken Round",
    "Mallorca Orient Valley", "Danube Valley Stage", "Elbe Valley Spin", "Taunus Classic",
]
TT_ROUTES = [
    "Kraichgau TT", "Roth Challenge TT", "Frankfurt City TT", "Club Evening TT",
    "Heidelberg Airfield TT", "Rhine Flats TT", "Kaiserslautern Duathlon TT",
]
GRAVEL_ROUTES = [
    "Taunus Gravel", "Odenwald Gravel Tour", "Strade Bianche Gran Fondo",
    "Eifel Gravel Loop", "Baltic Coast Gravel", "Flanders Farm Tracks",
    "Vosges Forest Gravel", "Black Forest Crossing",
]

# profile: (routes, split of climb routes, speed km/h, dist km range, ascent m/km range, watts)
BIKE_TOUR_PROFILE = {
    "b1": (ROLLING_ROUTES + CLIMB_ROUTES[:6], 25.0, (25, 100), (4, 16), (150, 210)),
    "b2": (CLIMB_ROUTES + ROLLING_ROUTES, 27.0, (30, 160), (5, 25), (170, 250)),
    "b3": (TT_ROUTES, 36.0, (18, 55), (1, 5), (220, 300)),
    "b4": (GRAVEL_ROUTES, 21.0, (25, 95), (6, 20), (150, 220)),
}
# active tour years and allowed months per bike
BIKE_TOUR_YEARS = {
    "b1": {2010: range(1, 13), 2011: range(1, 10)},
    "b2": {y: range(1, 13) for y in range(2010, 2021)},
    "b3": {y: range(5, 10) for y in range(2011, 2021)},
    "b4": {**{2016: range(4, 13)}, **{y: range(3, 12) for y in range(2017, 2021)}},
}

LEGACY_TOURS = [
    # (id, bike, title, started_at, distance m, moving s, ascent m, descent m, power kJ)
    (LEG_TOUR_GIAU, "b2", "Passo Giau", dt(2010, 6, 7, 10, 22, 46), 53120, 7614, 1520, 1520, 5043),
    (LEG_TOUR_TOURMALET, "b2", "Col du Tourmalet", dt(2010, 6, 11, 9, 50, 8), 43160, 5534, 1268, 1268, 4310),
]


def gen_tours():
    tours = list(LEGACY_TOURS)
    for bike_key, years in BIKE_TOUR_YEARS.items():
        routes, speed, dist_range, ascent_range, watts = BIKE_TOUR_PROFILE[bike_key]
        for year, months in years.items():
            legacy_count = sum(1 for _t, bk, _ti, st, *_ in LEGACY_TOURS if bk == bike_key and st.year == year)
            target = 20 - legacy_count
            days = set()
            while len(days) < target:
                m = rng.choice(list(months))
                d = rng.randint(1, 28)
                days.add((m, d))
            for i, (m, d) in enumerate(sorted(days)):
                started = dt(year, m, d, rng.randint(7, 16), rng.randint(0, 59), rng.randint(0, 59))
                dist_km = rng.uniform(*dist_range)
                speed_kmh = speed * rng.uniform(0.85, 1.15)
                distance = int(dist_km * 1000)
                moving = int(dist_km / speed_kmh * 3600)
                ascent = int(dist_km * rng.uniform(*ascent_range))
                # mostly loop rides: descent close to, but not exactly, the ascent
                descent = int(ascent * rng.uniform(0.92, 1.03))
                power = int(moving * rng.uniform(*watts) / 1000)
                title = rng.choice(routes)
                tid = uid(f"tour:{bike_key}:{year}:{m:02d}:{d:02d}:{i}")
                tours.append((tid, bike_key, title, started, distance, moving, ascent, descent, power))
    return tours


# --------------------------------------------------------------------------
# Maintenance
# --------------------------------------------------------------------------
MAINT_TASKS = [
    # (key, bike, name, distance m, time s, event dates)
    ("b2:lube", "b2", "Wax chain", 300_000, None,
     [dt(y, m, 5) for y in range(2018, 2021) for m in (2, 5, 8, 11)]),
    ("b2:service", "b2", "Annual service", None, 31_536_000,
     [dt(y, 11, 15) for y in range(2014, 2020)]),
    ("b3:clean", "b3", "Drivetrain deep clean", None, 15_768_000,
     [d for y in range(2016, 2020) for d in (dt(y, 5, 10), dt(y, 8, 10))]),
    ("b4:pads", "b4", "Brake pad check", 5_000_000, None,
     [dt(2017, 6, 1), dt(2018, 7, 1), dt(2019, 8, 1)]),
    ("b4:wax", "b4", "Wax chain", 400_000, None,
     [dt(y, m, 20) for y in (2019, 2020) for m in (3, 6, 9)]),
]


# --------------------------------------------------------------------------
# SQL emission
# --------------------------------------------------------------------------
TRUNCATE_TABLES = (
    "mounting, assembly_membership, slot_mapping, assembly_mounting, assembly_slot, "
    "component_assembly, maintenance_event, maintenance_task, tour, component, "
    "mount_point, bike, component_type, position, import_session, import_state, import_ignore"
)


def emit() -> str:
    out = []
    w = out.append
    w("-- Generated by tools/demo-data/generate_demo_data.py (CE-0043). DO NOT EDIT.")
    w("-- Demo dataset for the demo profile; refreshed on every application start.")
    w("")
    w(f"TRUNCATE {TRUNCATE_TABLES} CASCADE;")
    w("")
    w("INSERT INTO position (id, name) VALUES")
    w(",\n".join(f"      ('{pos_id[p]}', {sql_str(p)})" for p in POSITIONS) + ";")
    w("")
    w("INSERT INTO component_type (id, name) VALUES")
    w(",\n".join(f"      ('{type_id[t]}', {sql_str(t)})" for t in TYPES) + ";")
    w("")
    w("INSERT INTO bike (id, name, manufacturer, model, purchase_date, price, price_currency, retired_at) VALUES")
    w(",\n".join(
        f"      ('{b.id}', {sql_str(b.name)}, {sql_str(b.manufacturer)}, {sql_str(b.model)}, "
        f"{sql_date(b.purchase)}, {sql_str(b.price)}, '{b.currency}', {sql_ts(b.retired_at)})"
        for b in BIKES) + ";")
    w("")
    w("INSERT INTO mount_point (id, bike_id, component_type_id, position_id, name, mandatory) VALUES")
    w(",\n".join(
        f"      ('{mp}', '{bike_by_key[bk].id}', '{type_id[typ]}', "
        f"{sql_id(pos_id[pos] if pos is not None else None)}, {sql_str(name)}, {str(mand).lower()})"
        for mp, bk, name, typ, pos, mand in mount_points) + ";")
    w("")
    w("INSERT INTO component (id, component_type_id, label, manufacturer, model, serial_number, vendor, purchase_date, price, price_currency, retired_at, retirement_kind) VALUES")
    w(",\n".join(
        f"      ('{c.id}', '{type_id[c.typ]}', {sql_str(c.label)}, {sql_str(c.manufacturer)}, "
        f"{sql_str(c.model)}, {sql_str(c.serial)}, {sql_str(c.vendor)}, {sql_date(c.purchase)}, "
        f"{sql_str(c.price)}, {sql_id('EUR' if c.price is not None else None)}, "
        f"{sql_ts(c.retired_at)}, {sql_str(c.retirement_kind)})"
        for c in COMPONENTS) + ";")
    w("")
    w("INSERT INTO component_assembly (id, position_id, name) VALUES")
    w(",\n".join(
        f"      ('{a.id}', '{pos_id[a.position]}', {sql_str(a.name)})" for a in ASSEMBLIES) + ";")
    w("")
    w("INSERT INTO assembly_slot (id, assembly_id, component_type_id, name, valid_from, valid_to) VALUES")
    w(",\n".join(
        f"      ('{s.id}', '{asm_by_key[s.assembly_key].id}', '{type_id[s.typ]}', {sql_str(s.name)}, "
        f"{sql_ts(s.valid_from)}, {sql_ts(s.valid_to)})" for s in SLOTS) + ";")
    w("")
    w("INSERT INTO assembly_membership (id, component_id, assembly_slot_id, member_from, member_to) VALUES")
    w(",\n".join(
        f"      ('{uid(f'membership:{s}:{c}:{f.isoformat()}')}', '{comp_by_key[c].id}', "
        f"'{slot_by_key[s].id}', {sql_ts(f)}, {sql_ts(t)})"
        for s, c, f, t in MEMBERSHIPS) + ";")
    w("")
    w("INSERT INTO assembly_mounting (id, assembly_id, bike_id, mounted_at, dismounted_at) VALUES")
    w(",\n".join(
        f"      ('{am_id}', '{asm_by_key[a].id}', '{bike_by_key[b].id}', {sql_ts(s)}, {sql_ts(e)})"
        for am_id, a, b, s, e in assembly_mountings) + ";")
    w("")
    w("INSERT INTO mounting (id, component_id, mount_point_id, assembly_mounting_id, mounted_at, dismounted_at) VALUES")
    w(",\n".join(
        f"      ('{m.id}', '{comp_by_key[m.component].id}', '{m.mp_id}', "
        f"{sql_id(m.assembly_mounting_id)}, "
        f"{sql_ts(m.start)}, {sql_ts(m.end)})" for m in mountings) + ";")
    w("")
    w("INSERT INTO slot_mapping (id, assembly_slot_id, bike_id, mount_point_id) VALUES")
    w(",\n".join(
        f"      ('{uid(f'slotmap:{s}:{b}')}', '{slot_by_key[s].id}', '{bike_by_key[b].id}', '{mp}')"
        for (s, b), mp in sorted(slot_mappings.items())) + ";")
    w("")
    tours = gen_tours()
    w("INSERT INTO tour (id, bike_id, title, source, started_at, start_year, start_month, start_day, duration_moving, duration_recorded, duration_elapsed, distance, ascent, descent, power_total) VALUES")
    rows = []
    for tid, bike_key, title, started, distance, moving, ascent, descent, power in tours:
        recorded = int(moving * 1.03)
        elapsed = int(moving * 1.15)
        rows.append(
            f"      ('{tid}', '{bike_by_key[bike_key].id}', {sql_str(title)}, 'MANUAL', {sql_ts(started)}, "
            f"{started.year}, {started.month}, {started.day}, {moving}, {recorded}, {elapsed}, "
            f"{distance}, {ascent}, {descent}, {power})")
    w(",\n".join(rows) + ";")
    w("")
    w("INSERT INTO maintenance_task (id, bike_id, name, distance_interval, time_interval) VALUES")
    w(",\n".join(
        f"      ('{uid(f'task:{key}')}', '{bike_by_key[b].id}', {sql_str(name)}, "
        f"{dist if dist is not None else 'null'}, {time if time is not None else 'null'})"
        for key, b, name, dist, time, _events in MAINT_TASKS) + ";")
    w("")
    w("INSERT INTO maintenance_event (id, maintenance_task_id, performed_at) VALUES")
    ev_rows = []
    for key, _b, _name, _d, _t, events in MAINT_TASKS:
        for e in events:
            ev_rows.append(f"      ('{uid(f'event:{key}:{e.isoformat()}')}', '{uid(f'task:{key}')}', {sql_ts(e)})")
    w(",\n".join(ev_rows) + ";")
    w("")
    return "\n".join(out)


def main():
    validate()
    sql = emit()
    OUT.write_text(sql, encoding="utf-8")
    tours = sql.count("'MANUAL'")
    print(f"wrote {OUT} ({len(sql)} bytes, {len(COMPONENTS)} components, "
          f"{len(mountings)} mountings, {tours} tours)")


if __name__ == "__main__":
    main()
