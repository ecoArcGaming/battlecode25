package v1;

import battlecode.common.*;

/**
 *  Class for all general-purpose tower methods
 */

public abstract class Tower {
    /**
     * Reads new messages and does stuff
     */
    public static void readNewMessages(RobotController rc) throws GameActionException{
        // Looks at all incoming messages
        for (Message message: rc.readMessages(rc.getRoundNum()-1)){
            int bytes = message.getBytes();
            if (Communication.isRobotInfo(bytes)){
                RobotInfo msg = RobotInfoCodec.decode(bytes);
                continue;
            }
            else{
                MapInfo msg = MapInfoCodec.decode(bytes);
                if (msg.getPaint().isEnemy()){
                    RobotPlayer.spawnQueue.add(3);
                    RobotPlayer.spawnQueue.add(2);
                    RobotPlayer.enemyTile = msg;
                }
            }
        }
    }

    /**
     * Builds a robot of type robotType at location
     */
    public static void buildIfPossible(RobotController rc, UnitType robotType, MapLocation location)  throws GameActionException {
        if (rc.canBuildRobot(robotType, location)) {
            rc.buildRobot(robotType, location);
        }
    }

    /**
     * Builds a robot of type robotType at a random location
     */
    public static void buildAtRandomLocation(RobotController rc, UnitType robotType) throws GameActionException {
        Direction dir = Constants.directions[Constants.rng.nextInt(Constants.directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        buildIfPossible(rc, robotType, nextLoc);
        if (rc.canSendMessage(nextLoc)){
            rc.sendMessage(nextLoc, 1);
        }
    }

    /**
     * Builds a random robot at a random location
     */
    public static void buildCompletelyRandom(RobotController rc) throws GameActionException {
//        double robotType = Constants.rng.nextDouble();
//        if (robotType < 0.33) {
        RobotPlayer.spawnQueue.add(1);
//        } else if (robotType < 0.66) {
//            buildAtRandomLocation(rc, UnitType.MOPPER);
//        } else {
//            //buildAtRandomLocation(rc, UnitType.SPLASHER);
//        }
    }

    /**
     * Fires an attack at location if possible
     */
    public static void fireAttackIfPossible(RobotController rc, MapLocation location) throws GameActionException {
        if (rc.canAttack(location))  {
            rc.attack(location);
        }
    }

    /**
     * Attacks the robot with the lowest HP within attack range
     */
    public static void attackLowestRobot(RobotController rc) throws GameActionException {
        RobotInfo nearestLowBot = Sensing.findNearestLowestHP(rc);
        if (nearestLowBot != null) {
            fireAttackIfPossible(rc, nearestLowBot.getLocation());
        }
    }

    /**
     * Does an AOE attack if possible
     */
    public static void aoeAttackIfPossible(RobotController rc) throws GameActionException {
        if (rc.canAttack(null)) {
            rc.attack(null);
        }
    }

    /**
     * Creates a soldier at location NORTH if possible
     */
    public static void createSoldier(RobotController rc) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(Direction.NORTH);
        if (rc.canBuildRobot(UnitType.SOLDIER, addedDir)) {
            rc.buildRobot(UnitType.SOLDIER, addedDir);
            RobotPlayer.sendTypeMessage = true;
        }
    }

    /**
     * Creates a mopper at location NORTH if possible
     */
    public static void createMopper(RobotController rc) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(Direction.NORTH);
        if (rc.canBuildRobot(UnitType.MOPPER, addedDir)) {
            rc.buildRobot(UnitType.MOPPER, addedDir);
            RobotPlayer.sendTypeMessage = true;
        }
    }

    public static void createSplasher(RobotController rc) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(Direction.NORTH);
        if (rc.canBuildRobot(UnitType.SPLASHER, addedDir)) {
            rc.buildRobot(UnitType.SPLASHER, addedDir);
            RobotPlayer.sendTypeMessage = true;
        }
    }

    public static void sendTypeMessage(RobotController rc, int robotType) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(Direction.NORTH);
        if (rc.canSendMessage(addedDir)){
            rc.sendMessage(addedDir, robotType);
            // If robot is an attack soldier or mopper, send enemy tile location as well
            if (robotType == 3 || robotType == 2) {
                Communication.sendMapInformation(rc, RobotPlayer.enemyTile, addedDir);
            }
            RobotPlayer.sendTypeMessage = false;
            RobotPlayer.spawnQueue.removeFirst();
        }
    }
}
