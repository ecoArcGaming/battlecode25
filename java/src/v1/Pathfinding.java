package v1;

import battlecode.common.*;

/**
 * Class for all movement & pathfinding-related methods
 */
public class Pathfinding {
    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    public static Direction returnToTower(RobotController rc) throws GameActionException{
        for (MapInfo loc: rc.senseNearbyMapInfos()){
            if(Helper.checkTower(rc, loc)){
                return Pathfind(rc, loc.getMapLocation());
            }
        }
        return Pathfind(rc, lastTower.getMapLocation());
    }
}
