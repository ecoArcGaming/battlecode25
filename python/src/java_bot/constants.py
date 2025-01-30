from typing import Final, Dict, Set, Tuple
from battlecode25.stubs import *
from .hashable_coords import HashableCoords

class Constants:
    # Directions as an immutable tuple
    DIRECTIONS: Final[Tuple[Direction, ...]] = (
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    )

    # Paint loss values as a frozen dictionary
    PAINT_LOSS_VALUES: Final[Dict[PaintType, int]] = {
        PaintType.ALLY_PRIMARY: 0,
        PaintType.ALLY_SECONDARY: 0,
        PaintType.EMPTY: -1,
        PaintType.ENEMY_PRIMARY: -2,
        PaintType.ENEMY_SECONDARY: -2
    }

    # Game constants
    PERCENT_PAINT: Final[float] = 0.7
    RESIGN_AFTER: Final[int] = 2005
    LOW_PAINT_THRESHOLD: Final[int] = 20
    INIT_PROBABILITY_DEVELOP: Final[int] = 100
    RANDOM_STEP_PROBABILITY: Final[float] = 0.5
    DEVELOP_BOT_PROBABILITY_CAP: Final[float] = 0.6
    DEVELOP_BOT_PROB_SCALING: Final[int] = 200
    DEFENSE_RANGE: Final[float] = 0.3
    SPLASHER_CUTOFF: Final[int] = 8
    SPLASHER_SOLDIER_SPLIT: Final[float] = 0.5
    LOW_PAINT_MONEY_THRESHOLD: Final[int] = 5000
    DEV_SRP_BOT_SPLIT: Final[float] = 0.8

    DEV_LIFE_CYCLE_TURNS: Final[int] = 30
    SRP_LIFE_CYCLE_TURNS: Final[int] = 30
    MIN_PAINT_GIVE: Final[int] = 50

    SRP_MAP_WIDTH: Final[int] = 95
    SRP_MAP_HEIGHT: Final[int] = 95

    # Primary SRP coordinates as a frozenset
    PRIMARY_SRP: Final[Set[HashableCoords]] = frozenset({
        HashableCoords(2, 0),
        HashableCoords(1, 1), HashableCoords(2, 1), HashableCoords(3, 1),
        HashableCoords(0, 2), HashableCoords(1, 2), HashableCoords(3, 2),
        HashableCoords(1, 3), HashableCoords(2, 3), HashableCoords(3, 3),
        HashableCoords(2, 4), HashableCoords(4, 2)
    })

    # Tower patterns as tuples of tuples for immutability
    PAINT_TOWER_PATTERN: Final[Tuple[Tuple[PaintType, ...], ...]] = (
        (PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY),
        (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
        (PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.EMPTY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY),
        (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
        (PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY)
    )

    MONEY_TOWER_PATTERN: Final[Tuple[Tuple[PaintType, ...], ...]] = (
        (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
        (PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY),
        (PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.EMPTY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY),
        (PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY),
        (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY)
    )

    DEFENSE_TOWER_PATTERN: Final[Tuple[Tuple[PaintType, ...], ...]] = (
        (PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY),
        (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
        (PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.EMPTY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY),
        (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
        (PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY)
    )

    @classmethod
    def get_random(cls) -> random.Random:
        """Get a seeded random number generator for deterministic behavior"""
        # TODO: Replace this with game's built-in RNG if available
        rng = random.Random()
        rng.seed(42)  # Use a fixed seed for reproducibility
        return rng
