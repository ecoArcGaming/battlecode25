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

    /**
     * Send Map information to targetLoc
     */
    public static void sendMapInformation(RobotController rc, MapInfo mapInfo, MapLocation targetLoc) throws GameActionException {
        int encodedInfo = MapInfoCodec.encode(mapInfo);
        if (rc.canSendMessage(targetLoc, encodedInfo)) {
            rc.sendMessage(targetLoc, encodedInfo);
        }
    }

    /**
     * Checks to see if input message is a robot info or map info
     */
    public static boolean isRobotInfo(int msg){
        return msg >>> 21 > 0;
    }


}
