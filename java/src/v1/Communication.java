package v1;

import battlecode.common.*;

public class Communication {
    /**
     * Sends an encoded robotInfo to targetLoc
     */
    public static void sendRobotInformation(RobotController rc, RobotInfo robotInfo, MapLocation targetLoc) throws GameActionException {
        int encodedInfo = RobotInfoCodec.encode(robotInfo);
        if (rc.canSendMessage(targetLoc, encodedInfo)) {
            rc.sendMessage(targetLoc, encodedInfo);
        }
    }
}
