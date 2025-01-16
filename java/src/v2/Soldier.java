package v2;

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
                && rc.canAttack(paintLocation) && paintTile.getMark() == PaintType.EMPTY) {
            rc.attack(paintLocation);
        } else if ((!paintTile.getPaint().isEnemy()) && paintTile.getMark() != paintTile.getPaint()
                && paintTile.getMark() != PaintType.EMPTY && rc.canAttack(paintLocation)){
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
        if (rc.canSenseRobotAtLocation(towerLocation) && rc.canSendMessage(towerLocation)) {
            Communication.sendMapInformation(rc, enemyTile, towerLocation);
            RobotPlayer.enemyTile = null;
        }
    }

    /**
     * Advances attack soldier to the enemy tower
     */
    public static void attackEnemyTower(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        for (MapInfo nearbyTile : nearbyTiles) {
            // If enemy tower detected, then attack if you can or move towards it
            if (nearbyTile.hasRuin() && rc.canSenseRobotAtLocation(nearbyTile.getMapLocation()) && rc.senseRobotAtLocation(nearbyTile.getMapLocation()).getTeam().opponent().equals(rc.getTeam())){
                RobotPlayer.enemyTower = nearbyTile;
                if (rc.canAttack(nearbyTile.getMapLocation())) {
                    rc.attack(nearbyTile.getMapLocation());
                }
                else{
                    Direction dir = Pathfinding.bug2(rc, nearbyTile.getMapLocation());
                    if (dir != null) {
                        rc.move(dir);
                    }
                }
                return;
            }
        }
        RobotPlayer.enemyTower = null;
        // If no visible tower, keep moving towards enemy tile
        Direction dir = Pathfinding.bug2(rc, RobotPlayer.enemyTile.getMapLocation());
        if (dir != null) {
            rc.move(dir);
        }
        if (rc.canSenseLocation(RobotPlayer.enemyTile.getMapLocation()) && !rc.senseMapInfo(RobotPlayer.enemyTile.getMapLocation()).getPaint().isEnemy()){
            RobotPlayer.enemyTile = null;
            RobotPlayer.soldierType = SoldierType.ADVANCE;
        }
    }

    /**
     * Reads incoming messages:
     * When spawning in, message will be received that tells the robot what type of bot it should be
     */
    public static void readNewMessages(RobotController rc) throws GameActionException{
        // Looks at all incoming messages
        for (Message message: rc.readMessages(rc.getRoundNum()-1)){
            int bytes = message.getBytes();
            // Information is type of robot
            if (bytes == 0 || bytes == 1 || bytes == 2) {
                switch (bytes) {
                    case 0:
                        RobotPlayer.soldierType = SoldierType.DEVELOP;
                        break;
                    case 1:
                        RobotPlayer.soldierType = SoldierType.ADVANCE;
                        break;
                    case 2:
                        RobotPlayer.soldierType = SoldierType.ATTACK;
                        break;
                }
            }
            else{
                if (Communication.isRobotInfo(bytes)){
                    return;
                }
                // For attack robots to get enemy paint location
                else if (RobotPlayer.soldierType == SoldierType.ATTACK){
                    System.out.println("soldier received" + MapInfoCodec.decode(bytes));
                    RobotPlayer.enemyTile = MapInfoCodec.decode(bytes);
                }
            }
        }
    }
}
