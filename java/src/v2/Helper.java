package v2;

import battlecode.common.*;

public class Helper {
    public static PaintType resourcePatternGrid(RobotController rc, MapLocation loc) {
        int x = loc.x % 4;
        int y = loc.y % 4;
        HashableCoords coords = new HashableCoords(x, y);
        if (Constants.primarySRP.contains(coords)) {
            return PaintType.ALLY_PRIMARY;
        } else {
            return PaintType.ALLY_SECONDARY;
        }

    }
}
