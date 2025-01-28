package v3;

import battlecode.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.Map;

import java.util.*;

/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
/*
FIXME (General issues we noticed)
    - Clumped robots is a bit problematic
    - Exploration around walls is ass(?)
    - Differential behavior given map size
    - Splasher improvements
        - Survivability
        - Don't paint on our own patterns
        - Paint underneath them en route to enemy?
        - Prioritize enemy over our own side?
    - Strategy kinda sucks for smaller maps
TODO (Specific issues we noticed that currently have a solution)
    - Fix exploration for soldiers so that when a mopper goes and takes over area, the soldier can come and
        finish the ruin pattern
    - Low health behavior to improve survivability
    - Handle the 25 tower limit
    - Bug1 shenanigans (maybe we should try bug0 and take the L if bots do get stuck)
    - Check out robot distributions on varying map sizes and stuff (idk seems like tower queues are clogged up by splashers/moppers)
    - Robot lifecycle should be based around map size probably
    - Do we do SRPs too late?
    - Idea: somehow figure out symmetry of the map so we can tell robots to go in a certain direction
    - Have a better strategy for attacking the enemy
    - lifecycle idea: stuck && alive for x turns
    - if a bot is on low paint behavior and it runs out of paint standing next to the tower, if the tower doesnt have enough paint to refill to max, it just gets all the paint remaining in the tower and then leaves
 */
    
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    // Initialization Variables
    static int turnCount = 0;
    static int[][] currGrid;
    static ArrayList<MapLocation> last8 = new ArrayList<MapLocation>(); // Acts as queue
    static MapInfo lastTower = null;
    static SoldierType soldierType = SoldierType.ADVANCE;

    // Pathfinding Variable
    static int stuckTurnCount = 0;
    static int closestPath = -1;
    static boolean inBugNav = false;
    static MapLocation acrossWall = null;
    static MapLocation prevLocation = null;

    // Soldier state variables
    static SoldierState soldierState = SoldierState.EXPLORING;
    static SoldierState storedState = SoldierState.EXPLORING;

    static MapInfo fillEmpty = null;
    static int soldierMsgCooldown = -1;
    static int numTurnsAlive = 0; // Variable keeping track of how many turns alive for the soldier lifecycle

    // Key Soldier Location variables
    static MapInfo enemyTile = null; // location of an enemy paint/tower for a develop/advance robot to report
    static MapLocation ruinToFill = null; // location of a ruin that the soldier is filling in
    static MapLocation wanderTarget = null; // target for advance robot to pathfind towards during exploration
    static MapInfo enemyTower = null; // location of enemy tower for attack soldiers to pathfind to
    static UnitType fillTowerType = null;
    static MapLocation intermediateTarget = null; // used to record short-term robot targets
    static MapLocation prevIntermediate = null; //Copy of intermediate target

    // Enemy Info variables
    static MapInfo enemyTarget = null; // location of enemy tower/tile for tower to tell
    static MapInfo removePaint = null;

    // Tower Spawning Variables
    static ArrayList<Integer> spawnQueue = new ArrayList<>();
    static boolean sendTypeMessage = false;
    static Direction spawnDirection = null;
    static int numEnemyVisits = 0;
    static int roundsWithoutEnemy = 0;
    static int numSoldiersSpawned = 0;

    // Navigation Variables
    static MapLocation oppositeCorner = null;
    static boolean seenPaintTower = false;
    static int botRoundNum = 0;

    // Towers Broadcasting Variables
    static boolean broadcast = false;
    static boolean alertRobots = false;
    static boolean alertAttackSoldiers = false;

    // BugNav Variables
    static boolean isTracing = false;
    static int smallestDistance = 10000000;
    static MapLocation closestLocation = null;
    static Direction tracingDir = null;
    static MapLocation stoppedLocation = null;
    static int tracingTurns = 0;
    static int bug1Turns = 0;

    // Splasher State Variables
    static boolean isLowPaint = false;
    static MapInfo prevLocInfo = null;

    // Filling SRP State
    static MapLocation srpCenter = null;

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
        currGrid = new int[rc.getMapWidth()][rc.getMapHeight()];
        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.
            turnCount += 1;  // We have now been alive for one more turn!
            numTurnsAlive++;
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
//                for (MapInfo mi : rc.senseNearbyMapInfos()) {
//                    currGrid[mi.getMapLocation().y][mi.getMapLocation().x] = mi;
//                }

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
                if (last8.size() < 16) {
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
        Tower.readNewMessages(rc);

        // starting condition
        if (rc.getRoundNum() == 1 ) {
            rc.buildRobot(UnitType.SOLDIER, rc.getLocation().add(spawnDirection));
        } else if (rc.getRoundNum() == 2) {
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            if (!rc.getLocation().isWithinDistanceSquared(center, 150)) {
                rc.buildRobot(UnitType.SOLDIER, rc.getLocation().add(spawnDirection.rotateRight()));
            } else {
//                MapInfo enemyTile = Sensing.findEnemyPaint(rc, rc.senseNearbyMapInfos());
                int enemyTiles = Tower.countEnemyPaint(rc);
                if (enemyTiles == 0 || enemyTiles > 20){
                    rc.buildRobot(UnitType.SPLASHER, rc.getLocation().add(spawnDirection.rotateRight()));
                }
                else{
                    rc.buildRobot(UnitType.MOPPER, rc.getLocation().add(spawnDirection.rotateRight()));
                    if (rc.getType() == UnitType.LEVEL_ONE_MONEY_TOWER || rc.getType() == UnitType.LEVEL_TWO_MONEY_TOWER)
                        spawnQueue.add(3);
                }
            }
        } else {
            if (broadcast){
                rc.broadcastMessage(MapInfoCodec.encode(enemyTarget));
                broadcast = false;
            }

            // If unit has been spawned and communication hasn't happened yet
            if (sendTypeMessage) {
                Tower.sendTypeMessage(rc, spawnQueue.getFirst());
            }

            // Otherwise, if the spawn queue isn't empty, spawn the required unit
            else if (!spawnQueue.isEmpty() && (rc.getMoney() > 400 || (rc.getType() != UnitType.LEVEL_ONE_PAINT_TOWER && rc.getType() != UnitType.LEVEL_TWO_PAINT_TOWER && rc.getType() != UnitType.LEVEL_THREE_PAINT_TOWER))) {
                switch (spawnQueue.getFirst()) {
                    case 0, 1, 2:
                        Tower.createSoldier(rc);
                        break;
                    case 3:
                        Tower.createMopper(rc);
                        break;
                    case 4:
                        Tower.createSplasher(rc);
                        break;
                }
            } else if (rc.getMoney() > 1200 && rc.getPaint() > 200 && spawnQueue.size() < 3) {
                Tower.addRandomToQueue(rc);
            }
        }
        if (enemyTarget != null && alertRobots) {
            Tower.broadcastNearbyBots(rc);
        }

        if (enemyTower != null && rc.getRoundNum() % 50 == 0) {
            Tower.broadcastEnemyTower(rc);
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
        if (rc.getType() == UnitType.LEVEL_ONE_DEFENSE_TOWER && rc.getMoney() > 5000) {
            rc.upgradeTower(rc.getLocation());
        }
        if (rc.getType() == UnitType.LEVEL_TWO_DEFENSE_TOWER && rc.getMoney() > 7500) {
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
            wanderTarget = new MapLocation(rc.getMapWidth() - rc.getLocation().x, rc.getMapHeight() - rc.getLocation().y);
        }

        // Hard coded robot type for very first exploration
        if (rc.getRoundNum() <= 3) {
            soldierType = SoldierType.BINLADEN;
            wanderTarget = new MapLocation(rc.getMapWidth() - rc.getLocation().x, rc.getMapHeight() - rc.getLocation().y);
        }

        switch (soldierType) {
            case SoldierType.BINLADEN: {
                if (rc.getRoundNum() >= (rc.getMapHeight() + rc.getMapWidth())/2) {
                    soldierType = SoldierType.ADVANCE;
                    return;
                }
                Soldier.updateStateOsama(rc, initLocation, nearbyTiles);
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
                        rc.setIndicatorString("FILLINGTOWER" + ruinToFill);
                        Soldier.fillInRuin(rc, ruinToFill);
                        break;
                    }
                    case SoldierState.EXPLORING: {
                        rc.setIndicatorString("EXPLORING");
                        if (wanderTarget != null) {
                            Direction dir = Pathfinding.betterExplore(rc, initLocation, wanderTarget, false);
                            if (dir != null) {
                                rc.move(dir);
                                Soldier.paintIfPossible(rc, rc.getLocation());
                            }
                        } else {
                            intermediateTarget = null;
                            soldierState = SoldierState.STUCK;
                            Soldier.resetVariables();
                        }
                        if (intermediateTarget != null) {
                            rc.setIndicatorString("EXPLORING " + intermediateTarget);
                        }
                        break;
                    }
                    case SoldierState.STUCK: {
                        rc.setIndicatorString("STUCK");
                        Soldier.stuckBehavior(rc);
                    }
                }
                rc.setIndicatorDot(rc.getLocation(), 255, 255, 255);
                break;
            }

            case SoldierType.DEVELOP: {
                Soldier.updateState(rc, initLocation, nearbyTiles);
                Helper.tryCompleteResourcePattern(rc);

                if (numTurnsAlive > Constants.DEV_LIFE_CYCLE_TURNS && soldierState == SoldierState.STUCK) {
                    numTurnsAlive = 0;
                    if (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT){
                        soldierState = SoldierState.STUCK;
                    }
                    else
                        soldierState = SoldierState.FILLINGSRP;

                    soldierType = SoldierType.SRP;
                    Soldier.resetVariables();
                    return;
                }

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
                        if (Sensing.findPaintableTile(rc, rc.getLocation(), 20) != null) {
                            soldierState = SoldierState.EXPLORING;
                            Soldier.resetVariables();
                        }
                        break;
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
                        rc.setIndicatorString("FILLINGTOWER" + ruinToFill);
                        Soldier.fillInRuin(rc, ruinToFill);
                        break;
                    }
                    case SoldierState.EXPLORING: {
                        rc.setIndicatorString("EXPLORING");
                        if (wanderTarget != null) {
                            Direction dir = Pathfinding.betterExplore(rc, initLocation, wanderTarget, false);
                            if (dir != null) {
                                rc.move(dir);
                                Soldier.paintIfPossible(rc, rc.getLocation());
                            }
                        } else {
                            intermediateTarget = null;
                            soldierState = SoldierState.STUCK;
                            Soldier.resetVariables();
                        }
                        if (intermediateTarget != null) {
                            rc.setIndicatorString("EXPLORING " + intermediateTarget);
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
                        if (nearbyTile.hasRuin() && rc.canSenseRobotAtLocation(nearbyLocation) && !rc.senseRobotAtLocation(nearbyLocation).getTeam().isPlayer()) {
                            enemyTower = nearbyTile;
                            rc.setIndicatorDot(rc.getLocation(), 255, 0, 0);
                            return;
                        }
                    }
                    // If cannot see any towers, then attack robot tries to pathfind to its assigned enemy tower
                    MapLocation enemyTowerLoc = enemyTower.getMapLocation();
                    if (rc.canSenseRobotAtLocation(enemyTowerLoc) && rc.canAttack(enemyTowerLoc)) {
                        rc.attack(enemyTowerLoc);
                        Direction back = enemyTowerLoc.directionTo(rc.getLocation());
                        if (rc.canMove(back)){
                            rc.move(back);
                        }
                    } else {
                        Direction dir = Pathfinding.pathfind(rc, enemyTowerLoc);
                        if (dir != null) {
                            rc.move(dir);
                            if (rc.canAttack(enemyTowerLoc)){
                                rc.attack(enemyTowerLoc);
                            }
                        }
                        // If tower not there anymore when we see it, set enemyTower to null
                        if (rc.canSenseLocation(enemyTowerLoc) && !rc.canSenseRobotAtLocation(enemyTowerLoc)) {
                            enemyTower = null;
                        }
                    }
                }
                rc.setIndicatorDot(rc.getLocation(), 255, 0, 0);
                break;
            }

            case SoldierType.SRP: {
                // check for low paint and numTurnStuck
                Soldier.updateSRPState(rc, initLocation, nearbyTiles);
                Helper.tryCompleteResourcePattern(rc);
                // if stuck for too long, become attack bot
                if (numTurnsAlive > Constants.SRP_LIFE_CYCLE_TURNS && soldierState == SoldierState.STUCK){
                    soldierType = SoldierType.ADVANCE;
                    soldierState = SoldierState.EXPLORING;
                    numTurnsAlive = 0;
                    Soldier.resetVariables();
                }
                switch (soldierState) {
                    case SoldierState.LOWONPAINT: {
                        rc.setIndicatorString("LOWONPAINT");
                        Soldier.lowPaintBehavior(rc);
                        break;
                    }
                    case SoldierState.FILLINGSRP: {
                        if (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT){
                            Soldier.fillSRP(rc);
                        }
                        else {
                            // if a nearby allied tile mismatches the SRP grid, paint over it
                            boolean hasPainted = false;
                            rc.setIndicatorString("FILLING SRP");
                            for (MapInfo nearbyTile : nearbyTiles) {
                                MapLocation nearbyLocation = nearbyTile.getMapLocation();
                                PaintType paint = Helper.resourcePatternType(rc, nearbyLocation);
                                if (nearbyTile.getPaint().isAlly() &&
                                        !paint.equals(nearbyTile.getPaint())) {
                                    Direction dir = Pathfinding.pathfind(rc, nearbyLocation);
                                    if (rc.canAttack(nearbyLocation)) {
                                        rc.attack(nearbyLocation, (paint == PaintType.ALLY_SECONDARY));
                                        hasPainted = true;
                                        break;
                                    } else if (dir != null) {
                                        if (rc.canMove(dir)) {
                                            rc.move(dir);
                                            hasPainted = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            // stuck if nothing to paint
                            if (!hasPainted) {
                                soldierState = SoldierState.STUCK;
                            }
                        }
                        break;
                    }
                    case SoldierState.STUCK: {
                        rc.setIndicatorString("STUCK");
                        Soldier.stuckBehavior(rc);
                        if (!(rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT)) {
                            for (MapInfo map : nearbyTiles) {
                                if (map.getPaint().isAlly() && !map.getPaint().equals(Helper.resourcePatternType(rc, map.getMapLocation()))) {
                                    Soldier.resetVariables();
                                    soldierState = SoldierState.FILLINGSRP;
                                    numTurnsAlive = 0;
                                    break;
                                }
                            }
                        }
                        else if (rc.senseMapInfo(rc.getLocation()).getMark().isAlly() && !rc.canCompleteResourcePattern(rc.getLocation())){
                            boolean turnToSRP = true;
                            boolean allSame = true;
                            for (int i = 0; i < 5; i++) {
                                for (int j = 0; j < 5; j++) {
                                    MapInfo srpLoc = rc.senseMapInfo(rc.getLocation().translate(i - 2, j - 2));
                                    if (srpLoc.hasRuin() || srpLoc.getPaint().isEnemy()) {
                                        turnToSRP = false;
                                        break;
                                    }
                                    boolean isPrimary = Constants.primarySRP.contains(new HashableCoords(i, j));
                                    if ((srpLoc.getPaint() == PaintType.ALLY_PRIMARY && isPrimary) || (srpLoc.getPaint() == PaintType.ALLY_SECONDARY && !isPrimary)) {
                                        allSame = false;
                                    }
                                }
                            }
                            if (turnToSRP && !allSame){
                                Soldier.resetVariables();
                                soldierState = SoldierState.FILLINGSRP;
                                srpCenter = rc.getLocation();
                                numTurnsAlive = 0;
                            }
                        }
                        break;
                    }
                }
                rc.setIndicatorDot(rc.getLocation(), 255, 0, 255);
                break;
            }
            default:
                rc.setIndicatorDot(rc.getLocation(), 0, 0, 0);
        }
    }

    public static void runSplasher(RobotController rc) throws GameActionException{
        if (removePaint != null) {
            rc.setIndicatorString(removePaint.toString());
        } else {
            rc.setIndicatorString("null");
        }
        // Read input messages for information on enemy tile location
        Splasher.receiveLastMessage(rc);

        if (lastTower != null && rc.canSenseLocation(lastTower.getMapLocation())){
            if (!rc.canSenseRobotAtLocation(lastTower.getMapLocation())) {
                lastTower = null;
            }
        }

        // Update last paint tower location
        if (lastTower == null) {
            Soldier.updateLastTower(rc);
        } else {
            Soldier.updateLastPaintTower(rc);
        }

        // If paint is low, go back to refill
        if (Robot.hasLowPaint(rc, 75) && rc.getMoney() < Constants.LOW_PAINT_MONEY_THRESHOLD) {
            if (!isLowPaint){
                inBugNav = false;
                acrossWall = null;
                prevLocInfo = rc.senseMapInfo(rc.getLocation());
            }
            Robot.lowPaintBehavior(rc);
            return;
        }

        else if (isLowPaint){
            if (removePaint == null){
                removePaint = prevLocInfo;
            }
            prevLocInfo = null;
            inBugNav = false;
            acrossWall = null;
            isLowPaint = false;
        }

        // move perpendicular to enemy towers if any exists in range
        for (RobotInfo bot: rc.senseNearbyRobots()){
            if (bot.getType().isTowerType() && !bot.getTeam().equals(rc.getTeam())){
                Direction dir = rc.getLocation().directionTo(bot.getLocation()).rotateRight().rotateRight().rotateRight();
                if (rc.canMove(dir)) {
                    rc.move(dir);
                }
                if (removePaint != null && removePaint.getMapLocation().distanceSquaredTo(bot.getLocation()) <= 9){
                    removePaint = null; // ignore target in tower range
                }
            }
        }
        MapInfo enemies = Sensing.scoreSplasherTiles(rc);

        // Check to see if assigned tile is already filled in with our paint
        // Prevents splasher from painting already painted tiles
        if (removePaint != null && rc.canSenseLocation(removePaint.getMapLocation()) && rc.senseMapInfo(removePaint.getMapLocation()).getPaint().isAlly()){
            removePaint = null;
        }

        // splash assigned tile or move towards it

        if (enemies != null && rc.canAttack(enemies.getMapLocation())){
            rc.attack(enemies.getMapLocation());
            return;
        }
        else if (enemies != null){
            if (removePaint == null){
                removePaint = enemies;
            }

            Direction dir = Pathfinding.pathfind(rc, enemies.getMapLocation());
            if (dir != null){
                rc.move(dir);
            }
            return;
        }

        else if (removePaint != null) {
            if (rc.canAttack(removePaint.getMapLocation())) {
                rc.attack(removePaint.getMapLocation());
                return;
            }
            Direction dir = Pathfinding.pathfind(rc, removePaint.getMapLocation());
            if (rc.getActionCooldownTurns()  < 10 && dir != null){
                rc.move(dir);
            }
            return;
        }

        if (botRoundNum > 1) {
            //        System.out.println("BEFORE PATH " + Clock.getBytecodeNum());
            Direction dir = Pathfinding.betterUnstuck(rc);
            //        System.out.println("AFTER PATH " + Clock.getBytecodeNum());
            if (dir != null && rc.canMove(dir)) {
                rc.move(dir);
            }
        }
    }

    public static void runMopper(RobotController rc) throws GameActionException{
        // When spawning in, check tile to see if it needs to be cleared
        if (removePaint != null) {
            rc.setIndicatorString(removePaint.toString());
        } else {
            rc.setIndicatorString("null");
        }
        if (botRoundNum == 3 && rc.senseMapInfo(rc.getLocation()).getPaint().isEnemy()){
            rc.attack(rc.getLocation());
            return;
        }
        // Read all incoming messages
        Mopper.receiveLastMessage(rc);
        Helper.tryCompleteResourcePattern(rc);

        MapInfo[] all = rc.senseNearbyMapInfos();
        // avoid enemy towers with the highest priority
        for (MapInfo nearbyTile : all) {
            RobotInfo bot = rc.senseRobotAtLocation(nearbyTile.getMapLocation());
            if (bot != null && bot.getType().isTowerType() && !bot.getTeam().equals(rc.getTeam())){
                if (removePaint != null && removePaint.getMapLocation().distanceSquaredTo(bot.getLocation()) <= 9){
                    removePaint = null; // ignore target in tower range
                }

                // move around the tower by rotating 135 degrees
                Direction dir = rc.getLocation().directionTo(nearbyTile.getMapLocation()).rotateRight().rotateRight().rotateRight();
                if (rc.canMove(dir)){
                    rc.move(dir);
                    break;
                }
            }
        }
        // stay safe, stay on ally paint if possible
        if (!rc.senseMapInfo(rc.getLocation()).getPaint().isAlly()){
            Direction dir = Mopper.mopperWalk(rc);
            if (dir != null && rc.canMove(dir)){
                rc.move(dir);
            }
        }

        MapLocation currPaint = null;
        // check around the Mopper's attack radius for bots
        for (MapInfo tile: rc.senseNearbyMapInfos(2)) {
            RobotInfo bot = rc.senseRobotAtLocation(tile.getMapLocation());
            if (bot != null){
                if (bot.getType().isRobotType() && !bot.getTeam().equals(rc.getTeam()) && bot.getPaintAmount() > 0){
                    if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())){
                        rc.attack(tile.getMapLocation());
                    }
                    Direction dir = rc.getLocation().directionTo(bot.location);
                    switch (dir){
                        case Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST:
                            if (rc.canMopSwing(dir)){
                                rc.mopSwing(dir);
                                oppositeCorner = null;

                            }
                        default:
                            if (rc.canMopSwing(dir.rotateRight())){
                                rc.mopSwing(dir.rotateRight());
                                oppositeCorner = null;
                            }
                    }
                    return;
                }
            }

            if (tile.getPaint().isEnemy()) {
                if (rc.canAttack(tile.getMapLocation())) {
                    currPaint = tile.getMapLocation();
                }
                oppositeCorner = null;
            }
        }

        // move towards opponent bot in vision range
        for (MapInfo tile: rc.senseNearbyMapInfos()){
            // First check for enemy tile, store it
            if (tile.getPaint().isEnemy()){
                oppositeCorner = null;
                if (currPaint == null){
                    currPaint = tile.getMapLocation();
                }
            }
            RobotInfo bot = rc.senseRobotAtLocation(tile.getMapLocation());
            if (bot != null ){
                if (bot.getType().isRobotType() && !bot.getTeam().equals(rc.getTeam()) && !tile.getPaint().isEnemy()){
                    Direction enemyDir = Pathfinding.pathfind(rc, tile.getMapLocation());
                    if (enemyDir != null){
                        oppositeCorner = null;
                        rc.move(enemyDir);
                        break;
                    }
                }
            }
        }
        // attack nearest paint if exists with lower priority
        if (currPaint != null){
            if (rc.canAttack(currPaint)){
                oppositeCorner = null;
                rc.attack(currPaint);
                return;
            } else if (rc.isActionReady()){
                Direction dir = Pathfinding.pathfind(rc, currPaint);
                if (dir != null){
                    oppositeCorner = null;
                    rc.move(dir);
                    if (rc.canAttack(currPaint)){
                        rc.attack(currPaint);
                    }
                }
            }
            return;
        }
        // Path to opposite corner if we can't find enemy paint, lowest priority
        if (removePaint != null){
            oppositeCorner = null;
            Mopper.removePaint(rc, removePaint);
        } else {
            // attack adjacent tiles if possible
            Direction exploreDir = Pathfinding. getUnstuck(rc);
            if (exploreDir != null) {
                rc.move(exploreDir);
            }
        }
    }
}
