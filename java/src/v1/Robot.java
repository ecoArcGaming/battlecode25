package v1;

import battlecode.common.*;

import java.util.Random;

public abstract class Robot {
    /**
     * Method for robot behavior when they are low on paint
     * Currently, just pathfinds to the last paint tower seen
     */
    public static void lowPaintBehavior(RobotController rc) throws GameActionException {
        Direction dir = Pathfinding.returnToTower(rc);
        if (dir != null){
            rc.move(dir);
        }
        MapLocation towerLocation = RobotPlayer.lastTower.getMapLocation();
        Robot.completeRuinIfPossible(rc, towerLocation);
        int amtToTransfer = rc.getPaint()-rc.getType().paintCapacity;
        if (rc.canTransferPaint(towerLocation, amtToTransfer)) {
            rc.transferPaint(towerLocation, amtToTransfer);
        }
    }
    /**
     * Given MapInfo loc, return True if there is an allied tower at loc
     */
    public static boolean checkAlliedTower(RobotController rc, MapInfo loc) throws GameActionException {
        MapLocation location = loc.getMapLocation();
        if (loc.hasRuin() && rc.canSenseRobotAtLocation(location) && rc.senseRobotAtLocation(location).getTeam() == rc.getTeam()){
            return true;
        } else {
            return false;
        }
    }

    /**
     * Updates the lastTower variable to any allied paint tower currently in range
     */
    public static void updateLastPaintTower(RobotController rc) throws GameActionException {
        int min_distance = -1;
        MapInfo lastTower = null;
        for (MapInfo loc: rc.senseNearbyMapInfos()) {
            if (checkAlliedTower(rc, loc)) {
                UnitType towerType = rc.senseRobotAtLocation(loc.getMapLocation()).getType();
                if (towerType.getBaseType() == UnitType.LEVEL_ONE_PAINT_TOWER.getBaseType())
                {
                    RobotPlayer.seenPaintTower = true;
                    int distance = loc.getMapLocation().distanceSquaredTo(rc.getLocation());
                    if (min_distance == -1 || min_distance > distance){
                        lastTower = loc;
                        min_distance = distance;
                    }
                }
            }
        }
        if (min_distance != -1){
            RobotPlayer.lastTower = lastTower;
        }
    }

    /**
     * Updates the lastTower variable to any allied paint tower currently in range
     */
    public static void updateLastTower(RobotController rc) throws GameActionException {
        int min_distance = -1;
        MapInfo lastTower = null;
        for (MapInfo loc: rc.senseNearbyMapInfos()) {
            if (checkAlliedTower(rc, loc)) {
                int distance = loc.getMapLocation().distanceSquaredTo(rc.getLocation());
                if (min_distance == -1 || min_distance > distance){
                    lastTower = loc;
                    min_distance = distance;
                }
            }
        }
        if (min_distance != -1){
            RobotPlayer.lastTower = lastTower;
        }
    }


    /**
     * Check if the robot rc has less paint than the threshold
     */
    public static boolean hasLowPaint(RobotController rc, int threshold) {
        if (rc.getPaint() < threshold) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns a random tower with a Constants.TOWER_SPLIT split
     */
    public static UnitType genRandomTower() {
        double hehe = Constants.rng.nextDouble();
        return ((hehe < Constants.PERCENT_COIN) ? UnitType.LEVEL_ONE_MONEY_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER);
    }

    /**
     *  Marks a tower with random towerType at the given location if not already marked
     *  Not already marked == no marking at the spot to the north of the ruin
     */
    public static void markRandomTower(RobotController rc, MapLocation targetLoc) throws GameActionException {
        MapLocation shouldBeMarked = targetLoc.subtract(rc.getLocation().directionTo(targetLoc));
        if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY &&
                rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
            UnitType towerType = genRandomTower();
            rc.markTowerPattern(towerType, targetLoc);
        }
    }

    /**
     *  Marks a tower with towerType at the given location if not already marked
     *  Not already marked == no marking at the spot to the north of the ruin
     */
    public static void markTower(RobotController rc, UnitType towerType, MapLocation targetLoc) throws GameActionException {
        MapLocation shouldBeMarked = targetLoc.subtract(rc.getLocation().directionTo(targetLoc));
        if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY &&
                rc.canMarkTowerPattern(towerType, targetLoc)){
            rc.markTowerPattern(towerType, targetLoc);
        }
    }

    /**
     * Completes the ruin at the given location if possible
     */
    public static void completeRuinIfPossible(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruinLocation);
        }
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation);
        }
    }
}
