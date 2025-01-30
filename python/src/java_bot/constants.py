from battlecode25.stubs import *
import random

# Directions as an immutable tuple
DIRECTIONS = (
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
PAINT_LOSS_VALUES = {
    PaintType.ALLY_PRIMARY: 0,
    PaintType.ALLY_SECONDARY: 0,
    PaintType.EMPTY: -1,
    PaintType.ENEMY_PRIMARY: -2,
    PaintType.ENEMY_SECONDARY: -2
}

# Game constants
PERCENT_PAINT = 0.7
RESIGN_AFTER = 2005
LOW_PAINT_THRESHOLD = 20
INIT_PROBABILITY_DEVELOP = 100
RANDOM_STEP_PROBABILITY = 0.5
DEVELOP_BOT_PROBABILITY_CAP = 0.6
DEVELOP_BOT_PROB_SCALING = 200
DEFENSE_RANGE = 0.3
SPLASHER_CUTOFF = 8
SPLASHER_SOLDIER_SPLIT = 0.5
LOW_PAINT_MONEY_THRESHOLD = 5000
DEV_SRP_BOT_SPLIT = 0.8

DEV_LIFE_CYCLE_TURNS = 30
SRP_LIFE_CYCLE_TURNS = 30
MIN_PAINT_GIVE = 50

SRP_MAP_WIDTH = 95
SRP_MAP_HEIGHT = 95

# Primary SRP coordinates as a frozenset
PRIMARY_SRP = frozenset({
    (2, 0),
    (1, 1), (2, 1), (3, 1),
    (0, 2), (1, 2), (2, 2), (3, 2), (4, 2),
    (1, 3), (2, 3), (3, 3),
    (2, 4)
})

# Secondary SRP coordinates as a frozenset
SECONDARY_SRP = frozenset({
    (2, 0),
    (1, 1), (2, 1), (3, 1),
    (0, 2), (1, 2), (2, 2), (3, 2), (4, 2),
    (1, 3), (2, 3), (3, 3),
    (2, 4)
})

# Tower patterns as tuples of tuples for immutability
PAINT_TOWER_PATTERN = (
    (PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY),
    (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
    (PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.EMPTY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY),
    (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
    (PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY)
)

MONEY_TOWER_PATTERN = (
    (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
    (PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY),
    (PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.EMPTY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY),
    (PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY),
    (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY)
)

DEFENSE_TOWER_PATTERN = (
    (PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY),
    (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
    (PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.EMPTY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY),
    (PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY),
    (PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY)
)