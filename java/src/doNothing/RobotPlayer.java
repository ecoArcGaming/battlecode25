package doNothing;

import battlecode.common.*;
import v2.Constants;

public class RobotPlayer {
    static int turnCount = 0;

    public static void run(RobotController rc) throws GameActionException {
        BugNavigator.init(rc);
        while (true) {
            if (turnCount == 0) {
                if (rc.canBuildRobot(UnitType.SOLDIER, rc.getLocation().add(Direction.NORTH))) {
                    rc.buildRobot(UnitType.SOLDIER, rc.getLocation().add(Direction.NORTH));
                }
            }
            if (turnCount > 0) {
                BugNavigator.moveTo(new MapLocation(0, 0));
            }
            turnCount += 1;
            if (turnCount == 300) {


                rc.resign();
            }
            Clock.yield();
        }
    }
}