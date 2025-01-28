package v3;

import battlecode.common.*;

import static v3.RobotPlayer.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Class for all movement & pathfinding-related methods
 * All methods in this class should return a direction that a robot can move it (check sanity before returning)
 */
public class Pathfinding {
        // I think using stuff from other classes costs a ton of bytecode so im declaring this here
    public static int[][] directions = {{-2, -2}, {-2, 0}, {-2, 2}, {0, -2}, {0, 2}, {2, -2}, {2, 0}, {2, 2}};
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
        if (rc.getPaint() < 6){
            return paintedPathfind(rc, lastTower.getMapLocation());
        }
        return pathfind(rc, lastTower.getMapLocation());
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
            cumSum += 3*Sensing.countEmptyAround(rc, adjLocation.add(rc.getLocation().directionTo(adjLocation)));
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
     * How we choose exploration weights:
     * Check each of the 8 blocks around the robot
     * +20 if block is closer to target than starting point
     * +10 if block is equidistant to target than starting point
     * For each block, check the 3x3 area centered at that block
     * +3 for each paintable tile (including ruins)
     * -3 for each tile with an ally robot (including towers)
     *
     * if careAboutEnemy = true, +5 for enemy paint
     *
     * TODO: fine-tune parameters, perhaps introduce one for walls/impassible tiles/off the map
     */
    public static Direction betterExplore(RobotController rc, MapLocation curLocation, MapLocation target, boolean careAboutEnemy) throws GameActionException {
        // Only update intermediate target locations when we have reached one already or if we don't have one at all);
        if (intermediateTarget == null || curLocation.equals(intermediateTarget) ||
                (curLocation.isWithinDistanceSquared(intermediateTarget, 2))
                        && !rc.senseMapInfo(intermediateTarget).isPassable()) {
            if (curLocation.equals(intermediateTarget)){
                Soldier.resetVariables();
            }
            int cumSum = 0;
            // Calculate a score for each target
            int minScore = -1;
            int[] weightedAdjacent = new int[8];
            int curDistance = curLocation.distanceSquaredTo(target);
            //too lazy to loop unroll but entirely possible
            for (int i = 0; i < 8; i++) {
                int score = 0;
                MapLocation possibleTarget = curLocation.translate(directions[i][0], directions[i][1]);
                if (rc.onTheMap(possibleTarget)) {
                    score = Sensing.scoreTile(rc, possibleTarget, careAboutEnemy);
                    int newDistance = possibleTarget.distanceSquaredTo(target);
                    if (curDistance > newDistance) {
                        score += 20;
                    } else if (curDistance == newDistance) {
                        score += 10;
                    }
                }
                if (minScore == -1 || score < minScore) {
                    minScore = score;
                }
                cumSum += score;
                weightedAdjacent[i] = cumSum;
            }

            // Normalize by subtracting each score by the same amount so that one score is equal to 1
            // I love loop unrolling
            if (minScore != 0) minScore--;
            weightedAdjacent[0] -= minScore * 1;
            weightedAdjacent[1] -= minScore * 2;
            weightedAdjacent[2] -= minScore * 3;
            weightedAdjacent[3] -= minScore * 4;
            weightedAdjacent[4] -= minScore * 5;
            weightedAdjacent[5] -= minScore * 6;
            weightedAdjacent[6] -= minScore * 7;
            weightedAdjacent[7] -= minScore * 8;

            if (cumSum != 0) {
                int randomValue = Constants.rng.nextInt(weightedAdjacent[7]);
                for (int i = 0; i < 8; i++) {
                    if (randomValue < weightedAdjacent[i]) {
                        intermediateTarget = curLocation.translate(directions[i][0], directions[i][1]);
                        break;
                    }
                }
            }
        }
        if (intermediateTarget == null) {
            return null;
        }
        Direction moveDir = Pathfinding.pathfind(rc, intermediateTarget);
        if (moveDir != null) {
            return moveDir;
        }
        return null;
    }

    /**
     * Does a random walk
     */
    public static Direction randomWalk(RobotController rc) throws GameActionException {
        Direction[] allDirections = Direction.allDirections();
        for(int i = 0; i < 5; i++){
            Direction dir = allDirections[(int) (Math.random() * allDirections.length)];
            if (rc.canMove(dir) && !last8.contains(rc.getLocation().add(dir))) {
                return dir;
            }
        }

        return null;
    }


    /**
     * Finds the furthest corner and move towards it
     */
    public static Direction getUnstuck(RobotController rc) throws GameActionException{
        if (Math.random() < Constants.RANDOM_STEP_PROBABILITY){
            return randomWalk(rc);
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
    public static Direction betterUnstuck(RobotController rc) throws GameActionException {
        rc.setIndicatorString("GETTING UNSTUCK");
        intermediateTarget = null;
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
        return betterExplore(rc, rc.getLocation(), oppositeCorner, true);
    }
    /**
     * bug(?) pathfinding algorithm
     */
    public static Direction bugidk(RobotController rc, MapLocation target) throws GameActionException {
        if (!isTracing){
            //proceed as normal
            Direction dir = rc.getLocation().directionTo(target);
            if(rc.canMove(dir)){
                return dir;
            } else {
                if (rc.canSenseRobotAtLocation(rc.getLocation().add(dir))) {
                    if (Constants.rng.nextDouble() >= 0.8) {
                        //treat robot as passable 20% of the time
                        return null;
                    }
                }
                isTracing = true;
                tracingDir = dir;
                stoppedLocation = rc.getLocation();
                tracingTurns = 0;
            }
        } else {
            if ((Helper.isBetween(rc.getLocation(), stoppedLocation, target) && tracingTurns != 0)
                || tracingTurns > 2*(rc.getMapWidth() + rc.getMapHeight())) {
                Soldier.resetVariables();
            } else {
                // go along perimeter of obstacle
                if(rc.canMove(tracingDir)){
                    //move forward and try to turn right
                    Direction returnDir = tracingDir;
                    tracingDir = tracingDir.rotateRight();
                    tracingDir = tracingDir.rotateRight();
                    tracingTurns++;
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
                            tracingTurns++;
                            return returnDir;
                        }
                    }
                }
            }
        }
        return null;
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
                bug1Turns = 0;
            }
        } else{
            // tracing mode

            // need a stopping condition - this will be when we see the closestLocation again
            // TODO: 2 potential issues: 1. robot forces us to never get to closestLocation again
            //  2. robot doesn't move due to movement cooldowns and immediately thinks it rereached closestLocation
            if (rc.getLocation().equals(closestLocation) && bug1Turns != 0
                    || bug1Turns > 2*(rc.getMapWidth() + rc.getMapHeight())){
                // returned to closest location along perimeter of the obstacle
                Soldier.resetVariables();
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
                    bug1Turns++;
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
                            bug1Turns++;
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
            Soldier.resetVariables();
        }
        if (stuckTurnCount < 5 && !inBugNav){
            if (dist < closestPath){
                closestPath = dist;
            } else if (closestPath != -1){
                stuckTurnCount++;
            } else {
                closestPath = dist;
            }
            return lessOriginalPathfind(rc, target);
        }
        else if (inBugNav){
            // If robot has made it across the wall to the other side
            // Then, just pathfind to the place we are going to
            if (rc.getLocation().distanceSquaredTo(acrossWall) == 0){
                acrossWall = null;
                inBugNav = false;
                closestPath = -1;
                return null;
            }
            // Otherwise, just call bugnav
            return bug1(rc, acrossWall);
        }
        else {
            inBugNav = true;
            stuckTurnCount = 0;
            Direction toTarget = curLocation.directionTo(target);
            MapLocation newLoc = curLocation.add(toTarget);
            if (rc.canSenseLocation(newLoc)){
                if (rc.senseMapInfo(newLoc).isWall()){
                    newLoc = newLoc.add(toTarget);
                    if (rc.canSenseLocation(newLoc)){
                        if (rc.senseMapInfo(newLoc).isWall()) {
                            newLoc = newLoc.add(toTarget);
                            if (rc.canSenseLocation(newLoc)) {
                                if (!rc.senseMapInfo(newLoc).isWall()) {
                                    acrossWall = newLoc;
                                    return null;
                                }
                            }
                        }
                        else{
                            acrossWall = newLoc;
                            return null;
                        }
                    }
                }
                else{
                    acrossWall = newLoc;
                    return null;
                }
            }
            acrossWall = target;
            return null;
        }
    }

    public static Direction randomPaintedWalk(RobotController rc) throws GameActionException{
        List<MapInfo> allDirections = Sensing.getMovablePaintedTiles(rc);
        if (allDirections.isEmpty()){
            return null;
        }
        Direction dir = rc.getLocation().directionTo(allDirections.get((int) (Math.random() * allDirections.size())).getMapLocation());
        if (rc.canMove(dir)) {
            return dir;
        }
        return null;
    }
}
