package v2;

import static v2.RobotPlayer.*;

import battlecode.common.*;

public class Splasher {
    public static void receiveLastMessage(RobotController rc) throws GameActionException {
        for(Message msg: rc.readMessages(-1)) {
            int bytes = msg.getBytes();
            // Receives message of what type of splasher it is
            if (bytes == 4){
                continue;
            }
            if (Communication.isRobotInfo(bytes)) {
                RobotInfo message = RobotInfoCodec.decode(bytes);
                continue;
            } else {
                MapInfo message = MapInfoCodec.decode(bytes);
                if (message.getPaint().isEnemy()) {
                    MapLocation robotLoc = rc.getLocation();
                    if (removePaint == null || robotLoc.distanceSquaredTo(message.getMapLocation()) < robotLoc.distanceSquaredTo(removePaint.getMapLocation())){
                        removePaint = message;
                    }
                }
            }
        }
    }
}
