package v2;

import battlecode.common.*;
import java.util.Comparator;

public class MapInfoDistanceComparator implements Comparator<MapInfo> {
    private final RobotController rc;

    public MapInfoDistanceComparator(RobotController rc) {
        this.rc = rc;
    }

    @Override
    public int compare(MapInfo info1, MapInfo info2) {
        MapLocation currentLocation = rc.getLocation();
        MapLocation location1 = info1.getMapLocation();
        MapLocation location2 = info2.getMapLocation();

        int distance1 = currentLocation.distanceSquaredTo(location1);
        int distance2 = currentLocation.distanceSquaredTo(location2);

        return Integer.compare(distance1, distance2);
    }
}

