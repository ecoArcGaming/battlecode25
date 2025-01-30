import random
from battlecode25.stubs import *
from .hashable_coords import HashableCoords

# Directions
directions = [
    Direction.NORTH,
    Direction.NORTHEAST,
    Direction.EAST,
    Direction.SOUTHEAST,
    Direction.SOUTH,
    Direction.SOUTHWEST,
    Direction.WEST,
    Direction.NORTHWEST,
]

# Paint loss values
paint_loss_values = {
    PaintType.ALLY_PRIMARY: 0,
    PaintType.ALLY_SECONDARY: 0,
    PaintType.EMPTY: -1,
    PaintType.ENEMY_PRIMARY: -2,
    PaintType.ENEMY_SECONDARY: -2
}

# Random number generator
rng = random.Random()

# Constants
PERCENT_PAINT = 0.7
RESIGN_AFTER = 2005
LOW_PAINT_THRESHOLD = 20
INIT_PROBABILITY_DEVELOP = 100
RANDOM_STEP_PROBABILITY = 0.5
DEVELOP_BOT_PROBABILITY_CAP = 0.6
DEVELOP_BOT_PROB_SCALING = 200
DEFENSE_RANGE = 0.3
SPLASHER_CUTOFF = 8  # num soldiers spawned before splashers spawn with below variable chance
SPLASHER_SOLDIER_SPLIT = 0.5
LOW_PAINT_MONEY_THRESHOLD = 5000
DEV_SRP_BOT_SPLIT = 0.8

DEV_LIFE_CYCLE_TURNS = 30
SRP_LIFE_CYCLE_TURNS = 30
MIN_PAINT_GIVE = 50

SRP_MAP_WIDTH = 95
SRP_MAP_HEIGHT = 95

# Primary SRP coordinates
primary_srp = {
    HashableCoords(2, 0),
    HashableCoords(1, 1), HashableCoords(2, 1), HashableCoords(3, 1),
    HashableCoords(0, 2), HashableCoords(1, 2), HashableCoords(3, 2),
    HashableCoords(1, 3), HashableCoords(2, 3), HashableCoords(3, 3),
    HashableCoords(2, 4), HashableCoords(4, 2)
}

# Tower patterns
paint_tower_pattern = [
    [PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY],
    [PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY],
    [PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.EMPTY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY],
    [PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY],
    [PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY]
]

money_tower_pattern = [
    [PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY],
    [PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY],
    [PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.EMPTY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY],
    [PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY],
    [PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY]
]

defense_tower_pattern = [
    [PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY],
    [PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY],
    [PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.EMPTY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY],
    [PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY],
    [PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_SECONDARY, PaintType.ALLY_PRIMARY, PaintType.ALLY_PRIMARY]
]
