from battlecode25.stubs import *
from functools import total_ordering

@total_ordering
class MapInfoDistanceComparator:
    """
    Compares which one of two maplocations is further from the bot
    """
    def compare(self, info1, info2):
        current_location = self.get_location()
        location1 = info1.get_map_location()
        location2 = info2.get_map_location()

        distance1 = current_location.distance_squared_to(location1)
        distance2 = current_location.distance_squared_to(location2)

        if distance1 < distance2:
            return -1
        elif distance1 > distance2:
            return 1
        return 0

    def __call__(self, info1, info2):
        return self.compare(info1, info2)

    def __eq__(self, other):
        if not isinstance(other, MapInfoDistanceComparator):
            return NotImplemented
        return True  # All instances compare equal

    def __lt__(self, other):
        if not isinstance(other, MapInfoDistanceComparator):
            return NotImplemented
        return False  # All instances compare equal
