from battlecode25.stubs import *
from .hashable_coords import HashableCoords
from .constants import primary_srp

def resource_pattern_grid(rc, loc):
    """
    The map is predivided into 4x4 grids, which soldiers will use to paint tiles accordingly
    """
    x = loc.x % 4
    y = loc.y % 4
    coords = HashableCoords(x, y)
    return coords in primary_srp

def resource_pattern_type(rc, loc):
    """
    Determine the paint type for a resource pattern at the given location
    """
    x = loc.x % 4
    y = loc.y % 4
    coords = HashableCoords(x, y)
    if coords in primary_srp:
        return PaintType.ALLY_PRIMARY
    return PaintType.ALLY_SECONDARY

def try_complete_resource_pattern(rc):
    """
    Any bot will try to complete resource patterns nearby
    """
    for tile in rc.sense_nearby_map_infos(16):
        if rc.can_complete_resource_pattern(tile.get_map_location()):
            rc.complete_resource_pattern(tile.get_map_location())

def is_between(m, c1, c2):
    """
    Check if a MapLocation m is in the rectangle with c1 and c2 as its corners
    """
    # Determine the min and max bounds for x and y coordinates
    min_x = min(c1.x, c2.x)
    max_x = max(c1.x, c2.x)
    min_y = min(c1.y, c2.y)
    max_y = max(c1.y, c2.y)

    # Check if m is within these bounds
    return min_x <= m.x <= max_x and min_y <= m.y <= max_y
