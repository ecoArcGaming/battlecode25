package v2;

import battlecode.common.*;

import java.util.*;

public class Sensing {
    /**
     *  Finds the opponent robots within actionRadius with the lowest HP and returns its RobotInfo
     */
    public static RobotInfo findNearestLowestHP(RobotController rc) throws GameActionException {
        RobotInfo[] nearbyRobots = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        RobotInfo targetRobot = null;
        int minHealth = -1;
        for (RobotInfo robot: nearbyRobots) {
            int robotHealth = robot.getHealth();
            if (minHealth == -1 || minHealth > robotHealth) {
                targetRobot = robot;
                minHealth = robotHealth;
            }
        }
        return targetRobot;
    }

    /**
     * Given the MapLocation of a ruin, check if that ruin has any spots needing to be filled in within vision
     * Needs to be filled is defined as having empty paint or incorrect allied paint
     * Returns True if there are blocks that can be painted to still be painted, False if otherwise.
     * Purpose: Check if we need to pathfind to this tower to fill in the tower pattern
     */
    public static boolean needFilling(RobotController rc, MapLocation towerLocation) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(towerLocation, 8)){
            if (!patternTile.hasRuin() && (patternTile.getPaint() == PaintType.EMPTY ||
                    patternTile.getPaint().isAlly() && patternTile.getMark() != patternTile.getPaint())){
                return true;
            }
        }
        return false;
    }

    /**
     * Given the MapLocation of a ruin, check if the pattern is correct for a tower to be built
     *      and if there is no tower there currently
     * Returns False if the pattern is incorrect, there are no markers, there is enemy paint,
     *      or if there is a tower already existing
     * Purpose: Check of a tower can be built ignoring our coin amount
     */
    public static boolean canBuildTower(RobotController rc, MapLocation towerLocation) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(towerLocation, 8)){
            if (patternTile.hasRuin()) {
                if (rc.canSenseRobotAtLocation(patternTile.getMapLocation())) {
                    return false;
                }
            } else if ((patternTile.getMark() == PaintType.EMPTY
                    || patternTile.getMark() != patternTile.getPaint()
                    || patternTile.getPaint().isEnemy())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the closest ruin without a tower and returns a MapLocation corresponding to that ruin
     * Returns null if no unoccupied ruins are found
     */
    public static MapInfo findClosestRuin(RobotController rc, MapLocation robotLocation, MapInfo[] nearbyTiles) throws GameActionException {
        MapInfo curRuin = null;
        int minDis = -1;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation tileLocation = tile.getMapLocation();
                if (!rc.canSenseRobotAtLocation(tileLocation)) {
                    // Check distance among ruins that need filling
                    int ruinDistance = robotLocation.distanceSquaredTo(tileLocation);
                    if (minDis == -1 || minDis > ruinDistance) {
                        curRuin = tile;
                        minDis = ruinDistance;
                    }
                }
            }
        }
        return curRuin;
    }

    /**
     * Finds a paintable tile that is within a specific range of location and returns the MapInfo of that tile
     * Paintable: empty paint or incorrect allied paint
     * If none are found, return null
     */
    public static MapInfo findPaintableTile(RobotController rc, MapLocation location, int range) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(location, range)){
            if (rc.canPaint(patternTile.getMapLocation()) &&
                    (patternTile.getPaint() == PaintType.EMPTY ||
                            patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY)) {
                return patternTile;
            }
        }
        return null;
    }

    /**
     * Finds tiles adjacent to rc that
     *      1. Can be moved to
     *      2. Have no paint on them
     *      3. Hasn't been at this tile in the last 8 tiles it has moved to
     * Returns an ArrayList of MapInfo for these tiles
     */
    public static List<MapInfo> getMovableEmptyTiles(RobotController rc) throws GameActionException{
        MapInfo[] adjacentTiles = rc.senseNearbyMapInfos(2);
        List<MapInfo> validAdjacent = new ArrayList<>();
        for (MapInfo adjacentTile: adjacentTiles){
            if (adjacentTile.getPaint() == PaintType.EMPTY && adjacentTile.isPassable() &&
                    !RobotPlayer.last8.contains(adjacentTile.getMapLocation())) {
                validAdjacent.add(adjacentTile);
            }
        }
        return validAdjacent;
    }


    /**
     * Returns RobotInfo of a tower if there is a tower with a range of radius
     * ally = true: search for allied towers, and vice versa
     * If ally is not passed, then we search for all towers
     * Returns null if no tower is within range
     */
    public static RobotInfo towerInRange(RobotController rc, int range) throws GameActionException {
        RobotInfo[] robotsInRange = rc.senseNearbyRobots(range);
        for (RobotInfo robot: robotsInRange) {
            if (robot.getType().isTowerType()) {
                return robot;
            }
        }
        return null;
    }

    public static RobotInfo towerInRange(RobotController rc, int range, boolean ally) throws GameActionException {
        RobotInfo[] robotsInRange = null;
        if (ally) {
            robotsInRange = rc.senseNearbyRobots(range, rc.getTeam());
        } else {
            robotsInRange = rc.senseNearbyRobots(range, rc.getTeam().opponent());
        }
        for (RobotInfo robot: robotsInRange) {
            if (robot.getType().isTowerType()) {
                return robot;
            }
        }
        return null;
    }

    /**
     * Returns map info of location of enemy paint
     */
    public static MapInfo findEnemyPaint(RobotController rc, MapInfo[] nearbyTiles) throws GameActionException {
        for (MapInfo tile: nearbyTiles) {
            if (tile.getPaint().isEnemy()){
                return tile;
            }
        }
        return null;
    }

    /**
     * Counts the number of empty, passable tiles in a 3x3 area centered at center, assuming it is all visible
     */
    public static int countEmptyAround(RobotController rc, MapLocation center) throws GameActionException {
        MapInfo[] surroundingTiles = rc.senseNearbyMapInfos(center, 2);
        int count = 0;
        for (MapInfo surroundingTile: surroundingTiles) {
            if (surroundingTile.getPaint() == PaintType.EMPTY && surroundingTile.isPassable()
                    && !rc.canSenseRobotAtLocation(surroundingTile.getMapLocation())) {
                count++;
            }
        }
        return count;
    }

    // Checks if a Robot is a tower or robot by ID
    public static boolean isRobot(RobotController rc, int robotId)  throws GameActionException {
        if (rc.canSenseRobot(robotId)) {
            RobotInfo bot = rc.senseRobot(robotId);
            return bot.getType().isRobotType();
        }
        else {
            return false;
        }
    }

    public static boolean isTower(RobotController rc, int robotId) throws GameActionException {
        if (rc.canSenseRobot(robotId)) {
            RobotInfo bot = rc.senseRobot(robotId);
            return bot.getType().isTowerType();
        }
        else {
            return false;
        }
    }

    public static ArrayList<MapInfo> getNearByEnemiesSortedShuffled(RobotController rc) throws GameActionException {
        ArrayList<MapInfo> nearbyEnemies = new ArrayList<>();
        List<MapInfo> enemies = Arrays.asList(rc.senseNearbyMapInfos());
//        Collections.shuffle(enemies);
        for (MapInfo enemy: enemies){
            if (enemy.getPaint().isEnemy()){
                nearbyEnemies.add(enemy);
            }
            if (enemy.getPaint() == PaintType.EMPTY && !enemy.hasRuin() && !enemy.isWall()){
                RobotPlayer.fillEmpty = enemy;
            }
        }
//        Collections.shuffle(nearbyEnemies);
        Collections.sort(nearbyEnemies, new MapInfoDistanceComparator(rc).reversed() );
        return nearbyEnemies;
    }
}
