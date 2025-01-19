package v2;

import static v2.RobotPlayer.*;

import battlecode.common.*;

public class Mopper extends Robot{
    public static void receiveLastMessage(RobotController rc) throws GameActionException {
        for(Message msg: rc.readMessages(-1)) {
            int bytes = msg.getBytes();
            // Receives what type of mopper the bot is
            if (bytes == 3){
                continue;
            }
            if (Communication.isRobotInfo(bytes)) {
                RobotInfo message = RobotInfoCodec.decode(bytes);
                continue;
            } else {
                MapInfo message = MapInfoCodec.decode(bytes);
                if (message.getPaint().isEnemy()) {
                    MapLocation robotLoc = rc.getLocation();
                    if (RobotPlayer.removePaint == null || robotLoc.distanceSquaredTo(message.getMapLocation()) < robotLoc.distanceSquaredTo(removePaint.getMapLocation())){
                        removePaint = message;
                    }
                }
            }
        }
    }

    public static void removePaint(RobotController rc, MapInfo enemyPaint) throws GameActionException {
        MapLocation enemyLoc = enemyPaint.getMapLocation();
        if (rc.canAttack(enemyLoc) && enemyPaint.getPaint().isEnemy()){
            rc.attack(enemyLoc);
            removePaint = null;
        }
        else {
            Direction moveDir = Pathfinding.pathfind(rc, enemyLoc);
            if (moveDir != null) {
                rc.move(moveDir);
            }
        }
    }

    /*
    swing if there is enemy bots nearby, do nothing otherwise
     */
    public static void trySwing(RobotController rc) throws GameActionException {

        int north = 0;
        int east = 0;
        int south = 0;
        int west = 0;
        MapLocation loc = rc.getLocation();

        if (!rc.senseRobotAtLocation(loc.add(Direction.EAST)).getTeam().isPlayer()){
            east++;
        }
        if (!rc.senseRobotAtLocation(loc.add(Direction.NORTH)).getTeam().isPlayer()){
            north++;
        }
        if (!rc.senseRobotAtLocation(loc.add(Direction.WEST)).getTeam().isPlayer()){
            west++;
        }
        if (!rc.senseRobotAtLocation(loc.add(Direction.SOUTH)).getTeam().isPlayer()){
            south++;
        }
        if (!rc.senseRobotAtLocation(loc.add(Direction.NORTHEAST)).getTeam().isPlayer()){
            north++;
            east++;
        }
        if (!rc.senseRobotAtLocation(loc.add(Direction.NORTHWEST)).getTeam().isPlayer()){
            north++;
            west++;
        }
        if (!rc.senseRobotAtLocation(loc.add(Direction.SOUTHEAST)).getTeam().isPlayer()){
            south++;
            east++;
        }
        if (!rc.senseRobotAtLocation(loc.add(Direction.SOUTHWEST)).getTeam().isPlayer()){
            south++;
            west++;
        }
        if (north > 1 && north > east && north > south && north > west){
            rc.mopSwing(Direction.NORTH);
            return;
        }
        if (south > 1 && south > east && south > west){
            rc.mopSwing(Direction.SOUTH);
            return;
        }
        if (east > 1 && east > west){
            rc.mopSwing(Direction.EAST);
            return;
        }
        if (west > 1){
            rc.mopSwing(Direction.WEST);
        }

    }
}
