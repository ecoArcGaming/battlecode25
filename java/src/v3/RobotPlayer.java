package v3;

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
    - Take better advantage of defense towers
    - Clumped robots is a bit problematic
    - Exploration around walls is ass(?)
    - Differential behavior given map size
    - Splasher improvements
        - Survivability
        - Don't paint on our own patterns
        - Paint underneath them en route to enemy?
        - Prioritize enemy over our own side?
    - Improves on SRPs
TODO (Specific issues we noticed that currently have a solution)
    - Fix exploration for soldiers so that when a mopper goes and takes over area, the soldier can come and
        finish the ruin pattern
    - Low health behavior to improve survivability
    - Handle the 25 tower limit
    - Soldiers get paint whenever they deliver a message/build a tower
    - Initial soldier behavior (ignore allies around towers, go towards the center)
    - Fix exploration not going around walls
    - Improve exploration random walk (weigh it better)
    - Check out robot distributions on varying map sizes and stuff (idk seems like tower queues are clogged up by splashers/moppers)
    - Robot lifecycle should be based around map size probably
    - Do we do SRPs too late?
    - Advance robots start stuck and maybe stay too long at home
    - Idea: somehow figure out symmetry of the map so we can tell robots to go in a certain direction
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
    static int numTurnsStuck = 0;

    // Key Soldier Location variables
    static MapInfo enemyTile = null; // location of an enemy paint/tower for a develop/advance robot to report
    static MapLocation ruinToFill = null; // location of a ruin that the soldier is filling in
    static MapLocation wanderTarget = null; // target for advance robot to pathfind towards during exploration
    static MapInfo enemyTower = null; // location of enemy tower for attack soldiers to pathfind to
    static UnitType fillTowerType = null;

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
        Tower.readNewMessages(rc);

        // starting condition
        if (rc.getRoundNum() == 1 ) {
            rc.buildRobot(UnitType.SOLDIER, rc.getLocation().add(spawnDirection));
        } else if (rc.getRoundNum() == 2) {
            rc.buildRobot(UnitType.SOLDIER, rc.getLocation().add(spawnDirection.opposite()));
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
        }

        // Hard coded robot type for very first exploration
        if (rc.getRoundNum() <= 3) {
            soldierType = SoldierType.BINLADEN;
            wanderTarget = new MapLocation(rc.getMapWidth() - rc.getLocation().x, rc.getMapHeight() - rc.getLocation().y);
        }

        switch (soldierType) {
            case SoldierType.BINLADEN: {
                RobotInfo[] nearbyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (rc.getRoundNum() >= (rc.getMapHeight() + rc.getMapWidth())/2 || nearbyRobots.length != 0) {
                    soldierType = SoldierType.ADVANCE;
                    return;
                }
                Soldier.updateStateOsama(rc, initLocation, nearbyTiles);
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
                        if (wanderTarget != null) {
                            Direction dir;
                            if (Math.random() < Constants.RANDOM_STEP_PROBABILITY){
                                Direction[] allDirections = Direction.allDirections();
                                dir = allDirections[(int) (Math.random() * allDirections.length)];
                            }
                            else {
                                dir = Pathfinding.pathfind(rc, wanderTarget);
                            }
                            if (dir != null && rc.canMove(dir)) {
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
                rc.setIndicatorDot(rc.getLocation(), 255, 255, 255);
                break;
            }

            case SoldierType.DEVELOP: {
                Soldier.updateState(rc, initLocation, nearbyTiles);
                Helper.tryCompleteResourcePattern(rc);
                if (numTurnsStuck > 100){
                    soldierType = SoldierType.SRP;
                    Soldier.resetVariables();
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
                        } else {
                            numTurnsStuck++;
                        }
                        break;
                    }
                }
                rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
                return;
            }

            case SoldierType.ADVANCE: {
                Soldier.updateState(rc, initLocation, nearbyTiles);
                // lifecycle moves to SRP
                if (numTurnsStuck > Constants.ADV_LIFE_CYCLE_TURNS){
                    soldierType = SoldierType.SRP;
                    Soldier.resetVariables();
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
                        if (wanderTarget != null) {
                            Direction dir;
                            if (Math.random() < Constants.RANDOM_STEP_PROBABILITY){
                                Direction[] allDirections = Direction.allDirections();
                                dir = allDirections[(int) (Math.random() * allDirections.length)];
                            }
                            else {
                                dir = Pathfinding.pathfind(rc, wanderTarget);
                            }
                            if (dir != null && rc.canMove(dir)) {
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
                        numTurnsStuck++;
                        Soldier.stuckBehavior(rc);

                    }
                }
                rc.setIndicatorString(soldierState.toString());
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
                if (numTurnsStuck > Constants.SRP_LIFE_CYCLE_TURNS){
                    soldierType = SoldierType.ADVANCE;
                    soldierState = SoldierState.EXPLORING;
                    Soldier.resetVariables();
                }
                switch (soldierState) {
                    case SoldierState.LOWONPAINT: {
                        rc.setIndicatorString("LOWONPAINT");
                        Soldier.lowPaintBehavior(rc);
                        break;
                    }
                    case SoldierState.FILLINGSRP: {
                        // if a nearby allied tile mismatches the SRP grid, paint over it
                        boolean hasPainted = false;
                        rc.setIndicatorString("FILLING SRP");
                        for (MapInfo nearbyTile :nearbyTiles) {
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
                        break;
                    }
                    case SoldierState.STUCK: {
                        rc.setIndicatorString("STUCK");
                        numTurnsStuck++;
                        Soldier.stuckBehavior(rc);
                        for (MapInfo map: nearbyTiles) {
                            if (map.getPaint().isAlly() && !map.getPaint().equals(Helper.resourcePatternType(rc, map.getMapLocation()))){
                                Soldier.resetVariables();
                                soldierState = SoldierState.FILLINGSRP;
                                numTurnsStuck = 0;
                                break;
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
            if (exploreDir != null) {
                rc.move(exploreDir);
            }
        }
    }
}
