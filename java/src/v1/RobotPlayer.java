package v1;

import battlecode.common.*;
import scala.Unit;

import java.util.*;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;
    // Controls whether the soldier is currently filling in a ruin or not
    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

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

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        // You can also use indicators to save debug notes in replays.

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!
            if (turnCount % 100 == 0) {
                rc.resign();
            }
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break; // Consider upgrading examplefuncsplayer to use splashers!
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
        // starting condition
        if (rc.getRoundNum() == 1) {
            // upgrade paint tower
            if (rc.getType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                rc.upgradeTower(rc.getLocation());
            }
            // spawn a soldier bot at the north of the tower
            rc.buildRobot(UnitType.SOLDIER, rc.getLocation().add(Direction.NORTH));
        } else {
            // if not spawning a robot at the beginning spawn a robot
            // Pick a direction to build in
            Direction dir = directions[rng.nextInt(directions.length)];
            MapLocation nextLoc = rc.getLocation().add(dir);
            // Pick a random robot type to build.
//            int robotType = rng.nextInt(3);
            int robotType = 2;
            if (robotType == 0 && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)) {
                rc.buildRobot(UnitType.SOLDIER, nextLoc);
            } else if (robotType == 1 && rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
                rc.buildRobot(UnitType.MOPPER, nextLoc);
            } else if (robotType == 2 && rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
                 rc.buildRobot(UnitType.SPLASHER, nextLoc);
                 System.out.println("BUILT A SPLASHER");
//                rc.setIndicatorString("SPLASHER NOT IMPLEMENTED YET");
            }
        }
        // Read incoming messages
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
        }

        // TODO: can we attack other bots?
    }
    /**
     * Given the MapLocation of a tower, check if that tower pattern has any blocks in the vision of the robot that still
     * needs to be painted, regardless if the tower is currently there or not.
     * Needs to be painted: if it is not already painted
     * Returns True if there is, False if otherwise.
     */
    public static boolean needFilling(RobotController rc, MapLocation towerLocation) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(towerLocation, 8)){
            if (patternTile.getPaint() == PaintType.EMPTY && !patternTile.hasRuin()){
                return true;
            }
        }
        return false;
    }

    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException{
        // TODO: What if we run out of paint?
        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        MapLocation curLocation = rc.getLocation();

        // Search for a nearby ruin to complete.
        MapInfo curRuin = null;
        int minDis = -1;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin() && needFilling(rc, tile.getMapLocation())) {
                int ruinDistance = curLocation.distanceSquaredTo(tile.getMapLocation());
                if (minDis == -1 || minDis > ruinDistance) {
                    curRuin = tile;
                    minDis = ruinDistance;
                }
            }
        }
        if (curRuin != null){
            // Fill in a ruin!
            MapLocation targetLoc = curRuin.getMapLocation();
            Direction ruinDir = curLocation.directionTo(targetLoc);

            // Mark the pattern we need to draw to build a tower here if we haven't already.
            MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(ruinDir);
            if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
                rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
            }

            // Fill in any spots in the pattern with the appropriate paint.
            // Prioritize the tile under our own feet
            MapInfo currentTile = rc.senseMapInfo(curLocation);
            if (!currentTile.getPaint().isAlly() && rc.canAttack(curLocation)){
                rc.attack(curLocation);
            }
            for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
                if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY
                && patternTile.getPaint() == PaintType.EMPTY){
                    boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(patternTile.getMapLocation())) {
                        rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                    }
                }
            }
            // Complete the ruin if we can.
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
            }
            // Move clockwise around the ruin
            // TODO: what if we encounter opponent paint?
            Direction moveDir = ruinDir.rotateRight();
            if (rc.canMove(moveDir)) {
                rc.move(moveDir);
            }
        } else {
            // Find all unpainted nearby locations
            MapInfo[] adjacentTiles = rc.senseNearbyMapInfos(2);
            List<MapInfo> validAdjacent = new ArrayList<>();
            for (MapInfo adjacentTile: adjacentTiles){
                if (adjacentTile.getPaint() == PaintType.EMPTY && adjacentTile.isPassable()) {
                    validAdjacent.add(adjacentTile);
                }
            }

            // TODO: Make movement smarter by using all information in vision range
            // Uniformly and randomly choose an unpainted location to go to
            // If all adjacent tiles are painted, then randomly walk in a direction
            if (!validAdjacent.isEmpty()){
                MapInfo nextLoc = validAdjacent.get(rng.nextInt(validAdjacent.size()));
                Direction nextDir = curLocation.directionTo(nextLoc.getMapLocation());
                if (rc.canMove(nextDir)) {
                    rc.move(nextDir);
                }
            } else {
                Direction dir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(dir)){
                    rc.move(dir);
                }
            }
            // Try to paint beneath us as we walk to avoid paint penalties.
            // Avoiding wasting paint by re-painting our own tiles.
            MapInfo currentTile = rc.senseMapInfo(curLocation);
            if (!currentTile.getPaint().isAlly() && rc.canAttack(curLocation)) {
                rc.attack(curLocation);
            }
        }
    }


    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */

    public static void runSplasher(RobotController rc) throws GameActionException{
        rc.move(Pathfind(rc, new MapLocation(0, 0)));
        Clock.yield();
    }
    public static void runMopper(RobotController rc) throws GameActionException{
        // Move and attack randomly.
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (rc.canMove(dir)){
            rc.move(dir);
        }
        if (rc.canMopSwing(dir)){
            rc.mopSwing(dir);
        }
        else if (rc.canAttack(nextLoc)){
            rc.attack(nextLoc);
        }
        // We can also move our code into different methods or classes to better organize it!
        updateEnemyRobots(rc);
    }
    public static Direction Pathfind(RobotController rc, MapLocation target) throws GameActionException{
        // return a direction from curr to target


//        int horizontal = curr.x - target.x;
//        int vert = curr.y - target.y;
//        if (Math.abs(horizontal) > Math.abs(vert)){
//            if (horizontal > 0){
//                if (rc.canMove(Direction.WEST) && rc.senseMapInfo(curr.add(Direction.WEST)).getPaint().isAlly()) {
//                    return Direction.WEST;
//                }
//            } else {
//                if (rc.canMove(Direction.EAST) && rc.senseMapInfo(curr.add(Direction.EAST)).getPaint().isAlly()) {
//                    return Direction.EAST;
//                }
//            }
//        }
//
//       if (vert > 0){
//           if (rc.canMove(Direction.SOUTH) && rc.senseMapInfo(curr.add(Direction.SOUTH)).getPaint().isAlly()) {
//               return Direction.SOUTH;
//           }
//       } else {
//           if (rc.canMove(Direction.NORTH) && rc.senseMapInfo(curr.add(Direction.NORTH)).getPaint().isAlly()) {
//               return Direction.NORTH;
//           }
//       }
        Direction currDir = rc.getLocation().directionTo(target);
        Direction left = currDir.rotateLeft();
        Direction right = currDir.rotateRight();
        System.out.println("currDir: " + currDir + " left: " + left + " right: " + right);
        if (rc.canMove(currDir)){
            if (rc.senseMapInfo(rc.getLocation().add(currDir)).getPaint().isAlly()) {
                return currDir;
            } else {
                if (rc.canMove(left) && rc.senseMapInfo(rc.getLocation().add(left)).getPaint().isAlly()) {
                    return left;
                } else if (rc.canMove(right) && rc.senseMapInfo(rc.getLocation().add(right)).getPaint().isAlly()) {
                    return right;
                }
            }
        }

        Direction[] allDirections = Direction.allDirections();
        for (Direction dir: allDirections){
            if (rc.canMove(dir)){
                if (rc.senseMapInfo(rc.getLocation().add(dir)).getPaint().isAlly()) {
                    return dir;
                }
            }
        }

        for (Direction dir: allDirections){
            if (rc.canMove(dir)) {
                return dir;
            }
        }

        return null;
    }
    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        // Sensing methods can be passed in a radius of -1 to automatically 
        // use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            // Save an array of locations with enemy robots in them for possible future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            // Occasionally try to tell nearby allies how many enemy robots we see.
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }
}
