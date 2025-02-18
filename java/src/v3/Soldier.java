package v3;

import battlecode.common.*;
import scala.Unit;
import scala.collection.Map;

import static v3.RobotPlayer.*;

/**
 * Class for all methods that a soldier will do
 */
public class Soldier extends Robot {

    /**
     * Method for soldier to do when low on paint
     */
    public static void lowPaintBehavior(RobotController rc) throws GameActionException {

        Robot.lowPaintBehavior(rc);
        if (rc.getPaint() > Constants.lowPaintThreshold) {
            if (soldierState != storedState) {
                soldierState = storedState;
            } else if (ruinToFill != null) {
                soldierState = SoldierState.FILLINGTOWER;
            } else {
                soldierState = SoldierState.STUCK;
            }
            Soldier.resetVariables();
        }
    }
    /**
     * Methods for soldiers painting, given a MapInfo and/or MapLocation
     * Paints when there is no paint or if allied paint is incorrect
     * */
    public static void paintIfPossible(RobotController rc, MapInfo paintTile, MapLocation paintLocation) throws GameActionException {
        if (paintTile.getPaint() == PaintType.EMPTY
                && rc.canAttack(paintLocation) && paintTile.getMark() == PaintType.EMPTY) {
            // If map size less than 30 by 30, then don't fill in SRP colors as wandering
            if (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT){
                rc.attack(paintLocation, false);
            }
            else {
                rc.attack(paintLocation, !Helper.resourcePatternGrid(rc, paintLocation));
            }
        }
    }
    public static void paintIfPossible(RobotController rc, MapInfo paintTile) throws GameActionException {
        MapLocation paintLocation = paintTile.getMapLocation();
        paintIfPossible(rc, paintTile, paintLocation);
    }
    public static void paintIfPossible(RobotController rc, MapLocation paintLocation) throws GameActionException {
        MapInfo paintTile = rc.senseMapInfo(rc.getLocation());
        paintIfPossible(rc, paintTile, paintLocation);
    }

    /**
     * Reads incoming messages and updates internal variables/state as necessary
     */
    public static void readNewMessages(RobotController rc) throws GameActionException{
        // Looks at all incoming messages from the past round
        for (Message message: rc.readMessages(rc.getRoundNum()-1)){
            int bytes = message.getBytes();
            // Information is type of robot
            if (bytes == 0 || bytes == 1 || bytes == 2) {
                switch (bytes) {
                    case 0:
                        if (Constants.rng.nextDouble() <= Constants.DEV_SRP_BOT_SPLIT ||
                                (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT)) {
                            soldierType = SoldierType.DEVELOP;
                        } else {
                            soldierType = SoldierType.SRP;
                            soldierState = SoldierState.FILLINGSRP;
                        }
                        break;
                    case 1:
                        soldierType = SoldierType.ADVANCE;
                        break;
                    case 2:
                        soldierType = SoldierType.ATTACK;
                        break;
                }
            } else if (soldierType == SoldierType.ADVANCE || soldierType == SoldierType.ATTACK) {
                MapInfo tile = MapInfoCodec.decode(bytes);
                if (tile.hasRuin()) {
                    enemyTower = tile;
                    soldierType = SoldierType.ATTACK;
                    Soldier.resetVariables();
                }
                wanderTarget = tile.getMapLocation();
            }
        }
    }

    /**
     * Returns the MapInfo of a nearby tower, and then a nearby tile if any are sensed
     * Nearby tiles only updated at a maximum of once every 15 turns
     * Returns null if none are sensed.
     */
    public static MapInfo updateEnemyTiles(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        // Check if there are enemy paint or enemy towers sensed
        RobotInfo closestEnemyTower = Sensing.towerInRange(rc, 20, false);
        if (closestEnemyTower != null) {
            return rc.senseMapInfo(closestEnemyTower.getLocation());
        }
        // Find all Enemy Tiles and return one if one exists, but only care once every 15 rounds
        MapInfo enemyPaint = Sensing.findEnemyPaint(rc, nearbyTiles);
        if (soldierMsgCooldown == -1 && enemyPaint != null) {
            soldierMsgCooldown = 30;
            return enemyPaint;
        }
        return null;
    }
    /**
     * Updates the robot state according to its paint level (LOWONPAINT),
     * nearby enemy paint (DELIVERINGMESSAGE), or nearby ruins (FILLING TOWER)
     */
    public static void updateState(RobotController rc, MapLocation curLocation, MapInfo[] nearbyTiles) throws GameActionException {
        if (Soldier.hasLowPaint(rc, Constants.lowPaintThreshold) && (rc.getMoney() < Constants.LOW_PAINT_MONEY_THRESHOLD || soldierState == SoldierState.FILLINGTOWER)) {
            if (soldierState != SoldierState.LOWONPAINT) {
                intermediateTarget = null;
                Soldier.resetVariables();
                storedState = soldierState;
                soldierState = SoldierState.LOWONPAINT;
            }
        } else if (soldierState != SoldierState.DELIVERINGMESSAGE && soldierState != SoldierState.LOWONPAINT) {
            // Update enemy tile as necessary
            enemyTile = updateEnemyTiles(rc, nearbyTiles);
            if (enemyTile != null && lastTower != null) {
                if (soldierState == SoldierState.EXPLORING){
                    prevLocation = rc.getLocation();
                    Soldier.resetVariables();
                }
                else{
                    intermediateTarget = null;
                    Soldier.resetVariables();
                }
                storedState = soldierState;
                soldierState = SoldierState.DELIVERINGMESSAGE;
            // Check for nearby buildable ruins if we are not currently building one
            } else if (soldierState != SoldierState.FILLINGTOWER) {
                MapInfo bestRuin = Sensing.findBestRuin(rc, curLocation, nearbyTiles);
                if (bestRuin != null) {
                    ruinToFill = bestRuin.getMapLocation();
                    soldierState = SoldierState.FILLINGTOWER;
                    Soldier.resetVariables();
                }
            }
        }
    }
    /**
     * Returns the MapInfo of a nearby tower
     * Nearby towers only updated at a maximum of once every 30 turns
     * Returns null if none are sensed.
     */
    public static MapInfo updateEnemyTowers(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        // Check if there are enemy paint or enemy towers sensed
        RobotInfo closestEnemyTower = Sensing.towerInRange(rc, 20, false);
        if (closestEnemyTower != null) {
            return rc.senseMapInfo(closestEnemyTower.getLocation());
        }
        return null;
    }

    /**
     * Updates the robot state according to its paint level (LOWONPAINT) or nearby ruins (FILLING TOWER)
     * Only cares about enemy paint if the round number is larger than the map length + map width
     */
    public static void updateStateOsama(RobotController rc, MapLocation curLocation, MapInfo[] nearbyTiles) throws GameActionException {
        if (Soldier.hasLowPaint(rc, Constants.lowPaintThreshold)) {
            if (soldierState != SoldierState.LOWONPAINT) {
                intermediateTarget = null;
                Soldier.resetVariables();
                storedState = soldierState;
                soldierState = SoldierState.LOWONPAINT;
            }
        } else if (soldierState != SoldierState.DELIVERINGMESSAGE && soldierState != SoldierState.LOWONPAINT) {
            // Update enemy towers as necessary
            enemyTile = updateEnemyTowers(rc, nearbyTiles);
            if (enemyTile != null && lastTower != null) {
                soldierType = SoldierType.ADVANCE;
                Soldier.resetVariables();
            }
            if (soldierState != SoldierState.FILLINGTOWER) {
                MapInfo bestRuin = Sensing.findAnyRuin(rc, curLocation, nearbyTiles);
                if (bestRuin != null) {
                    if (!Sensing.canBuildTower(rc, bestRuin.getMapLocation())) {
                        soldierType = SoldierType.ADVANCE;
                        Soldier.resetVariables();
                    } else {
                        ruinToFill = bestRuin.getMapLocation();
                        soldierState = SoldierState.FILLINGTOWER;
                        Soldier.resetVariables();
                    }
                }
            // Turn into an advance bot if they see an enemy paint that prevents tower building
            } else if (soldierState == SoldierState.FILLINGTOWER) {
                if (!Sensing.canBuildTower(rc, ruinToFill)) {
                    soldierType = SoldierType.ADVANCE;
                    Soldier.resetVariables();
                }
            }
        }
    }
    public static void updateSRPState(RobotController rc, MapLocation curLocation, MapInfo[] nearbyTiles) throws GameActionException {
        if (rc.getLocation().equals(SRPLocation)) {
            SRPLocation = null;
        }
        if (soldierState != SoldierState.LOWONPAINT && Soldier.hasLowPaint(rc, Constants.lowPaintThreshold)) {
            if (soldierState != SoldierState.STUCK) {
                SRPLocation = rc.getLocation();
            }
            Soldier.resetVariables();
            storedState = soldierState;
            soldierState = SoldierState.LOWONPAINT;
        } else if (soldierState == SoldierState.STUCK) {
            // If less than 30, check 5x5 area for empty or ally primary tiles and mark center
            if (rc.getMapWidth() <= Constants.SRP_MAP_WIDTH && rc.getMapHeight() <= Constants.SRP_MAP_HEIGHT && !rc.senseMapInfo(curLocation).getMark().isAlly()) {
                MapInfo[] possSRP = rc.senseNearbyMapInfos(8);
                boolean canBuildSRP = true;
                for (MapInfo map : possSRP) {
                    // If we can travel to tile and the paint is ally primary or empty, then build an srp
                    if (!map.isPassable() || map.getPaint().isEnemy()){
                        canBuildSRP = false;
                        break;
                    }
                }
                // Check if srp is within build range
                if (canBuildSRP && possSRP.length == 25 && !Sensing.conflictsSRP(rc)){
                    Soldier.resetVariables();
                    soldierState = SoldierState.FILLINGSRP;
                    srpCenter = rc.getLocation();
                    rc.mark(rc.getLocation(), false);
                }
            }
            else if (Soldier.hasLowPaint(rc, Constants.lowPaintThreshold)){
                for (MapInfo map : nearbyTiles) {
                    if (map.getPaint().isAlly() && !map.getPaint().equals(Helper.resourcePatternType(rc, map.getMapLocation()))) {
                        Soldier.resetVariables();
                        soldierState = SoldierState.FILLINGSRP;
                    }
                }
            }
        }
    }

    /**
     * Creates SRP on small maps by placing marker to denote the center and painting around the marker
     */
    public static void fillSRP(RobotController rc) throws GameActionException {
        if (!rc.getLocation().equals(srpCenter)) {
            Direction dir = Pathfinding.pathfind(rc, srpCenter);
            if (dir != null && rc.canMove(dir)){
                rc.move(dir);
            }
        }
        else {
            boolean finished = true;
            boolean srpComplete = true;
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    if (!rc.onTheMap(rc.getLocation().translate(i - 2, j - 2))) {
                        continue;
                    }
                    MapInfo srpLoc = rc.senseMapInfo(rc.getLocation().translate(i - 2, j - 2));
                    boolean isPrimary = Constants.primarySRP.contains(new HashableCoords(i, j));
                    if ((srpLoc.getPaint() == PaintType.ALLY_PRIMARY && isPrimary) || (srpLoc.getPaint() == PaintType.ALLY_SECONDARY && !isPrimary)){
                        continue;
                    }
                    srpComplete = false;
                    if (!rc.canAttack(srpLoc.getMapLocation())) {
                        continue;
                    }
                    // If paint is empty or ally paint doesnt match, then paint proper color
                    if (srpLoc.getPaint() == PaintType.EMPTY) {
                        rc.attack(srpLoc.getMapLocation(), !isPrimary);
                        finished = false;
                        break;
                    } else if (srpLoc.getPaint() == PaintType.ALLY_PRIMARY && !isPrimary) {
                        rc.attack(srpLoc.getMapLocation(), true);
                        finished = false;
                        break;
                    } else if (srpLoc.getPaint() == PaintType.ALLY_SECONDARY && isPrimary) {
                        rc.attack(srpLoc.getMapLocation(), false);
                        finished = false;
                        break;
                    }
                }
            }
            if (finished) {
                if (srpComplete) {
                    soldierState = SoldierState.STUCK;
                    srpCenter = null;
                    numTurnsAlive = 0;
                }
                if (rc.canCompleteResourcePattern(rc.getLocation())) {
                    rc.completeResourcePattern(rc.getLocation());
                    soldierState = SoldierState.STUCK;
                    srpCenter = null;
                    numTurnsAlive = 0;
                }
            }
        }
    }


    /**
     * Pathfinds towards the last known paint tower and try to message it
     */
    public static void msgTower(RobotController rc) throws GameActionException {
        for (RobotInfo enemyRobot : rc.senseNearbyRobots(-1, rc.getTeam().opponent())) {
            if (enemyRobot.getType().isTowerType()) {
                if (rc.canAttack(enemyRobot.getLocation())) {
                    rc.attack(enemyRobot.getLocation());
                    break;
                }
            }
        }
        MapLocation towerLocation = lastTower.getMapLocation();
        if (rc.canSenseRobotAtLocation(towerLocation) && rc.canSendMessage(towerLocation)) {
            Communication.sendMapInformation(rc, enemyTile, towerLocation);
            enemyTile = null;
            if (soldierState != storedState) {
                soldierState = storedState;
            } else if (ruinToFill != null) {
                soldierState = SoldierState.FILLINGTOWER;
            }
            else {
                soldierState = SoldierState.STUCK;
            }
            Soldier.resetVariables();
            if (prevLocation != null){
                intermediateTarget = prevLocation;
                prevLocation = null;
            }
            return;
        }
        Direction dir = Pathfinding.returnToTower(rc);
        if (dir != null){
            rc.move(dir);
        }
    }

    /**
     * Soldier version of completeRuinIfPossible
     */
    public static void completeRuinIfPossible(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        Robot.completeRuinIfPossible(rc, ruinLocation);
        if (rc.canSenseRobotAtLocation(ruinLocation)) {
            soldierState = SoldierState.LOWONPAINT;
            storedState = SoldierState.EXPLORING;
            ruinToFill = null;
            fillTowerType = null;
        }
    }

    /**
     * Marks ruins
     * Pathfinds to the ruins and fills in the area around the ruin if we can build a tower there
     * If ignoreAlly is true, then we ignore the ruin if ally robots are already in proximity
     */
    public static void fillInRuin(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        // Mark the pattern we need to draw to build a tower here if we haven't already.
        // If robot has seen a paint tower, mark random tower
        if (!Sensing.canBuildTower(rc, ruinLocation)) {
            if(rc.canSenseRobotAtLocation(ruinLocation) && rc.senseRobotAtLocation(ruinLocation).getType() == UnitType.LEVEL_ONE_PAINT_TOWER) {
                soldierState = SoldierState.LOWONPAINT;
                storedState = SoldierState.EXPLORING;
                fillTowerType = null;
                ruinToFill = null;
            }
            else{
                soldierState = SoldierState.EXPLORING;
                fillTowerType = null;
                ruinToFill = null;
            }
        }
        // Check to see if we know the type of tower to fill in
        if (fillTowerType != null){
            // Paint the tile at a location
            PaintType[][] ruinPattern = (fillTowerType == UnitType.LEVEL_ONE_PAINT_TOWER) ? Constants.paintTowerPattern : (fillTowerType == UnitType.LEVEL_ONE_MONEY_TOWER) ? Constants.moneyTowerPattern : Constants.defenseTowerPattern;
            int[] tileToPaint = Sensing.findPaintableRuinTile(rc, ruinLocation, ruinPattern);
            if (tileToPaint != null) {
                MapLocation tile = ruinLocation.translate(tileToPaint[0], tileToPaint[1]);
                if (rc.canPaint(tile) && rc.canAttack(tile))
                    rc.attack(tile, ruinPattern[tileToPaint[0]+2][tileToPaint[1]+2] == PaintType.ALLY_SECONDARY);
            }
            // Move to the ruin
            Direction moveDir = Pathfinding.pathfind(rc, ruinLocation);
            if (moveDir != null) {
                rc.move(moveDir);
            }
            // Tries to complete the ruin
            completeRuinIfPossible(rc, ruinLocation);
        }
        else{
            // Determine the marking of the tower and mark if no marking present
            MapLocation northTower = ruinLocation.add(Direction.NORTH);
            if (rc.canSenseLocation(northTower)) {
                PaintType towerMarking = rc.senseMapInfo(northTower).getMark();
                // If mark type is 1, then ruin is a paint ruin
                if(towerMarking == PaintType.ALLY_PRIMARY){
                    fillTowerType = UnitType.LEVEL_ONE_PAINT_TOWER;
                }
                // If no mark, then check to see if there is a marking on east for defense tower
                else if (towerMarking == PaintType.EMPTY){
                    MapLocation defenseMarkLoc = northTower.add(Direction.EAST);
                    if (rc.canSenseLocation(defenseMarkLoc)) {
                        if (rc.senseMapInfo(defenseMarkLoc).getMark() == PaintType.ALLY_PRIMARY){
                            fillTowerType = UnitType.LEVEL_ONE_DEFENSE_TOWER;
                        }
                        // If can sense location but no mark, then figure out tower type
                        else{
                            UnitType towerType = Robot.genTowerType(rc, ruinLocation);
                            if (towerType == UnitType.LEVEL_ONE_DEFENSE_TOWER && rc.canMark(defenseMarkLoc)){
                                // Mark defense tower at north east
                                rc.mark(defenseMarkLoc, false);
                                fillTowerType = UnitType.LEVEL_ONE_DEFENSE_TOWER;
                            }
                            // If can mark tower, then mark it
                            else if (rc.canMark(northTower) && towerType != UnitType.LEVEL_ONE_DEFENSE_TOWER) {
                                if (seenPaintTower){
                                    rc.mark(northTower, towerType == UnitType.LEVEL_ONE_MONEY_TOWER);
                                    fillTowerType = towerType;
                                } else {
                                    // Otherwise, mark a paint tower
                                    rc.mark(northTower, false);
                                    fillTowerType = UnitType.LEVEL_ONE_PAINT_TOWER;
                                }
                            }
                            // Otherwise, pathfind towards location until can mark it
                            else{
                                Direction moveDir = Pathfinding.pathfind(rc, ruinLocation);
                                if (moveDir != null) {
                                    rc.move(moveDir);
                                }
                            }
                        }
                    }
                    // Otherwise, pathfind to ruin location since we can't sense the location of the ruin
                    else{
                        Direction moveDir = Pathfinding.pathfind(rc, ruinLocation);
                        if (moveDir != null) {
                            rc.move(moveDir);
                        }
                    }
                }
                // Otherwise, ruin is a money ruin
                else{
                    fillTowerType = UnitType.LEVEL_ONE_MONEY_TOWER;
                }
            }
            // Otherwise, pathfind to the tower
            else{
                Direction moveDir = Pathfinding.pathfind(rc, ruinLocation);
                if (moveDir != null) {
                    rc.move(moveDir);
                }
            }
        }
    }

    /**
     * Stuck behavior method
     */
    public static void stuckBehavior(RobotController rc) throws GameActionException {
        Direction newDir;
        if (soldierType == SoldierType.DEVELOP || soldierType == SoldierType.SRP){
            newDir = Pathfinding.findOwnCorner(rc);
        }
        else{
            newDir = Pathfinding.getUnstuck(rc);
        }
        if (newDir != null) {
            rc.move(newDir);
            Soldier.paintIfPossible(rc, rc.getLocation());
        }
    }
}
