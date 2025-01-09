package v1;

import battlecode.common.*;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
// TODO: organize our code so its not spaghetti
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;
    static MapInfo[][] currGrid;
    static ArrayList<MapLocation> last8 = new ArrayList<MapLocation>(); // Acts as queue
    static MapInfo lastTower = null;
    static RobotInfo lastEnemy = null;
    static boolean fillingTower = false;
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

        currGrid = new MapInfo[rc.getMapHeight()][rc.getMapWidth()];


        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            System.out.println("IM ALIVE");
            turnCount += 1;  // We have now been alive for one more turn!
            if (turnCount % 1000 == 0) {
                rc.resign();
            }
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!

                // Updates the grid each robot can see
                for (MapInfo mi : rc.senseNearbyMapInfos()) {
                    currGrid[mi.getMapLocation().x][mi.getMapLocation().y] = mi;
                }

                switch (rc.getType()) {
                    case SOLDIER:
                        runSoldier(rc);
                        break;
                    case MOPPER:
                        runMopper(rc);
                        break;
                    case SPLASHER:
                        runSplasher(rc);
                        break; // Consider upgrading examplefuncsplayer to use splashers!
                    default:
                        runTower(rc);
                        break;
                }

                // Update the last eight locations list
                if (last8.size() < 8) {
                    last8.add(rc.getLocation());
                } else {
                    last8.removeFirst();
                    last8.add(rc.getLocation());
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
     * Placeholder function for attacking an enemy robot
     */
    public static void attack(RobotController rc, MapLocation target) {
        return;
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
        // Looks at all incoming messages
        for (Message message: rc.readMessages(rc.getRoundNum()-1)){
            RobotInfo msg = RobotInfoCodec.decode(message.getBytes());
            // If message from same team, then transfer paint
            if (msg.team == rc.getTeam()) {
                System.out.println(rc.canTransferPaint(msg.getLocation(), 1));
                System.out.println("hi");
            }
            // Otherwise, alert nearby robots of an enemy attack
            else{
                attack(rc, msg.getLocation());
            }
        }
        // Encode info of tower to send to all nearby robots
        int encodedInfo = RobotInfoCodec.encode(rc.senseRobot(rc.getID()));
        int count = 0;
        for (RobotInfo robot: rc.senseNearbyRobots()){
            if (count >= 20){
                break;
            }
            if (rc.canSendMessage(robot.location, encodedInfo)) {
                rc.sendMessage(robot.location, encodedInfo);
                count++;
            }
        }
        // starting condition
        if (rc.getRoundNum() == 1) {
            // spawn a soldier bot at the north of the tower
            rc.buildRobot(UnitType.SOLDIER, rc.getLocation().add(Direction.NORTH));
        } else {
            // if not spawning a robot at the beginning spawn a robot
            // Pick a direction to build in
            Direction dir = directions[rng.nextInt(directions.length)];
            MapLocation nextLoc = rc.getLocation().add(dir);
            // Pick a random robot type to build.
            double robotType = rng.nextDouble();
            if (robotType < 1 && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)) {
                rc.buildRobot(UnitType.SOLDIER, nextLoc);
            } else if (robotType == 11 && rc.canBuildRobot(UnitType.MOPPER, nextLoc)) {
                rc.buildRobot(UnitType.MOPPER, nextLoc);
            } else if (robotType >= 1 && rc.canBuildRobot(UnitType.SPLASHER, nextLoc)) {
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
        // TODO: tower attack a robot in range that has the lowest hp
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
        MapLocation targetRobot = null;
        int minHealth = -1;
        for (RobotInfo robot: nearbyRobots) {
            int robotHealth = robot.getHealth();
            if (minHealth == -1 || minHealth > robotHealth) {
                targetRobot = robot.getLocation();
                minHealth = robotHealth;
            }
        }
        if (minHealth != -1 && rc.canAttack(targetRobot)) {
            rc.attack(targetRobot);
        }
        rc.attack(null);
    }
    /**
     * Given the MapLocation of a tower, check if that tower pattern has any blocks in the vision of the robot that still
     * needs to be painted, or if the tower is not there
     * Needs to be painted: not already painted or incorrect ally paint (doesn't match marker/mark does not exist)
     * Returns True if there are blocks that can be painted to still be painted or if no tower, False if otherwise.
     */
    public static boolean needFilling(RobotController rc, MapLocation towerLocation) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(towerLocation, 8)){
            if (patternTile.hasRuin()) {
                if (!rc.canSenseRobotAtLocation(patternTile.getMapLocation())) {
                    return true;
                }
            } else if ((patternTile.getPaint() == PaintType.EMPTY ||
                    patternTile.getPaint().isAlly() && patternTile.getMark() != patternTile.getPaint())){
                return true;
            }
        }
        return false;
    }
    /**
     * Given the MapLocation of a ruin, check if the pattern is correct for a tower to be built and if there is no
     * tower there currently
     * Returns False if the pattern is incorrect, there are no markers, or if there is a tower already existing
     */
    public static boolean canBuildTower(RobotController rc, MapLocation towerLocation) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(towerLocation, 8)){
            if (patternTile.hasRuin()) {
                if (rc.canSenseRobotAtLocation(patternTile.getMapLocation())) {
                    return false;
                }
            } else if ((patternTile.getMark() == PaintType.EMPTY || patternTile.getMark() != patternTile.getPaint())){
                return false;
            }
        }
        return true;
    }

    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */

    public static boolean checkTower(RobotController rc, MapInfo loc){
        if (loc.hasRuin() && rc.canSenseRobotAtLocation(loc.getMapLocation())){
            return true;
        } else {
            return false;
        }
    }

    public static void updateLastTower(RobotController rc){
        for (MapInfo loc: rc.senseNearbyMapInfos()) {
            if (checkTower(rc, loc)) {
                lastTower = loc;
            }
        }
    }

    public static Direction returnToTower(RobotController rc) throws GameActionException{
        for (MapInfo loc: rc.senseNearbyMapInfos()){
            if(checkTower(rc, loc)){
                return Pathfind(rc, loc.getMapLocation());
            }
        }
        return Pathfind(rc, lastTower.getMapLocation());
    }
    public static void runSoldier(RobotController rc) throws GameActionException{
        updateLastTower(rc);

        if (rc.getPaint() < 20){
            Direction dir = returnToTower(rc);
            if (dir != null){
                rc.move(dir);
            }
        }

        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        MapLocation startLocation = rc.getLocation();

        fillingTower = false;

        // Search for the closest ruin to complete.
        MapInfo curRuin = null;
        int minDis = -1;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation tileLocation = tile.getMapLocation();
                if (needFilling(rc, tileLocation)) {
                    // Check distance among ruins that need filling
                    int ruinDistance = startLocation.distanceSquaredTo(tileLocation);
                    if (minDis == -1 || minDis > ruinDistance) {
                        curRuin = tile;
                        minDis = ruinDistance;
                    }
                } else {
                    // If ruin does not need filling, check if we can build a tower there
                    if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, tileLocation)) {
                        rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, tileLocation);
                        rc.setTimelineMarker("Tower built", 0, 255, 0);
                    }
                    if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, tileLocation)) {
                        rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, tileLocation);
                        rc.setTimelineMarker("Tower built", 0, 255, 0);
                    }
                }
                if (canBuildTower(rc, tileLocation)){
                    fillingTower = true;
                }
            }
        }
        if (curRuin != null){
            // Fill in a ruin!
            MapLocation targetLoc = curRuin.getMapLocation();
            Direction ruinDir = startLocation.directionTo(targetLoc);

            // TODO: Improve logic for choosing which tower to build?
            // Mark the pattern we need to draw to build a tower here if we haven't already.
            UnitType towerType = ((rng.nextDouble() < 0.5) ? UnitType.LEVEL_ONE_MONEY_TOWER : UnitType.LEVEL_ONE_PAINT_TOWER);
            MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(ruinDir);
            if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY &&
                    rc.canMarkTowerPattern(towerType, targetLoc)){
                rc.markTowerPattern(towerType, targetLoc);
            }
            // Move towards the ruin
            // TODO: Change to use pathfinding function
            // TODO: What happens if we run into enemy bots/paint while filling?
            Direction moveDir = ruinDir;
            // Rotation for clockwise movement around tower
            if (minDis <= 2) {
                moveDir = moveDir.rotateRight();
            }
            if (rc.canMove(moveDir)) {
                rc.move(moveDir);
            }

            // Fill in any spots in the pattern with the appropriate paint.
            // Prioritize the tile under our own feet
            MapLocation newLocation = rc.getLocation();
            MapInfo currentTile = rc.senseMapInfo(newLocation);
            if (currentTile.getMark() == PaintType.EMPTY && currentTile.getPaint() == PaintType.EMPTY
                    && rc.canAttack(newLocation)) {
                rc.attack(newLocation);
            } else if ((currentTile.getPaint().isAlly() || currentTile.getPaint() == PaintType.EMPTY)
                    && currentTile.getMark() != currentTile.getPaint()
                    && rc.canAttack(newLocation)){
                boolean useSecondaryColor = currentTile.getMark() == PaintType.ALLY_SECONDARY;
                rc.attack(newLocation, useSecondaryColor);
            }
            // Tiles not under our own feet
            for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
                if (patternTile.getMark() != patternTile.getPaint() &&
                        (patternTile.getPaint().isAlly() || patternTile.getPaint() == PaintType.EMPTY)) {
                    boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(patternTile.getMapLocation())) {
                        rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                    }
                }
            }
            // Complete the ruin if we can.
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
            }
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)) {
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                rc.setTimelineMarker("Tower built", 0, 255, 0);
            }
            if (canBuildTower(rc, targetLoc)){
                fillingTower = true;
            }
        } else if (!fillingTower){
            // Find all unpainted nearby locations
            MapInfo[] adjacentTiles = rc.senseNearbyMapInfos(2);
            List<MapInfo> validAdjacent = new ArrayList<>();
            for (MapInfo adjacentTile: adjacentTiles){
                if (adjacentTile.getPaint() == PaintType.EMPTY && adjacentTile.isPassable()) {
                    validAdjacent.add(adjacentTile);
                }
            }

            // TODO: Make movement smarter by using all information in vision range (which tiles painted, walls, map edges)
            // Uniformly and randomly choose an unpainted location to go to
            // If all adjacent tiles are painted, then randomly walk in a direction
            if (!validAdjacent.isEmpty()){
                MapInfo nextLoc = validAdjacent.get(rng.nextInt(validAdjacent.size()));
                Direction moveDir = startLocation.directionTo(nextLoc.getMapLocation());
                if (rc.canMove(moveDir)) {
                    rc.move(moveDir);
                }
            } else {
                Direction moveDir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(moveDir)){
                    rc.move(moveDir);
                }
            }
            // Try to paint beneath us as we walk to avoid paint penalties.
            // Avoiding wasting paint by re-painting our own tiles.
            MapLocation newLocation = rc.getLocation();
            MapInfo currentTile = rc.senseMapInfo(newLocation);
            if (!currentTile.getPaint().isAlly() && rc.canAttack(newLocation)) {
                rc.attack(newLocation);
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
        if (rc.canMove(currDir) && rc.senseMapInfo(rc.getLocation().add(currDir)).getPaint().isAlly()) {
            return currDir;
        }
        else if (rc.canMove(left) && rc.senseMapInfo(rc.getLocation().add(left)).getPaint().isAlly()){
            return left;
        }
        else if (rc.canMove(right) && rc.senseMapInfo(rc.getLocation().add(right)).getPaint().isAlly()) {
            return right;
        }

        Direction[] allDirections = Direction.allDirections();
        for (Direction dir: allDirections){
            if (rc.canMove(dir)){
                if (rc.senseMapInfo(rc.getLocation().add(dir)).getPaint().isAlly() && !last8.contains(rc.getLocation().add(currDir))) {
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
