package v1;

import battlecode.common.*;

public abstract class Robot {
    // Updates the lastTower variable to any paint tower currently in range
    public static void updateLastPaintTower(RobotController rc){
        for (MapInfo loc: rc.senseNearbyMapInfos()) {
            if (Helper.checkTower(rc, loc)) {
                RobotPlayer.lastPaintTower = loc;
            }
        }
    }
}
