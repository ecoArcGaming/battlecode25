package v1;

import battlecode.common.*;

import java.util.List;

/**
 * Class for all movement & pathfinding-related methods
 * All methods in this class should return a direction that a robot can move it (check sanity before returning)
 */
public class Pathfinding {
    /**
     * Returns a Direction that brings rc closer to target, regardless of its painted or not
     * Prioritizes going along the three closest directions pointing to the target
     * Then, it finds any painted tile adjacent to the robot
     * Then, it just finds any tile adjacent to the robot that the robot can move on and null otherwise
     */
    public static Direction pathfind(RobotController rc, MapLocation target) throws GameActionException {
        Direction currDir = rc.getLocation().directionTo(target);
        Direction left = currDir.rotateLeft();
        Direction right = currDir.rotateRight();
        if (rc.canMove(currDir)) {
            return currDir;
        }
        else if (rc.canMove(left)) {
            return left;
        }
        else if (rc.canMove(right)) {
            return right;
        }

        Direction[] allDirections = Direction.allDirections();
        for (Direction dir: allDirections){
            if (rc.canMove(dir) && !RobotPlayer.last8.contains(rc.getLocation().add(currDir))) {
                    return dir;
            }
        }

        for (Direction dir: allDirections){
            if (rc.canMove(dir)) {
                return dir;
            }
        }

        return null;
    }

    /**
     * Returns a Direction that brings rc closer to target, going along painted areas
     * Prioritizes going along the three closest directions pointing to the target
     * Then, it finds any painted tile adjacent to the robot
     * Then, it just finds any tile adjacent to the robot that the robot can move on and null otherwise
     */
    public static Direction paintedPathfind(RobotController rc, MapLocation target) throws GameActionException{
        Direction currDir = rc.getLocation().directionTo(target);
        Direction left = currDir.rotateLeft();
        Direction right = currDir.rotateRight();
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
                if (rc.senseMapInfo(rc.getLocation().add(dir)).getPaint().isAlly() && !RobotPlayer.last8.contains(rc.getLocation().add(currDir))) {
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

    /**
     * Returns a Direction representing the direction to move to the closest tower in vision or the last one remembered
     */
    public static Direction returnToTower(RobotController rc) throws GameActionException{
        int minDistance = -1;
        MapInfo closestTower = null;
        MapLocation curLocation = rc.getLocation();
        for (MapInfo loc: rc.senseNearbyMapInfos()){;
            int distance = curLocation.distanceSquaredTo(loc.getMapLocation());
            if(Robot.checkTower(rc, loc) && (minDistance == -1 || distance < minDistance)){
                minDistance = distance;
                closestTower = loc;
            }
        }
        if (closestTower == null) {
            closestTower = RobotPlayer.lastTower;
        }
        return paintedPathfind(rc, closestTower.getMapLocation());
    }

    /**
     * Returns a random Direction representing the direction of an unpainted block
     * If all adjacent blocks are painted, then return a random direction
     */
    public static Direction exploreUnpainted(RobotController rc) throws GameActionException {
        List<MapInfo> validAdjacent = Sensing.getMovableEmptyTiles(rc);
        if (!validAdjacent.isEmpty()){
            MapInfo nextLoc = validAdjacent.get(Constants.rng.nextInt(validAdjacent.size()));
            Direction moveDir = rc.getLocation().directionTo(nextLoc.getMapLocation());
            if (rc.canMove(moveDir)) {
                return moveDir;
            }
        } else {
            Direction moveDir = Constants.directions[Constants.rng.nextInt(Constants.directions.length)];
            if (rc.canMove(moveDir)){
                return moveDir;
            }
        }
        return null;
    }

    /**
     * Finds the furthest corner and move towards it
     */
    public static Direction getUnstuck(RobotController rc) throws GameActionException{
        int x = rc.getLocation().x;
        int y = rc.getLocation().y;
        int target_x, target_y;
        if (x < rc.getMapWidth()/2){
            target_x = rc.getMapWidth();
        } else {
            target_x = 0;
        }
        if (y < rc.getMapHeight()/2){
            target_y = rc.getMapHeight();
        } else {
            target_y = 0;
        }
        return paintedPathfind(rc, new MapLocation(target_x, target_y));
    }
}
