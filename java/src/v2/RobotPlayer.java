package v2;

import battlecode.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
/*
FIXME (General issues we noticed)
    - Could optimize SRPs a bit more
    - If towers get destroyed, robots don't know this and keep trying to get paint from the ruin
    - Take better advantage of defense towers
    - Mopper mop swings
    - Clumped robots is a bit problematic
    - Exploration around walls is ass(?)
    - Differential behavior given map size
    - Improve splasher survivability
TODO (Specific issues we noticed that currently have a solution)
    - Robots wait for paint around money towers
    - Soldier attack micro: move in, attack, attack, move out allows soldier to attack
    - Don't use markers when painting
    - Fix splasher functionality where it won't splash on ally paint for a ruin
    - Fix exploration for soldiers so that when a mopper goes and takes over area, the soldier can come and
        finish the ruin pattern
    - Low health behavior to improve survivability
    - Can we move all the constants into the Constants class :D (things like thresholds to do certain actions)
    - pathfind doesn't make soldiers stay on allied paint, but paintedPathfind can cause robots to get stuck
    - Handle the 25 tower limit
 */
    
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    // Initialization Variables
    static int turnCount = 0;
    static MapInfo[][] currGrid;
    static ArrayList<MapLocation> last8 = new ArrayList<MapLocation>(); // Acts as queue
    static MapInfo lastTower = null;
    static SoldierType soldierType = SoldierType.ADVANCE;

    // Pathfinding Variable
    static int stuckTurnCount = 0;
    static int closestPath = -1;

    // Soldier state variables
    static SoldierState soldierState = SoldierState.EXPLORING;
    static SoldierState storedState = SoldierState.EXPLORING;

    static MapInfo fillEmpty = null;
    static int soldierMsgCooldown = -1;

    // Key Soldier Location variables
    static MapInfo enemyTile = null; // location of an enemy paint/tower for a develop/advance robot to report
    static MapLocation ruinToFill = null; // location of a ruin that the soldier is filling in
    static MapLocation wanderTarget = null; // target for advance robot to pathfind towards during exploration
    static MapInfo enemyTower = null; // location of enemy tower for attack soldiers to pathfind to

    // Enemy Info variables
    static MapInfo enemyTarget = null; // location of enemy tower/tile for tower to tell
    static MapInfo removePaint = null;

    // Tower Spawning Variables
    static ArrayList<Integer> spawnQueue = new ArrayList<>();
    static boolean sendTypeMessage = false;
    static Direction spawnDirection = null;
    static int numEnemyVisits = 0;
    static int roundsWithoutEnemy = 0;

    // Navigation Variables
    static MapLocation oppositeCorner = null;
    static boolean seenPaintTower = false;
    static int botRoundNum = 0;

    // Towers Broadcasting Variables
    static boolean broadcast = false;
    static boolean alertRobots = false;
    static boolean alertAttackSoldiers = false;

    // Bug 1 Variables
    static boolean isTracing = false;
    static int smallestDistance = 10000000;
    static MapLocation closestLocation = null;
    static Direction tracingDir = null;

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

                botRoundNum += 1;
                if (soldierMsgCooldown != -1) {
                    soldierMsgCooldown--;
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
                        break;
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
        // Sets spawn direction of each tower when created
        if (spawnDirection == null){
            spawnDirection = Tower.spawnDirection(rc);
        }

        roundsWithoutEnemy += 1; //  Update rounds without enemy
        if (roundsWithoutEnemy == 50){
            roundsWithoutEnemy += Constants.INIT_PROBABILITY_DEVELOP*100;
        }
        Tower.readNewMessages(rc);

        // starting condition
        if (rc.getRoundNum() == 1) {
            // spawn a soldier bot
            Tower.createSoldier(rc);
            spawnQueue.add(1);
        } else {
            if (broadcast){
                rc.broadcastMessage(MapInfoCodec.encode(enemyTarget));
            }

            // If unit has been spawned and communication hasn't happened yet
            if (sendTypeMessage) {
                Tower.sendTypeMessage(rc, spawnQueue.getFirst());
            }

            // Otherwise, if the spawn queue isn't empty, spawn the required unit
            else if (!spawnQueue.isEmpty() && rc.getMoney() > 400 && rc.getPaint() > 300){
                switch (spawnQueue.getFirst()){
                    case 0, 1, 2: Tower.createSoldier(rc); break;
                    case 3: Tower.createMopper(rc); break;
                    case 4: Tower.createSplasher(rc); break;
                }
            }

            else if (rc.getMoney() > 1200 && rc.getPaint() > 400) {
                Tower.buildCompletelyRandom(rc);
            }
        }
        if (enemyTarget != null && alertRobots) {
            Tower.broadcastNearbyBots(rc);
        }

        if (rc.getType() == UnitType.LEVEL_ONE_PAINT_TOWER && rc.getMoney() > 5000) {
            rc.upgradeTower(rc.getLocation());
        }
        if (rc.getType() == UnitType.LEVEL_ONE_MONEY_TOWER && rc.getMoney() > 7500) {
            rc.upgradeTower(rc.getLocation());
        }
        if (rc.getType() == UnitType.LEVEL_TWO_PAINT_TOWER && rc.getMoney() > 7500) {
            rc.upgradeTower(rc.getLocation());
        }
        if (rc.getType() == UnitType.LEVEL_TWO_MONEY_TOWER && rc.getMoney() > 10000) {
            rc.upgradeTower(rc.getLocation());
        }
        Tower.attackLowestRobot(rc);
        Tower.aoeAttackIfPossible(rc);
    }

    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException {
        // Update locations of last known towers
        if (lastTower == null) {
            Soldier.updateLastTower(rc);
        } else {
            Soldier.updateLastPaintTower(rc);
        }
        // Read incoming messages
        Soldier.readNewMessages(rc);
      
        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Get current location
        MapLocation initLocation = rc.getLocation();

        // On round 1, just paint tile it is on
        if (botRoundNum == 1) {
            Soldier.paintIfPossible(rc, rc.getLocation());
            if (rc.getRoundNum() < 15) {
                wanderTarget = new MapLocation(rc.getMapWidth() - rc.getLocation().x, rc.getMapHeight() - rc.getLocation().y);
            }
            return;
        }

        switch (soldierType) {
            case null:
                rc.setIndicatorDot(rc.getLocation(), 255, 255, 255);
                return;
            case SoldierType.DEVELOP: {
                Soldier.updateState(rc, initLocation, nearbyTiles);
                Helper.tryCompleteResourcePattern(rc);

                switch (soldierState) {
                    case SoldierState.LOWONPAINT: {
                        rc.setIndicatorString("LOWONPAINT");
                        Soldier.lowPaintBehavior(rc);
                        break;
                    }
                    case SoldierState.DELIVERINGMESSAGE: {
                        rc.setIndicatorString("DELIVERINGMESSAGE");
                        Soldier.msgTower(rc);
                        break;
                    }
                    case SoldierState.FILLINGTOWER: {
                        rc.setIndicatorString("FILLINGTOWER");
                        Soldier.fillInRuin(rc, ruinToFill);
                        break;
                    }
                    case SoldierState.EXPLORING: {
                        rc.setIndicatorString("EXPLORING");
                        Direction dir = Pathfinding.exploreUnpainted(rc);
                        if (dir != null) {
                            rc.move(dir);
                            Soldier.paintIfPossible(rc, rc.getLocation());
                        } else if (rc.getMovementCooldownTurns() < 10){
                            soldierState = SoldierState.STUCK;
                            Soldier.resetVariables();
                        }
                        break;
                    }
                    case SoldierState.STUCK: {
                        rc.setIndicatorString("STUCK");
                        Soldier.stuckBehavior(rc);
                    }
                }
                rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
                return;
            }

            case SoldierType.ADVANCE: {
                Soldier.updateState(rc, initLocation, nearbyTiles);

                switch (soldierState) {
                    case SoldierState.LOWONPAINT: {
                        rc.setIndicatorString("LOWONPAINT");
                        Soldier.lowPaintBehavior(rc);
                        break;
                    }
                    case SoldierState.DELIVERINGMESSAGE: {
                        rc.setIndicatorString("DELIVERINGMESSAGE");
                        Soldier.msgTower(rc);
                        break;
                    }
                    case SoldierState.FILLINGTOWER: {
                        rc.setIndicatorString("FILLINGTOWER");
                        Soldier.fillInRuin(rc, ruinToFill);
                        break;
                    }
                    case SoldierState.EXPLORING: {
                        rc.setIndicatorString("EXPLORING");
                        if (wanderTarget != null) {
                            Direction dir = Pathfinding.pathfind(rc, wanderTarget);
                            if (dir != null) {
                                rc.move(dir);
                                Soldier.paintIfPossible(rc, rc.getLocation());
                            }
                        } else {
                            soldierState = SoldierState.STUCK;
                            Soldier.resetVariables();
                        }
                        break;
                    }
                    case SoldierState.STUCK: {
                        rc.setIndicatorString("STUCK");
                        Soldier.stuckBehavior(rc);
                    }
                }
                rc.setIndicatorDot(rc.getLocation(), 0, 0, 255);
                return;
            }

            case SoldierType.ATTACK: {
                if (enemyTower == null) {
                    soldierType = SoldierType.ADVANCE;
                    soldierState = SoldierState.EXPLORING;
                    Soldier.resetVariables();
                } else {
                    // Prioritize any towers the attack robot sees
                    for (MapInfo nearbyTile : nearbyTiles) {
                        // If enemy tower detected, then attack if you can or move towards it
                        MapLocation nearbyLocation = nearbyTile.getMapLocation();
                        if (nearbyTile.hasRuin() && rc.canSenseRobotAtLocation(nearbyLocation) && rc.senseRobotAtLocation(nearbyLocation).getTeam().opponent().equals(rc.getTeam())) {
                            if (rc.canAttack(nearbyLocation)) {
                                rc.attack(nearbyLocation);
                            } else {
                                Direction dir = Pathfinding.pathfind(rc, nearbyLocation);
                                if (dir != null) {
                                    rc.move(dir);
                                }
                            }
                            rc.setIndicatorDot(rc.getLocation(), 255, 0, 0);
                            return;
                        }
                    }
                    // If cannot see any towers, then attack robot tries to pathfind to its assigned enemy tower
                    MapLocation enemyTowerLoc = enemyTower.getMapLocation();
                    if (rc.canSenseRobotAtLocation(enemyTowerLoc) && rc.canAttack(enemyTowerLoc)) {
                        rc.attack(enemyTowerLoc);
                    } else {
                        Direction dir = Pathfinding.pathfind(rc, enemyTowerLoc);
                        if (dir != null) {
                            rc.move(dir);
                        }
                        // If tower not there anymore when we see it, set enemyTower to null
                        if (rc.canSenseLocation(enemyTowerLoc) && !rc.canSenseRobotAtLocation(enemyTowerLoc)) {
                            enemyTower = null;
                        }
                    }
                }
                rc.setIndicatorDot(rc.getLocation(), 255, 0, 0);
            }
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException{
        // Read input messages for information on enemy tile location
        Splasher.receiveLastMessage(rc);
        Helper.tryCompleteResourcePattern(rc);
        // Update last paint tower location
        if (lastTower == null) {
            Soldier.updateLastTower(rc);
        } else {
            Soldier.updateLastPaintTower(rc);
        }

        // If paint is low, go back to refill
        if (Robot.hasLowPaint(rc, 75)) {
            Robot.lowPaintBehavior(rc);
            return;
        }

        MapInfo[] all = rc.senseNearbyMapInfos();

        // Check to see if assigned tile is already filled in with our paint
        // Prevents splasher from painting already painted tiles
        if (removePaint != null && rc.canSenseLocation(removePaint.getMapLocation()) && rc.senseMapInfo(removePaint.getMapLocation()).getPaint().isAlly()){
            removePaint = null;
        }

        // splash assigned tile or move towards it
        if (removePaint != null){
            if (rc.canAttack(removePaint.getMapLocation()) && rc.isActionReady()){
                rc.attack(removePaint.getMapLocation());
                removePaint = null;

            }
            else if (!rc.canAttack(removePaint.getMapLocation())){
                Direction dir = Pathfinding.pathfind(rc, removePaint.getMapLocation());
                if (dir != null){
                    rc.move(dir);
                }
            }
            else{
                return;
            }
            oppositeCorner = null;
            return;
        } else { //splash other tiles it sees but avoid overlap
            MapInfo enemies = Sensing.getNearByEnemiesSortedShuffled(rc);
            if (enemies != null){
                removePaint = enemies;
                oppositeCorner = null;
                return;
            } else {
                removePaint = fillEmpty;
            }
        }
        Direction dir = Pathfinding.getUnstuck(rc);
        if (dir != null && rc.canMove(dir)){
            rc.move(dir);
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException{
        // When spawning in, check tile to see if it needs to be cleared

        if (botRoundNum == 3 && rc.senseMapInfo(rc.getLocation()).getPaint().isEnemy()){
            rc.attack(rc.getLocation());
            rc.move(Direction.NORTHEAST);
            return;
        }
        // Read all incoming messages
        Mopper.receiveLastMessage(rc);
        Helper.tryCompleteResourcePattern(rc);
        Mopper.trySwing(rc);

        // check around the mopper's attack radius
        for (MapInfo tile: rc.senseNearbyMapInfos(2)) {
            if (tile.getPaint().isEnemy()) {
                if (rc.canAttack(tile.getMapLocation())) {
                    rc.attack(tile.getMapLocation());
                }
                oppositeCorner = null;
                return;
            }
        }
        // move towards opponent tiles in vision range
        for (MapInfo tile: rc.senseNearbyMapInfos()){
            if (tile.getPaint().isEnemy()){
                oppositeCorner = null;
                Direction dir = Pathfinding.pathfind(rc, tile.getMapLocation());
                if (dir != null){
                    rc.move(dir);
                    return;
                }
            }
        }
        // Path to opposite corner if we can't find enemy paint
        if (removePaint != null){
            oppositeCorner = null;
            Mopper.removePaint(rc, removePaint);
        } else {
            // attack adjacent tiles if possible
            Direction exploreDir = Pathfinding.getUnstuck(rc);
            if (exploreDir != null && rc.canMove(exploreDir)) {
                rc.move(exploreDir);
            }
        }
    }
}
