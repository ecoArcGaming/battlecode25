package doNothing;

import battlecode.common.*;

public class BugNavigator {
    private static RobotController rc;
    private static MapLocation currentTarget;
    private static int minDistanceToTarget;
    private static boolean obstacleOnRight;
    private static MapLocation currentObstacle;
    private static boolean[] visitedStates;

    public static void init(RobotController robotController) {
        rc = robotController;
        reset();
    }

    public static void moveTo(MapLocation target) throws GameActionException {
        if (currentTarget == null || !currentTarget.equals(target)) {
            reset();
        }

        boolean hasOptions = false;
        for (Direction dir : Direction.allDirections()) {
            if (canMove(dir)) {
                hasOptions = true;
                break;
            }
        }

        if (!hasOptions) {
            return;
        }

        MapLocation myLocation = rc.getLocation();

        int distanceToTarget = myLocation.distanceSquaredTo(target);
        if (distanceToTarget < minDistanceToTarget) {
            reset();
            minDistanceToTarget = distanceToTarget;
        }

        if (currentObstacle != null && rc.canSenseLocation(currentObstacle) && !rc.isLocationOccupied(currentObstacle)) {
            reset();
        }

        if (!addVisitedState(getState(target))) {
            reset();
        }

        currentTarget = target;

        if (currentObstacle == null) {
            Direction forward = myLocation.directionTo(target);
            if (canMove(forward)) {
                move(forward);
                return;
            }

            setInitialDirection();
        }

        followWall(true);
    }

    public static void reset() {
        currentTarget = null;
        minDistanceToTarget = Integer.MAX_VALUE;
        obstacleOnRight = true;
        currentObstacle = null;
        visitedStates = new boolean[65536]; // 2^16 possible states
    }

    private static void setInitialDirection() throws GameActionException {
        MapLocation myLocation = rc.getLocation();
        Direction forward = myLocation.directionTo(currentTarget);

        Direction left = forward.rotateLeft();
        for (int i = 8; --i >= 0; ) {
            MapLocation location = myLocation.add(left);
            if (rc.onTheMap(location) && !rc.isLocationOccupied(location)) {
                break;
            }
            left = left.rotateLeft();
        }

        Direction right = forward.rotateRight();
        for (int i = 8; --i >= 0; ) {
            MapLocation location = myLocation.add(right);
            if (rc.onTheMap(location) && !rc.isLocationOccupied(location)) {
                break;
            }
            right = right.rotateRight();
        }

        MapLocation leftLocation = myLocation.add(left);
        MapLocation rightLocation = myLocation.add(right);

        int leftDistance = leftLocation.distanceSquaredTo(currentTarget);
        int rightDistance = rightLocation.distanceSquaredTo(currentTarget);

        if (leftDistance < rightDistance) {
            obstacleOnRight = true;
        } else if (rightDistance < leftDistance) {
            obstacleOnRight = false;
        } else {
            obstacleOnRight = myLocation.distanceSquaredTo(leftLocation) < myLocation.distanceSquaredTo(rightLocation);
        }

        if (obstacleOnRight) {
            currentObstacle = myLocation.add(left.rotateRight());
        } else {
            currentObstacle = myLocation.add(right.rotateLeft());
        }
    }

    private static void followWall(boolean canRotate) throws GameActionException {
        Direction direction = rc.getLocation().directionTo(currentObstacle);

        for (int i = 8; --i >= 0; ) {
            direction = obstacleOnRight ? direction.rotateLeft() : direction.rotateRight();
            if (canMove(direction)) {
                move(direction);
                return;
            }

            MapLocation location = rc.getLocation().add(direction);
            if (canRotate && !rc.onTheMap(location)) {
                obstacleOnRight = !obstacleOnRight;
                followWall(false);
                return;
            }

            if (rc.onTheMap(location) && rc.isLocationOccupied(location)) {
                currentObstacle = location;
            }
        }
    }

    private static char getState(MapLocation target) {
        MapLocation myLocation = rc.getLocation();
        Direction direction = myLocation.directionTo(currentObstacle != null ? currentObstacle : target);
        int rotation = obstacleOnRight ? 1 : 0;

        return (char) ((((myLocation.x << 6) | myLocation.y) << 4) | (direction.ordinal() << 1) | rotation);
    }

    private static boolean addVisitedState(char state) {
        if (visitedStates[state]) {
            return false;
        }
        visitedStates[state] = true;
        return true;
    }

    private static boolean canMove(Direction direction) {
        return rc.canMove(direction);
    }

    private static void move(Direction direction) throws GameActionException {
        if (rc.canMove(direction)) {
            rc.move(direction);
        }
        System.out.println("bug " + direction);
    }
}
