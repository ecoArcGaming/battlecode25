class HashableCoords:
    """A hashable version of MapLocation"""
    def __init__(self, x: int, y: int):
        self.x = x
        self.y = y

    def __eq__(self, other):
        if not isinstance(other, HashableCoords):
            return False
        return self.x == other.x and self.y == other.y

    def __hash__(self):
        return 31 * self.x + self.y
