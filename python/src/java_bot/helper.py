from battlecode25.stubs import *
from .hashable_coords import HashableCoords
from .constants import Constants
from typing import Optional

def resource_pattern_grid(loc: MapLocation) -> bool:
    """
    Check if a location is part of the primary resource pattern grid.
    
    Args:
        loc: The MapLocation to check
        
    Returns:
        bool: True if the location is part of the primary resource pattern, False otherwise
    """
    x = loc.x % 4
    y = loc.y % 4
    coords = HashableCoords(x, y)
    return is_primary_srp(coords)

def resource_pattern_type(loc: MapLocation) -> PaintType:
    """
    Determine the paint type for a resource pattern at the given location.
    
    Args:
        loc: The MapLocation to check
        
    Returns:
        PaintType: The type of paint that should be at this location (ALLY_PRIMARY or ALLY_SECONDARY)
    """
    x = loc.x % 4
    y = loc.y % 4
    coords = HashableCoords(x, y)
    return get_srp_type(coords)

def try_complete_resource_pattern() -> None:
    """
    Try to complete resource patterns in nearby tiles.
    Scans nearby tiles within a radius of 16 and attempts to complete any resource patterns found.
    """
    for tile in sense_nearby_map_infos(16):
        if can_complete_resource_pattern(tile.get_map_location()):
            complete_resource_pattern(tile.get_map_location())

def is_between(m: MapLocation, c1: MapLocation, c2: MapLocation) -> bool:
    """
    Check if a MapLocation is within the rectangle defined by two corner points.
    
    Args:
        m: The MapLocation to check
        c1: First corner of the rectangle
        c2: Second corner of the rectangle
        
    Returns:
        bool: True if m is within or on the rectangle bounds, False otherwise
    """
    min_x = min(c1.x, c2.x)
    max_x = max(c1.x, c2.x)
    min_y = min(c1.y, c2.y)
    max_y = max(c1.y, c2.y)

    return min_x <= m.x <= max_x and min_y <= m.y <= max_y

def is_primary_srp(coords: HashableCoords) -> bool:
    """
    Check if coordinates are part of the primary SRP pattern.
    
    Args:
        coords: The HashableCoords to check
        
    Returns:
        bool: True if the coordinates are part of the primary SRP pattern, False otherwise
    """
    return coords in Constants.PRIMARY_SRP

def get_srp_type(coords: HashableCoords) -> PaintType:
    """
    Get the paint type that should be at the given coordinates in the SRP pattern.
    
    Args:
        coords: The HashableCoords to check
        
    Returns:
        PaintType: ALLY_PRIMARY if coords are in the primary SRP pattern, ALLY_SECONDARY otherwise
    """
    if coords in Constants.PRIMARY_SRP:
        return PaintType.ALLY_PRIMARY
    return PaintType.ALLY_SECONDARY
