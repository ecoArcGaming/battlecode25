package v2;

import battlecode.common.*;

import java.util.Map;

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
                    double robotType = Constants.rng.nextDouble();
                    // prepare to broadcast new enemy location
                    if (Sensing.isRobot(rc, message.getSenderID()) && !RobotPlayer.ignore){
                        RobotPlayer.broadcast = true;
                    }

                    if (Sensing.isTower(rc, message.getSenderID())){
                        RobotPlayer.broadcast = true;
                    }

                    if (robotType > Constants.MOPPER_SPLIT){
                        RobotPlayer.spawnQueue.add(4);
                    } else {
                        RobotPlayer.spawnQueue.add(2);
                    }
                    RobotPlayer.spawnQueue.add(3);
                    RobotPlayer.enemyTile = msg;
                    RobotPlayer.numEnemyVisits += 1;
                }
            }
        }
        // stop ignoring after round one
        if (RobotPlayer.ignore){
            RobotPlayer.ignore = false;
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
        double robotType = Constants.rng.nextDouble();
        if (robotType < RobotPlayer.numEnemyVisits*0.2) {
            RobotPlayer.spawnQueue.add(4);
            RobotPlayer.numEnemyVisits = 0;
        } else {
            RobotPlayer.spawnQueue.add(1);
        }
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
        MapLocation addedDir = rc.getLocation().add(RobotPlayer.spawnDirection);
        if (rc.canBuildRobot(UnitType.SOLDIER, addedDir)) {
            rc.buildRobot(UnitType.SOLDIER, addedDir);
            RobotPlayer.sendTypeMessage = true;
        }
    }

    /**
     * Creates a mopper at location NORTH if possible
     */
    public static void createMopper(RobotController rc) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(RobotPlayer.spawnDirection);
        if (rc.canBuildRobot(UnitType.MOPPER, addedDir)) {
            rc.buildRobot(UnitType.MOPPER, addedDir);
            RobotPlayer.sendTypeMessage = true;
        }
    }

    /**
     * Creates a splasher at the north
     */
    public static void createSplasher(RobotController rc) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(RobotPlayer.spawnDirection);
        if (rc.canBuildRobot(UnitType.SPLASHER, addedDir)) {
            rc.buildRobot(UnitType.SPLASHER, addedDir);
            RobotPlayer.sendTypeMessage = true;
        }
    }

    /**
     * Send message to the robot indicating what type of bot it is
     */
    public static void sendTypeMessage(RobotController rc, int robotType) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(RobotPlayer.spawnDirection);
        System.out.println(rc.canSendMessage(addedDir));
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


    /**
     * Finds spawning direction for a given tower
     */
    public static Direction spawnDirection(RobotController rc) throws GameActionException {
        int height = rc.getMapHeight();
        int width = rc.getMapWidth();
        MapLocation center = new MapLocation(width/2, height/2);
        Direction toCenter = rc.getLocation().directionTo(center);
        if (toCenter.getDeltaX() != 0 && toCenter.getDeltaY() != 0) {
            toCenter = toCenter.rotateLeft();
        }
        return toCenter;
    }
    // message all nearby robots about lastest enemyTile
    public static void broadcastNearbyBots(RobotController rc) throws GameActionException {
        for (RobotInfo bot: rc.senseNearbyRobots()){
            if (rc.canSendMessage(bot.getLocation())){
                rc.sendMessage(bot.getLocation(), MapInfoCodec.encode(RobotPlayer.enemyTile));
            }
        }
    }
}
