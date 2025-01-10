package v1;

import battlecode.common.*;

public class Sensing {
    // Finds the robot within vision with the lowest HP and returns its RobotInfo
    public static RobotInfo findNearestLowestHP(RobotController rc) {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        RobotInfo targetRobot = null;
        int minHealth = -1;
        for (RobotInfo robot: nearbyRobots) {
            int robotHealth = robot.getHealth();
            if (minHealth == -1 || minHealth > robotHealth) {
                targetRobot = robot;
                minHealth = robotHealth;
            }
        }
        return targetRobot;
    }
    /**
     * Given the MapLocation of a ruin, check if that ruin has any spots needing to be filled in vision
     * Needs to be filled is defined as having empty paint or incorrect allied paint
     * Returns True if there are blocks that can be painted to still be painted, False if otherwise.
     * Purpose: Check if we need to pathfind to this tower to fill in the tower pattern
     */
    public static boolean needFilling(RobotController rc, MapLocation towerLocation) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(towerLocation, 8)){
            if (!patternTile.hasRuin() && (patternTile.getPaint() == PaintType.EMPTY ||
                    patternTile.getPaint().isAlly() && patternTile.getMark() != patternTile.getPaint())){
                return true;
            }
        }
        return false;
    }
    /**
     * Given the MapLocation of a ruin, check if the pattern is correct for a tower to be built
     *      and if there is no tower there currently
     * Returns False if the pattern is incorrect, there are no markers, there is enemy paint,
     *      or if there is a tower already existing
     * Purpose: Check of a tower can be built ignoring our coin amount
     */
    public static boolean canBuildTower(RobotController rc, MapLocation towerLocation) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(towerLocation, 8)){
            if (patternTile.hasRuin()) {
                if (rc.canSenseRobotAtLocation(patternTile.getMapLocation())) {
                    return false;
                }
            } else if ((patternTile.getMark() == PaintType.EMPTY
                    || patternTile.getMark() != patternTile.getPaint()
                    || patternTile.getPaint().isEnemy())) {
                return false;
            }
        }
        return true;
    }
}
