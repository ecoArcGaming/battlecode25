package v2;

import battlecode.common.*;

import static v2.RobotPlayer.*;

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
            rc.attack(paintLocation);
        } else if ((!paintTile.getPaint().isEnemy()) && paintTile.getMark() != paintTile.getPaint()
                && paintTile.getMark() != PaintType.EMPTY && rc.canAttack(paintLocation)){
            boolean useSecondaryColor = paintTile.getMark() == PaintType.ALLY_SECONDARY;
            rc.attack(paintLocation, useSecondaryColor);
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
                        soldierType = SoldierType.DEVELOP;
                        break;
                    case 1:
                        soldierType = SoldierType.ADVANCE;
                        break;
                    case 2:
                        soldierType = SoldierType.ATTACK;
                        break;
                }
            } else if (soldierType == SoldierType.ADVANCE){
                MapInfo tile = MapInfoCodec.decode(bytes);
                if (tile.hasRuin()) {
                    enemyTower = tile;
                    soldierType = SoldierType.ATTACK;
                    Soldier.resetVariables();
                } else {
                    wanderTarget = tile.getMapLocation();
                }

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
        if (Soldier.hasLowPaint(rc, Constants.lowPaintThreshold)) {
            if (soldierState != SoldierState.LOWONPAINT) {
                Soldier.resetVariables();
                storedState = soldierState;
                soldierState = SoldierState.LOWONPAINT;
            }
        } else {
            if (soldierState != SoldierState.DELIVERINGMESSAGE && soldierState != SoldierState.LOWONPAINT) {
                // Update enemy tile as necessary
                enemyTile = updateEnemyTiles(rc, nearbyTiles);
                if (enemyTile != null) {
                    Soldier.resetVariables();
                    storedState = soldierState;
                    soldierState = SoldierState.DELIVERINGMESSAGE;
                } else {
                    // TODO: soldier currently only checks the closest ruin. however, if this ruin is not buildable,
                    //  we don't check any other ruins
                    //  Issue with checking all ruins: we don't want to bounce between different ruins
                    //  Possible fix: only update ruinToFill if state is not FILLINGTOWER
                    // Check if the robot can fill in paint for the ruin if no enemy tiles found
                    MapInfo closestRuin = Sensing.findClosestRuin(rc, curLocation, nearbyTiles);
                    if (closestRuin != null && Sensing.canBuildTower(rc, closestRuin.getMapLocation())) {
                        ruinToFill = closestRuin.getMapLocation();
                        soldierState = SoldierState.FILLINGTOWER;
                        Soldier.resetVariables();
                    }
                }
            }
        }
    }

    /**
     * Pathfinds towards the last known paint tower and try to message it
     */
    public static void msgTower(RobotController rc) throws GameActionException {
        MapLocation towerLocation = lastTower.getMapLocation();
        if (rc.canSenseRobotAtLocation(towerLocation) && rc.canSendMessage(towerLocation)) {
            Communication.sendMapInformation(rc, enemyTile, towerLocation);
            enemyTile = null;
            if (soldierState != storedState) {
                soldierState = storedState;
            } else {
                soldierState = SoldierState.STUCK;
            }
            Soldier.resetVariables();
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
            soldierState = SoldierState.EXPLORING;
            wanderTarget = null;
        }
    }

    /**
     * Marks ruins
     * Pathfinds to the ruins and fills in the area around the ruin if we can build a tower there
     */
    public static void fillInRuin(RobotController rc, MapLocation ruinLocation) throws GameActionException {
        // Mark the pattern we need to draw to build a tower here if we haven't already.
        // If robot has seen a paint tower, mark random tower
        if (seenPaintTower){
            Soldier.markRandomTower(rc, ruinLocation);
        } else {
            // Otherwise, mark a paint tower
            Soldier.markTower(rc, UnitType.LEVEL_ONE_PAINT_TOWER, ruinLocation);
        }
        // Move towards the ruin
        // NOTE: ORIGINALPATHFIND AUTOMATICALLY HANDLES ROTATION AROUND THE RUIN BC OF THE WAY IT WORKS
        // NOTE2: We should try and make bug1 work with this somehow
        Direction moveDir = Pathfinding.originalPathfind(rc, ruinLocation);
        if (moveDir != null) {
            rc.move(moveDir);
        }
        // Fill in any spots in the pattern with the appropriate paint.
        // Prioritize the tile under our own feet
        MapLocation newLocation = rc.getLocation();
        MapInfo currentTile = rc.senseMapInfo(newLocation);
        paintIfPossible(rc, currentTile);
        // Paint in another tile around the ruin
        MapInfo tileToPaint = Sensing.findPaintableTile(rc, ruinLocation,8);
        if (tileToPaint != null) {
            paintIfPossible(rc, tileToPaint);
        }
        // Tries to complete the ruin
        completeRuinIfPossible(rc, ruinLocation);
    }

    /**
     * Stuck behavior method
     */
    public static void stuckBehavior(RobotController rc) throws GameActionException {
        Direction newDir = Pathfinding.getUnstuck(rc);
        if (newDir != null) {
            rc.move(newDir);
            Soldier.paintIfPossible(rc, rc.getLocation());
        }
    }
}
