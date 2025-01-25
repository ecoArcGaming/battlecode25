package v3;

import battlecode.common.*;

public class Helper {
    /**
     * the map is predivided into 4x4 grids, which soldiers will use to paint tiles accordingly
     */
    public static boolean resourcePatternGrid(RobotController rc, MapLocation loc) {
        int x = loc.x % 4;
        int y = loc.y % 4;
        HashableCoords coords = new HashableCoords(x, y);
        return Constants.primarySRP.contains(coords);
    }
    public static PaintType resourcePatternType(RobotController rc, MapLocation loc) {

        int x = loc.x % 4;
        int y = loc.y % 4;
        HashableCoords coords = new HashableCoords(x, y);
        if (Constants.primarySRP.contains(coords)){
            return PaintType.ALLY_PRIMARY;
        }
        return PaintType.ALLY_SECONDARY;
    }
    /**
     * any bot will try to complete resource pattterns nearby
     */
    public static void tryCompleteResourcePattern(RobotController rc) throws GameActionException {
        for (MapInfo tile: rc.senseNearbyMapInfos(16)){
            if (rc.canCompleteResourcePattern(tile.getMapLocation())){
                rc.completeResourcePattern(tile.getMapLocation());
            }
        }
    }
    /**
     * Check if a MapLocation m is in the rectangle with c1 and c2 as its corners
     */
    public static boolean isBetween(MapLocation m, MapLocation c1, MapLocation c2) {
        // Determine the min and max bounds for x and y coordinates
        int minX = Math.min(c1.x, c2.x);
        int maxX = Math.max(c1.x, c2.x);
        int minY = Math.min(c1.y, c2.y);
        int maxY = Math.max(c1.y, c2.y);

        // Check if m is within these bounds
        return m.x >= minX && m.x <= maxX && m.y >= minY && m.y <= maxY;
    }

}
