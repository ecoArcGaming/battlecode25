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
            RobotInfo msg = RobotInfoCodec.decode(message.getBytes());
            // Do stuff
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
    }

    /**
     * Builds a random robot at a random location
     */
    public static void buildCompletelyRandom(RobotController rc) throws GameActionException {
        double robotType = Constants.rng.nextDouble();
        if (robotType < 0.33) {
            buildAtRandomLocation(rc, UnitType.SOLDIER);
        } else if (robotType < 0.66) {
            buildAtRandomLocation(rc, UnitType.MOPPER);
        } else {
            buildAtRandomLocation(rc, UnitType.SPLASHER);
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
}
