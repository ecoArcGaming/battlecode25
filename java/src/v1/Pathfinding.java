package v1;

import battlecode.common.*;

import java.util.ArrayList;
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
        return paintedPathfind(rc, RobotPlayer.lastTower.getMapLocation());
    }

    /**
     * Given an ArrayList of tiles to move to, randomly chooses a tile, weighted by how many tiles are unpainted & unoccupied
     * in the 3x3 area centered at the tile behind the tile (relative to the robot)
     */
    public static MapLocation tiebreakUnpainted(RobotController rc, List<MapInfo> validAdjacent) throws GameActionException{
        int cumSum = 0;
        int numTiles = validAdjacent.size();
        int[] weightedAdjacent = new int[numTiles];
        for (int i = 0; i < numTiles; i++){
            MapLocation adjLocation = validAdjacent.get(i).getMapLocation();
            cumSum += Sensing.countEmptyAround(rc, adjLocation.add(rc.getLocation().directionTo(adjLocation)));
            weightedAdjacent[i] = cumSum;
        }
        if (cumSum == 0) {
            return rc.getLocation().add(Constants.directions[Constants.rng.nextInt(Constants.directions.length)]);
        } else {
            int randomValue = Constants.rng.nextInt(cumSum);
            for (int i = 0; i < numTiles; i++) {
                if (randomValue < weightedAdjacent[i]){
                    return validAdjacent.get(i).getMapLocation();
                }
            }
        }
        return rc.getLocation().add(Constants.directions[Constants.rng.nextInt(Constants.directions.length)]);
    }

    /**
     * Returns a Direction representing the direction of an unpainted block
     * Smartly chooses an optimal direction among adjacent, unpainted tiles using the method tiebreakUnpainted
     * If all surrounding blocks are painted, apply tiebreakUnpainted
     */
    public static Direction exploreUnpainted(RobotController rc) throws GameActionException {
        List<MapInfo> validAdjacent = Sensing.getMovableEmptyTiles(rc);
        if (!validAdjacent.isEmpty()){
            /* Previous code where we choose an adjacent unpainted block at random
            MapInfo nextLoc = validAdjacent.get(Constants.rng.nextInt(validAdjacent.size()));
            Direction moveDir = rc.getLocation().directionTo(nextLoc.getMapLocation());
            if (rc.canMove(moveDir)) {
                return moveDir;
            }
            */
        } else {
            MapLocation curLoc = rc.getLocation();
            for (Direction dir: Constants.directions) {
                if (rc.canMove(dir)) {
                    validAdjacent.add(rc.senseMapInfo(curLoc.add(dir)));
                }
            }

        }
        Direction moveDir = rc.getLocation().directionTo(tiebreakUnpainted(rc, validAdjacent));
        if (rc.canMove(moveDir)) {
            return moveDir;
        }
        return null;
    }

    /**
     * Finds the furthest corner and move towards it
     */
    public static Direction getUnstuck(RobotController rc) throws GameActionException{
        if (!RobotPlayer.isStuck) {
            RobotPlayer.isStuck = true;
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
            RobotPlayer.oppositeCorner = new MapLocation(target_x, target_y);
        }
        return paintedPathfind(rc, RobotPlayer.oppositeCorner);
    }

}
