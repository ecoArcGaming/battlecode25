package v2;

import battlecode.common.*;

import static v2.RobotPlayer.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Class for all movement & pathfinding-related methods
 * All methods in this class should return a direction that a robot can move it (check sanity before returning)
 */
public class Pathfinding {
    /**
     * Returns a Direction that brings rc closer to target
     * Prioritizes distance first, then type of paint (ally tiles, then neutral tiles, then enemy tiles)
     * Exception: does not move onto a tile if doing so will kill itself
     * If the robot cannot move, return null
     */
    public static Direction lessOriginalPathfind(RobotController rc, MapLocation target) throws GameActionException {
        int minDistance = -1;
        PaintType bestPaintType = PaintType.EMPTY;
        MapLocation curLocation = rc.getLocation();
        MapInfo bestLocation = null;
        for (Direction dir: Constants.directions) {
            if (rc.canMove(dir)) {
                MapInfo adjLocation = rc.senseMapInfo(curLocation.add(dir));
                int distance = adjLocation.getMapLocation().distanceSquaredTo(target);
                PaintType adjType = adjLocation.getPaint();
                if ((distance < minDistance || minDistance == -1)) {
                    minDistance = distance;
                    bestPaintType = adjType;
                    bestLocation = adjLocation;
                } else if (distance == minDistance) {
                    PaintType adjPaintType = adjLocation.getPaint();
                    if ((bestPaintType.isEnemy() && !adjPaintType.isEnemy() ||
                            bestPaintType == PaintType.EMPTY && adjPaintType.isAlly())) {
                        bestPaintType = adjLocation.getPaint();
                        bestLocation = adjLocation;
                    }
                }
            }
        }
        if (minDistance != -1) {
            return curLocation.directionTo(bestLocation.getMapLocation());
        } else {
            return null;
        }
    }

    /**
     * Returns a Direction that brings rc closer to target
     * Prioritizes going along the three closest directions pointing to the target
     * Then, it finds any painted tile adjacent to the robot
     * Then, it just finds any tile adjacent to the robot that the robot can move on and null otherwise
     */
    public static Direction originalPathfind(RobotController rc, MapLocation target) throws GameActionException {
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
            if (rc.canMove(dir) && !last8.contains(rc.getLocation().add(currDir))) {
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
                if (rc.senseMapInfo(rc.getLocation().add(dir)).getPaint().isAlly() && !last8.contains(rc.getLocation().add(currDir))) {
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
        if (rc.getRoundNum() > Math.min(rc.getMapHeight(), rc.getMapWidth()) * 2){ // Change in future for converting to regular pathfind
            return pathfind(rc, lastTower.getMapLocation());
        } else{
            return paintedPathfind(rc, lastTower.getMapLocation());
        }
    }

    /**
     * Given an ArrayList of tiles to move to, randomly chooses a tile, weighted by how many tiles are unpainted & unoccupied
     * in the 3x3 area centered at the tile behind the tile (relative to the robot)
     * Returns null if everything appears painted or if validAdjacent is empty
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
            return null;
        } else {
            int randomValue = Constants.rng.nextInt(cumSum);
            for (int i = 0; i < numTiles; i++) {
                if (randomValue < weightedAdjacent[i]){
                    return validAdjacent.get(i).getMapLocation();
                }
            }
        }
        return null;
    }

    /**
     * Returns a Direction representing the direction of an unpainted block
     * Smartly chooses an optimal direction among adjacent, unpainted tiles using the method tiebreakUnpainted
     * If all surrounding blocks are painted, looks past those blocks (ignoring passability of adjacent tiles)
     *      and pathfinds to a passable tile, chosen by tiebreakUnpainted
     */
    public static Direction exploreUnpainted(RobotController rc) throws GameActionException {
        List<MapInfo> validAdjacent = Sensing.getMovableEmptyTiles(rc);
        if (validAdjacent.isEmpty()){
            MapLocation curLoc = rc.getLocation();
            for (Direction dir: Constants.directions) {
                MapLocation fartherLocation = curLoc.add(dir);
                if (rc.onTheMap(fartherLocation)) {
                    MapInfo fartherInfo = rc.senseMapInfo(fartherLocation);
                    if (fartherInfo.isPassable()) {
                        validAdjacent.add(fartherInfo);
                    }
                }
            }
        }
        MapLocation bestLocation = tiebreakUnpainted(rc, validAdjacent);
        if (bestLocation == null) {
            return null;
        }
        Direction moveDir = Pathfinding.pathfind(rc, bestLocation);
        if (moveDir != null) {
            return moveDir;
        }
        return null;
    }

    /**
     * Finds the furthest corner and move towards it
     */
    public static Direction getUnstuck(RobotController rc) throws GameActionException{
        if (Math.random() < Constants.RANDOM_STEP_PROBABILITY){
            Direction[] allDirections = Direction.allDirections();
            int index = (int) (Math.random() * allDirections.length);
            Direction dir = allDirections[index];
            if (rc.canMove(dir)) {
                return dir;
            }
            return null;
        }
        else {
            if (oppositeCorner == null || rc.getLocation().distanceSquaredTo(oppositeCorner) <= 20) {
                int x = rc.getLocation().x;
                int y = rc.getLocation().y;
                int target_x, target_y;
                if (x < rc.getMapWidth() / 2) {
                    target_x = rc.getMapWidth();
                } else {
                    target_x = 0;
                }
                if (y < rc.getMapHeight() / 2) {
                    target_y = rc.getMapHeight();
                } else {
                    target_y = 0;
                }
                oppositeCorner = new MapLocation(target_x, target_y);
            }
            return pathfind(rc, oppositeCorner);
        }
    }

    /**
     * bug1 pathfinding algorithm
     */
    public static Direction bug1(RobotController rc, MapLocation target) throws GameActionException{
        if (!isTracing){
            //proceed as normal
            Direction dir = rc.getLocation().directionTo(target);
            if(rc.canMove(dir)){
                return dir;
            } else {
                isTracing = true;
                tracingDir = dir;
            }
        } else{
            // tracing mode

            // need a stopping condition - this will be when we see the closestLocation again
            // TODO: 2 potential issues: 1. robot forces us to never get to closestLocation again
            //  2. robot doesn't move due to movement cooldowns and immediately thinks it rereached closestLocation
            if (rc.getLocation().equals(closestLocation)){
                // returned to closest location along perimeter of the obstacle
                isTracing = false;
                smallestDistance = 10000000;
                closestLocation = null;
                tracingDir= null;
            } else {
                // keep tracing

                // update closestLocation and smallestDistance
                int distToTarget = rc.getLocation().distanceSquaredTo(target);
                if(distToTarget < smallestDistance){
                    smallestDistance = distToTarget;
                    closestLocation = rc.getLocation();
                }

                // go along perimeter of obstacle
                if(rc.canMove(tracingDir)){
                    //move forward and try to turn right
                    Direction returnDir = tracingDir;
                    tracingDir = tracingDir.rotateRight();
                    tracingDir = tracingDir.rotateRight();
                    return returnDir;
                }
                else{
                    // turn left because we cannot proceed forward
                    // keep turning left until we can move again
                    for (int i=0; i<8; i++){
                        tracingDir = tracingDir.rotateLeft();
                        if(rc.canMove(tracingDir)){
                            Direction returnDir = tracingDir;
                            tracingDir = tracingDir.rotateRight();
                            tracingDir = tracingDir.rotateRight();
                            return returnDir;
                        }
                    }
                }
            }
        }
        return null;
    }

    public static Direction pathfind(RobotController rc, MapLocation target) throws GameActionException{
        MapLocation curLocation = rc.getLocation();
        int dist = curLocation.distanceSquaredTo(target);
        if (dist == 0){
            stuckTurnCount = 0;
            closestPath = -1;
        }
        if (stuckTurnCount < 5){
            if (dist < closestPath){
                closestPath = dist;
            } else if (closestPath != -1){
                closestPath = dist;
                stuckTurnCount++;
            } else {
                closestPath = dist;
            }
            return lessOriginalPathfind(rc, target);
        } else {
            return bug1(rc, target);
        }
    }
}
