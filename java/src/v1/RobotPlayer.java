package v1;

import battlecode.common.*;

import java.util.*;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
/*
FIXME (General issues we noticed)
    - Robots clumping together around towers
    - Robots clumping together around ruins
    - Robots not attacking towers
    - Soldiers behavior when encountering enemy paint very inefficient
    - Can't really fight about against other enemy robots
TODO (Specific issues we noticed)
    - Different types of movement / navigation for robots
    - Splasher behavior (ensure they don't paint over tower patterns)?
    - Upgrade towers when we have a ton of coins
    - Towers are not being built sometimes even when pattern is complete
    - Robots still get stuck (lectureplayer vs v1 on DefaultHuge)
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
    static MapInfo lastTower = null;
    static boolean fillingTower = false;
    static MapInfo enemyTile = null;
    static MapInfo removePaint = null;
    static boolean sendEnemyPaintMsg = false;
    static MapLocation enemySpawn = null;
    static int soldierMsgCooldown = -1;
    static boolean isStuck = false;
    static MapLocation oppositeCorner = null;
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
                    currGrid[mi.getMapLocation().y][mi.getMapLocation().x] = mi;
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
            // upgrade money tower
            if (rc.getType() == UnitType.LEVEL_ONE_MONEY_TOWER){
                rc.upgradeTower(rc.getLocation());
            }
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

        Tower.attackLowestRobot(rc);
        Tower.aoeAttackIfPossible(rc);
    }

    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException{
        if (lastTower == null){
            Soldier.updateLastTower(rc);
        }
        else{
            Soldier.updateLastPaintTower(rc);
        }

        // On round 1, just paint tile it is on
        if (rc.getRoundNum() == 2) {
            Soldier.paintIfPossible(rc, rc.getLocation());
            enemySpawn = new MapLocation(rc.getMapWidth() - rc.getLocation().x,rc.getMapHeight()- rc.getLocation().y);
            return;
        }

        if (rc.getRoundNum() == 3) {
            rc.move(Direction.EAST);
            Soldier.paintIfPossible(rc, rc.getLocation());
            return;
        }

        // If the soldier needs to report a tile, it will call inform tower of paint
        if (enemyTile != null && soldierMsgCooldown == rc.getRoundNum() % 10){
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
            // send msg about enemyPaint every 10 turns if seen
            if (soldierMsgCooldown != -1 && rc.getRoundNum() % 10 == soldierMsgCooldown) {
                System.out.println("Soldier msg cooldown");
                Soldier.informTowerOfEnemyPaint(rc, enemyPaint);
                enemyTile = enemyPaint;
                return;
            } else if (soldierMsgCooldown == -1) {
                soldierMsgCooldown = rc.getRoundNum() % 10;
            }
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
            isStuck = false;
            MapLocation ruinLocation = closestRuin.getMapLocation();
            // If true, the robot will not move
            fillingTower = Sensing.canBuildTower(rc, ruinLocation);

            // TODO: Improve logic for choosing which tower to build?
            // Mark the pattern we need to draw to build a tower here if we haven't already.
            Soldier.markRandomTower(rc, ruinLocation);

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
            if (enemySpawn != null && rc.getRoundNum()  < 15){
                Direction dir = Pathfinding.pathfind(rc, enemySpawn);
                if (dir != null && rc.canMove(dir)){
                    isStuck = false;
                    rc.move(dir);
                    Soldier.paintIfPossible(rc, rc.getLocation());
                    return;
                }
            }
            Direction dir = Pathfinding.exploreUnpainted(rc);
            if (dir != null && rc.canMove(dir)){
                isStuck = false;
                rc.move(dir);
                Soldier.paintIfPossible(rc, rc.getLocation());
                return;
            }
            Direction newDir = Pathfinding.getUnstuck(rc);
            if (newDir != null && rc.canMove(newDir)){
                rc.move(newDir);
            }
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException{

        Mopper.receiveLastMessage(rc);
        Robot.updateLastPaintTower(rc);
        if (Robot.hasLowPaint(rc, 20)) {
            Robot.lowPaintBehavior(rc);
            return;
        }
        // splash assigned tile or move towards it
        if (removePaint != null){
            if (rc.canAttack(removePaint.getMapLocation()) && rc.isActionReady()){
                rc.attack(removePaint.getMapLocation());
                removePaint = null;

            } else if (rc.canAttack(removePaint.getMapLocation())){
                Clock.yield(); // wait for cooldown
            }

            else {
                Direction dir = Pathfinding.pathfind(rc, removePaint.getMapLocation());
                if (rc.canMove(dir)){
                    rc.move(dir);

                }
            }
            isStuck = false;
            return;
        } else { //splash other tiles it sees but avoid overlap
            MapInfo[] all = rc.senseNearbyMapInfos();
            for (int i =0; i < all.length; i++){
                if (i == 8 || i == 15 || i == 17 || i == 23 || i ==27 || i==31 || i == 37 || i == 41 || i == 45 || i == 51 || i == 53 || i == 60){
                    continue;
                } else {
                    if (all[i].getPaint().isEnemy()){
                        removePaint = all[i];
                        Direction dir = Pathfinding.pathfind(rc, removePaint.getMapLocation());
                        if (rc.canMove(dir)){
                            rc.move(dir);
                        }
                        isStuck = false;
                        return;
                    }
                }
            }
        }
        Direction dir = Pathfinding.getUnstuck(rc);
        if (dir != null && rc.canMove(dir)){
            rc.move(dir);
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException{
        // Read all incoming messages
        Mopper.receiveLastMessage(rc);

        if (removePaint != null){
            isStuck = false;
            Mopper.removePaint(rc, removePaint);

        } else {
            // attack adjacent tiles if possible
            for (MapInfo tile: rc.senseNearbyMapInfos(2)) {
                if (tile.getPaint().isEnemy()) {
                    if (rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation());
                    }
                    isStuck = false;
                    return;
                }
            }
            // move towards opponent tiles in vision range
            for (MapInfo tile: rc.senseNearbyMapInfos()){
                if (tile.getPaint().isEnemy()){
                    isStuck = false;
                    Direction dir = Pathfinding.pathfind(rc, tile.getMapLocation());
                    if (dir != null){
                        rc.move(dir);
                        return;
                    }
                }
            }
            // Path to opposite corner if we can't find enemy paint
            Direction exploreDir = Pathfinding.getUnstuck(rc);
            System.out.println(exploreDir);
            if (exploreDir != null && rc.canMove(exploreDir)) {
                rc.move(exploreDir);
            }
        }
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
