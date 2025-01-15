package doNothing;

import battlecode.common.*;
import v2.Constants;

public class RobotPlayer {
    static int turnCount = 0;
    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount += 1;
            if (turnCount == 300) {
                rc.resign();
            }
            Clock.yield();
        }
    }
}