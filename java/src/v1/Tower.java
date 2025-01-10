package v1;

import battlecode.common.*;
import scala.Unit;

import java.util.Random;

public abstract class Tower {
    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };
    static final Random rng = new Random(6147);

    public static void readNewMessages(RobotController rc) throws GameActionException{
        // Looks at all incoming messages
        for (Message message: rc.readMessages(rc.getRoundNum()-1)){
            RobotInfo msg = RobotInfoCodec.decode(message.getBytes());
            // Do stuff
        }
    }
    // Builds a robot of robotType at location if possible
    public static void buildIfPossible(RobotController rc, UnitType robotType, MapLocation location)  throws GameActionException {
        if (rc.canBuildRobot(robotType, location)) {
            rc.buildRobot(robotType, location);
        }
    }
    // Builds a robot of robotType at a random adjacent location
    public static void buildAtRandomLocation(RobotController rc, UnitType robotType) throws GameActionException {
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        buildIfPossible(rc, robotType, nextLoc);
    }
    // Builds a random robot at a random adjacent location
    public static void buildCompletelyRandom(RobotController rc) throws GameActionException {
        double robotType = rng.nextDouble();
        if (robotType < 0.33) {
            buildAtRandomLocation(rc, UnitType.SOLDIER);
        } else if (robotType < 0.66) {
            buildAtRandomLocation(rc, UnitType.MOPPER);
        } else {
            buildAtRandomLocation(rc, UnitType.SPLASHER);
        }

    }
    // Attacks the MapLocation location if possible
    public static void fireAttackIfPossible(RobotController rc, MapLocation location) throws GameActionException {
        if (rc.canAttack(location))  {
            rc.attack(location);
        }
    }

    // Attacks the robot with the lowest HP within vision
    public static void attackLowestRobot(RobotController rc) throws GameActionException {
        fireAttackIfPossible(rc, Sensing.findNearestLowestHP(rc).getLocation());
    }
    // Performs an AOE attack if possible
    public static void aoeAttackIfPossible(RobotController rc) throws GameActionException {
        if (rc.canAttack(null)) {
            rc.attack(null);
        }
    }
}
