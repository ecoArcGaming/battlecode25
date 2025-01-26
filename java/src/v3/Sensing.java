package v3;

import battlecode.common.*;

import static v3.RobotPlayer.*;
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
     * Given the MapLocation of a ruin, check if we can eventually build a tower at the ruin
     * Returns False if there is enemy paint, or if there is a tower already existing
     * Purpose: Check if we should go to this ruin to build on it
     */
    public static boolean canBuildTower(RobotController rc, MapLocation towerLocation) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(towerLocation, 8)){
            if (patternTile.hasRuin()) {
                if (rc.canSenseRobotAtLocation(patternTile.getMapLocation())) {
                    return false;
                }
            } else if (patternTile.getPaint().isEnemy()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds the closest ruin that fits the following criteria
     * 1. No enemy paint around the tower
     * 2. No tower at the ruin
     * 3. No ally robots directly adjacent to the ruin
     */
    public static MapInfo findBestRuin(RobotController rc, MapLocation robotLocation, MapInfo[] nearbyTiles) throws GameActionException {
        MapInfo curRuin = null;
        int minDis = -1;
        for (MapInfo tile : nearbyTiles) {
            if (tile.hasRuin()) {
                MapLocation tileLocation = tile.getMapLocation();
                if (!rc.canSenseRobotAtLocation(tileLocation) && Sensing.canBuildTower(rc, tileLocation)
                && rc.senseNearbyRobots(tileLocation, 2, rc.getTeam()).length < 1) {
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
    public static MapInfo findPaintableTile(RobotController rc, MapLocation location, int rangeSquared) throws GameActionException {
        for (MapInfo patternTile : rc.senseNearbyMapInfos(location, rangeSquared)){
            if (rc.canPaint(patternTile.getMapLocation()) &&
                    (patternTile.getPaint() == PaintType.EMPTY ||
                            patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY)) {
                return patternTile;
            }
        }
        return null;
    }

    /**
     * Finds a paintable tile that is within a specific range of tower and returns the MapInfo of that tile
     * Paintable: tile with paint different than needed
     * If none are found, return null
     */
    public static int[] findPaintableRuinTile(RobotController rc, MapLocation ruinLocation, PaintType[][] ruinPattern) throws GameActionException {
        // Iterate through the 5x5 area around a ruin
        for(int i = -2; i < 3; i++){
            for (int j = -2; j < 3; j++){
                MapLocation patternTile = ruinLocation.translate(i, j);
                if (rc.canPaint(patternTile) && ruinPattern[i+2][j+2] != rc.senseMapInfo(patternTile).getPaint()){
                    return new int[]{i, j};
                }
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
                    !last8.contains(adjacentTile.getMapLocation())) {
                validAdjacent.add(adjacentTile);
            }
        }
        return validAdjacent;
    }

    /**
     * Finds tiles adjacent to rc that
     *      1. Can be moved to
     *      2. Has paint on them
     *      3. Hasn't been at this tile in the last 8 tiles it has moved to
     * Returns an ArrayList of MapInfo for these tiles
     */
    public static List<MapInfo> getMovablePaintedTiles(RobotController rc) throws GameActionException{
        MapInfo[] adjacentTiles = rc.senseNearbyMapInfos(2);
        List<MapInfo> validAdjacent = new ArrayList<>();
        for (MapInfo adjacentTile: adjacentTiles){
            if (adjacentTile.getPaint().isAlly() && adjacentTile.isPassable() &&
                    !last8.contains(adjacentTile.getMapLocation())) {
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

    public static MapInfo getNearByEnemiesSortedShuffled(RobotController rc) throws GameActionException {
        ArrayList<MapInfo> nearbyEnemies = new ArrayList<>();
        List<MapInfo> enemies = Arrays.asList(rc.senseNearbyMapInfos());
        for (MapInfo enemy: enemies){
            if (enemy.getPaint().isEnemy()){
                nearbyEnemies.add(enemy);
            }
            if (enemy.getPaint() == PaintType.EMPTY && !enemy.hasRuin() && !enemy.isWall()){
                fillEmpty = enemy;
            }
        }
        if (nearbyEnemies.isEmpty()) {
            return null;
        }
        return Collections.max(nearbyEnemies, new MapInfoDistanceComparator(rc));
    }

    // scores tiles that decides where a splasher should go
    public static MapInfo scoreSplasherTiles(RobotController rc) throws GameActionException {

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        // hash the tiles
        for (MapInfo tile: nearbyTiles) {
            MapLocation loc = tile.getMapLocation();
            PaintType paint = tile.getPaint();
            if (paint == null){
                continue;
            }

            if (rc.senseRobotAtLocation(loc) != null && !rc.senseRobotAtLocation(loc).getTeam().isPlayer() && rc.senseRobotAtLocation(loc).getType().isTowerType()) {
                currGrid[loc.x][loc.y] = -30;
            }
            else if (paint == PaintType.EMPTY && tile.isPassable()) {
                currGrid[loc.x][loc.y] = 0;
            } else if (paint.isEnemy()){
                currGrid[loc.x][loc.y] = 1;
            } else if (paint.isAlly()){
                currGrid[loc.x][loc.y] = -1;
            } else {
                currGrid[loc.x][loc.y] = -1;
            }
        }
        MapInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MapInfo tile: nearbyTiles) {
            if (tile.isPassable() && rc.canAttack(tile.getMapLocation())) {
                int score = ScoreSplash(rc, tile);
                if (score > bestScore) {
                    bestScore = score;
                    best = tile;
                }
            }

        }
        if (bestScore < -2){
            return null;
        }
        return best;
    }

    // scores based on paintTypes of tiles within splasher radius
    public static int ScoreSplash(RobotController rc,  MapInfo tile) throws GameActionException {
        int out = 0;
        MapLocation loc = tile.getMapLocation();
        int x = loc.x;
        int y = loc.y;
        int up = rc.getMapHeight();
        int right = rc.getMapWidth();
        if (1 < y ) {
            out += currGrid[x][y-2];
        }
        if (0 < x && 0 < y){
            out += currGrid[x-1][y-1];
        }
        if (0 < y){
            out += currGrid[x][y-1];
        }
        if (x < right - 1 && y > 0) {
            out += currGrid[x+1][y-1];
        }
        if (1 < x ){
            out += currGrid[x-2][y];
        }
        if (0 < x){
            out += currGrid[x-1][y];
        }
        out += currGrid[x][y];
        if (x < right -1 ){
            out += currGrid[x+1][y];
        }
        if (x < right - 2){
            out += currGrid[x+2][y];
        }
        if (x > 0 && y < up -1){
            out += currGrid[x-1][y+1];
        }
        if (y < up -1){
            out += currGrid[x][y+1];
        }
        if (x < right -1 && y < up -1){
            out += currGrid[x+1][y+1];
        }
        if (y < up - 2){
            out += currGrid[x][y+2];
        }

//        out += map.getOrDefault(new HashableCoords(x, y-1), 0);
//        out += map.getOrDefault(new HashableCoords(x-1, y-1), 0);
//        out += map.getOrDefault(new HashableCoords(x, y-1), 0);
//        out += map.getOrDefault(new HashableCoords(x+1, y-1), 0);
//        out += map.getOrDefault(new HashableCoords(x-2, y), 0);
//        out += map.getOrDefault(new HashableCoords(x-1, y), 0);
//        out += map.getOrDefault(new HashableCoords(x, y), 0);
//        out += map.getOrDefault(new HashableCoords(x+1, y), 0);
//        out += map.getOrDefault(new HashableCoords(x+2, y), 0);
//        out += map.getOrDefault(new HashableCoords(x-1, y+1), 0);
//        out += map.getOrDefault(new HashableCoords(x, y+1), 0);
//        out += map.getOrDefault(new HashableCoords(x+1, y+1), 0);
//        out += map.getOrDefault(new HashableCoords(x, y+2), 0);

        return out;
    }

    /**
     * Checks whether a ruin is in the middle area of the map, in which a defense tower is built
     */
    public static boolean isInDefenseRange(RobotController rc, MapLocation ruinLoc){
        int xLower = (int) (rc.getMapWidth() * Constants.DEFENSE_RANGE);
        int yLower = (int) (rc.getMapHeight() * Constants.DEFENSE_RANGE);
        int xHigher = (int) (rc.getMapWidth() * (1-Constants.DEFENSE_RANGE));
        int yHigher = (int) (rc.getMapHeight() * (1-Constants.DEFENSE_RANGE));
        int x = ruinLoc.x;
        int y = ruinLoc.y;
        if (x >= xLower && x <= xHigher && y >= yLower && y <= yHigher) {
            return true;
        }
        return false;
    }

    public static int scoreTile(RobotController rc, MapLocation tile) throws GameActionException {
        MapInfo[] surroundingTiles = rc.senseNearbyMapInfos(tile, 2);
        int count = 30;
        for (MapInfo surroundingTile: surroundingTiles) {
            if (surroundingTile.getPaint() == PaintType.EMPTY && surroundingTile.isPassable()) {
                count += 3;
            }
            if (!surroundingTile.isPassable()) {
                count -= 2;
            }
            MapLocation surroundingLocation = surroundingTile.getMapLocation();
            if (rc.canSenseRobotAtLocation(surroundingLocation)) {
                if (rc.senseRobotAtLocation(surroundingLocation).getTeam() == rc.getTeam()) {
                    count -= 3;
                }
            }
        }
        return count;
    }
}
