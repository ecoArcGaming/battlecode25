package v2;

import battlecode.common.*;

public class Helper {
    public static boolean resourcePatternGrid(RobotController rc, MapLocation loc) {
        int x = loc.x % 4;
        int y = loc.y % 4;
        HashableCoords coords = new HashableCoords(x, y);
        return Constants.primarySRP.contains(coords);
    }

    public static void tryCompleteResourcePattern(RobotController rc) throws GameActionException {
        for (MapInfo tile: rc.senseNearbyMapInfos(16)){
            if (rc.canCompleteResourcePattern(tile.getMapLocation())){
                rc.completeResourcePattern(tile.getMapLocation());
            }
        }
    }
}
