package v1;

import battlecode.common.*;

import java.awt.*;
import java.nio.file.Path;
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
    static MapInfo[][] currGrid;
    static ArrayList<MapLocation> last8 = new ArrayList<MapLocation>(); // Acts as queue
    static MapInfo lastTower = null; // TODO: Somehow navigate back to paint towers and not money towers
    static boolean fillingTower = false;
    static MapInfo enemyTile = null;
    static RobotInfo lastEnemy = null;
    static boolean botNotSent = false;
    static MapInfo removePaint = null;
    static boolean sendEnemyPaintMsg = false;

    // Controls whether the soldier is currently filling in a ruin or not
    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);
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
            turnCount += 1;  // We have now been alive for one more turn!
            if (turnCount == Constants.RESIGN_AFTER) {
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
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
        Tower.readNewMessages(rc);
        // starting condition
        if (rc.getRoundNum() == 1) {
            // spawn a soldier bot at the north of the tower
            Tower.buildIfPossible(rc, UnitType.SOLDIER, rc.getLocation().add(Direction.NORTH));
        } else {
            // TODO: Figure out tower spawning logic (when to spawn, what to spawn)
            if (rc.getMoney() > 1500) {
                Tower.buildCompletelyRandom(rc);
            }
            if (sendEnemyPaintMsg && removePaint != null) {
                Communication.sendMapInformation(rc, removePaint, rc.getLocation().add(Direction.NORTHEAST));
                sendEnemyPaintMsg = false;
            }
        }

        // TODO: make sure this works: tower attack a robot in range that has the lowest hp
        Tower.attackLowestRobot(rc);
        Tower.aoeAttackIfPossible(rc);
    }

    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException{
        Soldier.updateLastTower(rc);

        // On round 1, just paint tile it is on
        if (rc.getRoundNum() == 2) {
            Soldier.paintIfPossible(rc, rc.getLocation());
            return;
        }

        if (rc.getRoundNum() == 3) {
            rc.move(Direction.EAST);
            Soldier.paintIfPossible(rc, rc.getLocation());
            return;
        }

        // If the soldier needs to report a tile, it will call inform tower of paint
        if (enemyTile != null){
            Soldier.informTowerOfEnemyPaint(rc, enemyTile);
            return;
        }

        // If the soldier has low paint, perform low paint behavior
        if (Soldier.hasLowPaint(rc, 20)) {
            Soldier.lowPaintBehavior(rc);
            return;
        }

        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Find all Enemy Tiles
        MapInfo enemyPaint = Sensing.findEnemyPaint(rc, nearbyTiles);
        if (enemyPaint != null) {
            Soldier.informTowerOfEnemyPaint(rc, enemyPaint);
            enemyTile = enemyPaint;
            return;
        }


        // Finds closest ruin
        MapLocation startLocation = rc.getLocation();
        MapInfo closestRuin = Sensing.findClosestRuin(rc, startLocation, nearbyTiles);
        if (closestRuin != null) {
            fillingTower = Sensing.canBuildTower(rc, closestRuin.getMapLocation());
        } else {
            fillingTower = false;
        }

        if (closestRuin != null){
            MapLocation ruinLocation = closestRuin.getMapLocation();
            // If true, the robot will not move
            fillingTower = Sensing.canBuildTower(rc, ruinLocation);

            // TODO: Improve logic for choosing which tower to build?
            // Mark the pattern we need to draw to build a tower here if we haven't already.
            Soldier.markRandomTower(rc, ruinLocation);

            // TODO: What happens if we run into enemy bots/paint while filling?
            // Move towards the ruin
            // NOTE: PATHFIND AUTOMATICALLY HANDLES ROTATION AROUND THE RUIN BC OF THE WAY IT WORKS
            Direction moveDir = Pathfinding.pathfind(rc, ruinLocation);
            if (moveDir != null) {
                rc.move(moveDir);}

            // Fill in any spots in the pattern with the appropriate paint.
            // Prioritize the tile under our own feet
            MapLocation newLocation = rc.getLocation();
            MapInfo currentTile = rc.senseMapInfo(newLocation);
            Soldier.paintIfPossible(rc, currentTile);
            MapInfo tileToPaint = Sensing.findPaintableTile(rc, ruinLocation,8);
            if (tileToPaint != null) {
                Soldier.paintIfPossible(rc, tileToPaint);
            }
            Soldier.completeRuinIfPossible(rc, ruinLocation);
        } else if (!fillingTower){
            // TODO: Improve exploration behavior: use all information in vision to choose where to move next
            Direction exploreDir = Pathfinding.exploreUnpainted(rc);
            if (exploreDir != null) {rc.move(exploreDir);}
            Soldier.paintIfPossible(rc, rc.getLocation());
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException{
        rc.move(Pathfinding.pathfind(rc, new MapLocation(0, 0)));
    }

    public static void runMopper(RobotController rc) throws GameActionException{
        // Read all incoming messages
        Mopper.receiveLastMessage(rc);

        if (removePaint != null){
            Mopper.removePaint(rc, removePaint);
        }

        // We can also move our code into different methods or classes to better organize it!
        updateEnemyRobots(rc);
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
