package v1;

import battlecode.common.*;

public class Mopper extends Robot{
    public static void receiveLastMessage(RobotController rc) throws GameActionException {
        for(Message msg: rc.readMessages(-1)) {
            int bytes = msg.getBytes();
            if (Communication.isRobotInfo(bytes)) {
                RobotInfo message = RobotInfoCodec.decode(bytes);
                continue;
            } else {
                MapInfo message = MapInfoCodec.decode(bytes);
                if (message.getPaint().isEnemy()) {
                    RobotPlayer.removePaint = message;
                }
            }
        }
    }

    public static void removePaint(RobotController rc, MapInfo enemyPaint) throws GameActionException {
        System.out.println("Removing paint");
        MapLocation enemyLoc = enemyPaint.getMapLocation();
        if (rc.canAttack(enemyLoc) && enemyPaint.getPaint().isEnemy()){
            rc.attack(enemyLoc);
            RobotPlayer.removePaint = null;
        }
        else {
            rc.move(Pathfinding.pathfind(rc, enemyLoc));
        }
    }
}
