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

    public static void markSRP(RobotController rc, MapInfo towerLoc) throws GameActionException {
        int centerX = towerLoc.getMapLocation().x;
        int centerY = towerLoc.getMapLocation().y;
        int[][] corners = new int[4][2];

        // Top-left corner
        corners[0] = new int[]{centerX - 3, centerY + 3};
        // Top-right corner
        corners[1] = new int[]{centerX + 3, centerY + 3};
        // Bottom-left corner
        corners[2] = new int[]{centerX - 3, centerY - 3};
        // Bottom-right corner
        corners[3] = new int[]{centerX + 3, centerY - 3};

        for (int i = 0; i < 4; i++) {
            if (RobotPlayer.curr != i){
                continue;
            }
            MapLocation corn = new MapLocation(corners[i][0], corners[i][1]);
            if (!rc.getLocation().equals(corn)) {
                Direction dir = Pathfinding.pathfind(rc, corn);
                if (dir != null) {
                    rc.move(dir);
                }
            }
            else if (rc.canMarkResourcePattern(corn)){
                rc.markResourcePattern(corn);
                RobotPlayer.SRPLocation = corn;
                RobotPlayer.markingSRP = false;
                RobotPlayer.fillingSRP = true;
                RobotPlayer.curr = 0;

            }
            else {
                RobotPlayer.curr++;
            }
        }
    }
    public static void fillSRP(RobotController rc, MapLocation SRPLoc) throws GameActionException {
        if (!rc.getLocation().equals(SRPLoc)){
            Direction dir = Pathfinding.pathfind(rc, SRPLoc);
            if (dir != null){
                rc.move(dir);
            }
//            return;
        } else {
            RobotPlayer.tries++;
            MapInfo paint = Sensing.findPaintableTile(rc);
            if (paint != null) {
                Soldier.paintIfPossible(rc, paint);
//                return;
            }
        }
        Robot.completeSRPIfPossible(rc, SRPLoc);
    }
}
