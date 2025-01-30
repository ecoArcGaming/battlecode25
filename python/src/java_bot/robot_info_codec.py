from battlecode25.stubs import *
import math

"""
RobotInfoCodec encodes and decodes a RobotInfo to/from an int so that it can be sent in a message.
Coding uses the bottom 31 of the available 32 bits: _ppppppp Thhhhhhh UUUUyyyy yyxxxxxx
paint (p) and health (h) are approximate, since it won't fit otherwise. ID is not sent.
"""


def encode(robot_info):
    """
    Encode the given RobotInfo into an integer value.
    Approx 69 bytecode.
    """
    i = 0
    i = i + robot_info.get_location().x
    i = i + (robot_info.get_location().y << 6)
    i = i + (robot_info.get_type().value << 12)
    health_percent = (100 * robot_info.get_health()) // robot_info.get_type().health
    i = i + (health_percent << 16)
    i = i + (robot_info.get_team().value << 23)
    paint_percent = (100 * robot_info.get_paint_amount()) // robot_info.get_type().paint_capacity
    i = i + (paint_percent << 24)
    return i


def decode(i):
    """
    Decode the given integer into a RobotInfo.
    Approx 71 bytecode.
    """
    location_mask = (1 << 6) - 1
    x = i & location_mask
    y = (i >> 6) & location_mask
    unit_type = UnitType((i >> 12) & ((1 << 4) - 1))
    health_percent = (i >> 16) & ((1 << 7) - 1)
    team = Team((i >> 23) & 1)
    paint_percent = (i >> 24) & ((1 << 7) - 1)
    
    health = math.ceil((unit_type.health / 100.0) * health_percent)
    paint = math.ceil((unit_type.paint_capacity / 100.0) * paint_percent)
    
    return RobotInfo(0, team, unit_type, health, MapLocation(x, y), paint)


def equals(a, b):
    """Compare two RobotInfo objects for equality"""
    return (a.get_location().equals(b.get_location()) and
            a.get_type() == b.get_type() and 
            a.get_team() == b.get_team() and
            a.get_health() == b.get_health() and
            a.get_paint_amount() == b.get_paint_amount())
