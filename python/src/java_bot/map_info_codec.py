from battlecode25.stubs import *

class MapInfoCodec:
    """
    MapInfoCodec encodes and decodes a MapInfo to/from an int so that it can be sent in a message.
    Coding uses the bottom 21 of the available 32 bits: ________ ___Rmmmp ppWPyyyy yyxxxxxx
    """
    
    @staticmethod
    def encode(map_info):
        """
        Encode the given MapInfo into an integer value.
        Approx 55 bytecode.
        """
        i = 0
        i += map_info.get_map_location().x
        i += map_info.get_map_location().y << 6
        if map_info.is_passable():
            i += 1 << 12
        if map_info.is_wall():
            i += 1 << 13
        i += map_info.get_paint().value << 14
        i += map_info.get_mark().value << 17
        if map_info.has_ruin():
            i += 1 << 20
        return i

    @staticmethod
    def decode(i):
        """
        Decode the given integer into a MapInfo.
        Approx 62 bytecode.
        """
        mask = (1 << 6) - 1
        x = i & mask
        y = (i >> 6) & mask
        is_passable = (i & (1 << 12)) != 0
        is_wall = (i & (1 << 13)) != 0
        paint = PaintType(((i >> 14) & ((1 << 3) - 1)))
        mark = PaintType(((i >> 17) & ((1 << 3) - 1)))
        has_ruin = (i & (1 << 20)) != 0
        return MapInfo(MapLocation(x, y), is_passable, is_wall, paint, mark, has_ruin, False)

    @staticmethod
    def equals(a, b):
        """Compare two MapInfo objects for equality"""
        return (a.get_map_location().equals(b.get_map_location()) and
                a.is_passable() == b.is_passable() and 
                a.is_wall() == b.is_wall() and
                a.get_paint() == b.get_paint() and 
                a.get_mark() == b.get_mark() and
                a.has_ruin() == b.has_ruin())
