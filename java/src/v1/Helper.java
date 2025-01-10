package v1;

import battlecode.common.*;

public class Helper {
    // Given MapInfo loc, return True if there is a tower at loc
    public static boolean checkTower(RobotController rc, MapInfo loc){
        if (loc.hasRuin() && rc.canSenseRobotAtLocation(loc.getMapLocation())){
            return true;
        } else {
            return false;
        }
    }
}
