package v1;

import battlecode.common.*;

/**
 * Class for all methods that a soldier will do
 */
public class Soldier extends Robot {
    /**
     * Methods for soldiers painting, given a MapInfo and/or MapLocation
     * Paints when there is no paint or if allied paint is incorrect
     * */
    public static void paintIfPossible(RobotController rc, MapInfo paintTile, MapLocation paintLocation) throws GameActionException {
        if (paintTile.getPaint() == PaintType.EMPTY
                && rc.canAttack(paintLocation)) {
            rc.attack(paintLocation);
        } else if ((!paintTile.getPaint().isEnemy()) && paintTile.getMark() != paintTile.getPaint()
                && rc.canAttack(paintLocation)){
            boolean useSecondaryColor = paintTile.getMark() == PaintType.ALLY_SECONDARY;
            rc.attack(paintLocation, useSecondaryColor);
        }
    }
    public static void paintIfPossible(RobotController rc, MapInfo paintTile) throws GameActionException {
        MapLocation paintLocation = paintTile.getMapLocation();
        paintIfPossible(rc, paintTile, paintLocation);
    }
    public static void paintIfPossible(RobotController rc, MapLocation paintLocation) throws GameActionException {
        MapInfo paintTile = rc.senseMapInfo(rc.getLocation());
        paintIfPossible(rc, paintTile, paintLocation);
    }

    /**
     * If tower is there, sends message to tower that enemy paint exists.
     * Otherwise, pathfind to tower
     */
    public static void informTowerOfEnemyPaint(RobotController rc, MapInfo enemyTile) throws GameActionException {
        Direction dir = Pathfinding.returnToTower(rc);
        if (dir != null){
            rc.move(dir);
        }
        MapLocation towerLocation = RobotPlayer.lastTower.getMapLocation();
        if (rc.canSendMessage(towerLocation)) {
            Communication.sendMapInformation(rc, enemyTile, towerLocation);
            RobotPlayer.enemyTile = null;
        }
    }
}
