package v3;

import battlecode.common.*;

import java.nio.file.Path;

import static v3.RobotPlayer.*;

public abstract class Robot {
    /**
     * Method for robot behavior when they are low on paint
     */
    public static void lowPaintBehavior(RobotController rc) throws GameActionException {
        isLowPaint = true;
        // If last tower is null, then just random walk on paint
        for (RobotInfo enemyRobot : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (enemyRobot.getType().isTowerType()) {
                if (rc.canAttack(enemyRobot.getLocation())) {
                    rc.attack(enemyRobot.getLocation());
                    break;
                }
            }
        }
        if (lastTower == null){
            Direction moveTo = Pathfinding.randomPaintedWalk(rc);
            if (moveTo != null && rc.canMove(moveTo)){
                rc.move(moveTo);
            }
            return;
        }
        Direction dir = Pathfinding.returnToTower(rc);
        if (dir != null){
            rc.move(dir);
        }
        // Otherwise, pathfind to the tower
        MapLocation towerLocation = lastTower.getMapLocation();
        Robot.completeRuinIfPossible(rc, towerLocation);
        int amtToTransfer = rc.getPaint()-rc.getType().paintCapacity;
        if (rc.canSenseRobotAtLocation(towerLocation)){
            int towerPaint = rc.senseRobotAtLocation(towerLocation).paintAmount;
            if (rc.getPaint() < 5 && rc.canTransferPaint(towerLocation, -towerPaint) && towerPaint > Constants.MIN_PAINT_GIVE){
                rc.transferPaint(towerLocation, -towerPaint);
            }
        }
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
                    seenPaintTower = true;
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
        else if (lastTower != null && lastTower.getMapLocation().isWithinDistanceSquared(rc.getLocation(), 20)){
            RobotPlayer.lastTower = null;
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
     * Returns a random tower with a Constants.TOWER_SPLIT split and defense tower only if in range
     */
    public static UnitType genTowerType(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        if (rc.getNumberTowers() <= 3){
            return UnitType.LEVEL_ONE_MONEY_TOWER;
        }
        double probDefense = Math.min(1, (double)(rc.getNumberTowers())/(rc.getMapHeight()+rc.getMapWidth())*7);
        double probFromCenter = 1-2.5*(Math.abs(rc.getMapWidth() / 2 - ruinLocation.x) + Math.abs(rc.getMapHeight() / 2 - ruinLocation.y))/(rc.getMapHeight()+rc.getMapWidth());
        double haha = Constants.rng.nextDouble();
        if (haha < probDefense*probFromCenter){
            return UnitType.LEVEL_ONE_DEFENSE_TOWER;
        }
        double hehe = Constants.rng.nextDouble();
        return ((hehe < Math.min((rc.getNumberTowers())/Math.sqrt(rc.getMapHeight()+rc.getMapWidth()), Constants.PERCENT_PAINT)) ? UnitType.LEVEL_ONE_PAINT_TOWER : UnitType.LEVEL_ONE_MONEY_TOWER);
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
        if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation)) {
            rc.completeTowerPattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruinLocation);
        }
    }
    /**
     * Resets pathfinding variables
     * Meant to be called when the robot has found else to do
     */
    public static void resetVariables() {
        isTracing = false;
        smallestDistance = 10000000;
        closestLocation = null;
        tracingDir = null;
        stuckTurnCount = 0;
        closestPath = -1;
        fillTowerType = null;
        stoppedLocation = null;
        tracingTurns = 0;
        bug1Turns = 0;
        inBugNav = false;
        acrossWall = null;
    }
}
