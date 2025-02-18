package v3;

import battlecode.common.*;

import static v3.RobotPlayer.*;

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
            }
            else{
                MapInfo msg = MapInfoCodec.decode(bytes);
                // Check if message is enemy tower
                if (msg.hasRuin()){
                    roundsWithoutEnemy = 0;
                    // If tower receives enemy message from robots, broadcast the information to other
                    // towers. Additionally, spawn a splasher and a mopper
                    if (Sensing.isRobot(rc, message.getSenderID())){
                        RobotPlayer.broadcast = true;
                        RobotPlayer.alertAttackSoldiers = true;
                        spawnQueue.add(3); //  Spawns a mopper
                        spawnQueue.add(4); //  Spawns a splasher
                        RobotPlayer.numEnemyVisits += 1; //   Increases probability of spawning a splasher
                    }

                    // If tower receives message from tower, just alert the surrounding bots to target the enemy
                    // paint
                    RobotPlayer.alertRobots = true;

                    // Update enemy tile regardless
                    RobotPlayer.enemyTarget = msg;
                    enemyTower = msg;
                }
                // Check if message is enemy paint
                else if (msg.getPaint().isEnemy()){
                    roundsWithoutEnemy = 0;
                    // If tower receives enemy message from robots, broadcast the information to other
                    // towers. Additionally, spawn a splasher and a mopper
                    if (Sensing.isRobot(rc, message.getSenderID())){
                        broadcast = true;
                        if (Constants.rng.nextDouble() <= 0.5) {
                            spawnQueue.add(4); //  Spawns a splasher
                        } else {
                            spawnQueue.add(3); //  Spawns a mopper
                        }
                        numEnemyVisits += 1; //   Increases probability of spawning a splasher
                    }

                    // If tower receives message from tower, just alert the surrounding bots to target the enemy
                    // paint
                    alertRobots = true;


                    // Update enemy tile regardless
                   enemyTarget = msg;
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
     * Builds an advance/develop soldier, weighted by how long it has been since the tower last saw a robot
     */
    public static void addRandomToQueue(RobotController rc) throws GameActionException {
        if (Constants.rng.nextDouble() < numEnemyVisits*0.2
                || (numSoldiersSpawned > Constants.SPLASHER_CUTOFF && Constants.rng.nextDouble() < Constants.SPLASHER_SOLDIER_SPLIT)) {
            spawnQueue.add(4);
            numEnemyVisits = 0;
        } else {
            numSoldiersSpawned++;
            // odds of explore robot increases linearly from 30-70 to 60-40
            if (Constants.rng.nextDouble() <
                    Math.min((roundsWithoutEnemy+Constants.INIT_PROBABILITY_DEVELOP) / Constants.DEVELOP_BOT_PROB_SCALING,
                            Constants.DEVELOP_BOT_PROBABILITY_CAP)){
                spawnQueue.add(0);
            }
            else{
                spawnQueue.add(1);
            }
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
        MapLocation addedDir = rc.getLocation().add(spawnDirection);
        if (startSquareCovered(rc)){
            if (rc.canBuildRobot(UnitType.MOPPER, addedDir)) {
                rc.buildRobot(UnitType.MOPPER, addedDir);
                return;
            }
        }
        if (rc.canBuildRobot(UnitType.SOLDIER, addedDir)) {
            rc.buildRobot(UnitType.SOLDIER, addedDir);
            sendTypeMessage = true;
        }
    }

    /**
     * Creates a mopper at location NORTH if possible
     */
    public static void createMopper(RobotController rc) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(spawnDirection);
        if (rc.canBuildRobot(UnitType.MOPPER, addedDir)) {
            rc.buildRobot(UnitType.MOPPER, addedDir);
            sendTypeMessage = true;
        }
    }

    /**
     * Creates a splasher at the north
     */
    public static void createSplasher(RobotController rc) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(spawnDirection);
        if (rc.canBuildRobot(UnitType.SPLASHER, addedDir)) {
            rc.buildRobot(UnitType.SPLASHER, addedDir);
            sendTypeMessage = true;
        }
    }

    /**
     * Send message to the robot indicating what type of bot it is
     */
    public static void sendTypeMessage(RobotController rc, int robotType) throws GameActionException {
        MapLocation addedDir = rc.getLocation().add(spawnDirection);
        if (rc.canSenseRobotAtLocation(addedDir) && rc.canSendMessage(addedDir)){
            rc.sendMessage(addedDir, robotType);
            // If robot is an attack soldier or mopper, send enemy tile location as well
            if (robotType == 4 || robotType == 3 || robotType == 2) {
                Communication.sendMapInformation(rc, enemyTarget, addedDir);
            }
        }
        sendTypeMessage = false;
        spawnQueue.removeFirst();
    }

    /**
     * Checks to see if that spawning square is covered with enemy paint
     */
    public static boolean startSquareCovered(RobotController rc) throws GameActionException {
        return rc.senseMapInfo(rc.getLocation().add(spawnDirection)).getPaint().isEnemy();
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

    /**
     *     message all nearby robots about lastest enemyTile
     */
    public static void broadcastNearbyBots(RobotController rc) throws GameActionException {
        for (RobotInfo bot: rc.senseNearbyRobots()){
            // Only sends messages to moppers and splashers
            if (rc.canSendMessage(bot.getLocation()) && isAttackType(rc, bot)){
                rc.sendMessage(bot.getLocation(), MapInfoCodec.encode(RobotPlayer.enemyTarget));
            }
        }
        alertRobots = false;
        alertAttackSoldiers = false;
    }

    /**
     *     message all nearby robots about latest enemyTower
     */
    public static void broadcastEnemyTower(RobotController rc) throws GameActionException {
        for (RobotInfo bot: rc.senseNearbyRobots()){
            // Only sends messages to moppers and splashers
            if (rc.canSendMessage(bot.getLocation())){
                rc.sendMessage(bot.getLocation(), MapInfoCodec.encode(RobotPlayer.enemyTower));
            }
        }
    }

    public static boolean isAttackType(RobotController rc, RobotInfo bot) throws GameActionException {
        return bot.getType() == UnitType.MOPPER || bot.getType() == UnitType.SPLASHER || (bot.getType() == UnitType.SOLDIER && alertAttackSoldiers);
    }


    public static int countEnemyPaint(RobotController rc) throws GameActionException {
        int out = 0;
        for (MapInfo map: rc.senseNearbyMapInfos()){
            if (map.getPaint().isEnemy()){
                out ++;
            }

        }
        return out;
    }
}
