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



    /**
    swing if there is enemy bots nearby, do nothing otherwise
     **/
    public static void trySwing(RobotController rc) throws GameActionException {
        if (rc.getActionCooldownTurns() > 10){
            return;
        }
        int north = 0;
        int east = 0;
        int south = 0;
        int west = 0;
        MapLocation loc = rc.getLocation();

        for (RobotInfo enemy: rc.senseNearbyRobots(2, rc.getTeam().opponent())){
            if (loc.directionTo(enemy.getLocation()) == Direction.NORTH){
                north++;
            }
            else if (loc.directionTo(enemy.getLocation()) == Direction.SOUTH){
                south++;
            }
            else if (loc.directionTo(enemy.getLocation()) == Direction.WEST){
                west++;
            }
            else if (loc.directionTo(enemy.getLocation()) == Direction.EAST){
                east++;
            }
            else if (loc.directionTo(enemy.getLocation()) == Direction.NORTHWEST){
                north++;
                west++;
            }
            else if (loc.directionTo(enemy.getLocation()) == Direction.NORTHEAST){
                north++;
                east++;
            }
            else if (loc.directionTo(enemy.getLocation()) == Direction.SOUTHWEST){
                south++;
                west++;
            }
            else if (loc.directionTo(enemy.getLocation()) == Direction.SOUTHEAST){
                south++;
                east++;
            }
        }
        if (north > 1 && north > east && north > south && north > west){
            if (rc.canMopSwing(Direction.NORTH)){
                rc.mopSwing(Direction.NORTH);
            }
            return;
        }
        if (south > 1 && south > east && south > west){
            if (rc.canMopSwing(Direction.SOUTH)){
                rc.mopSwing(Direction.SOUTH);
            }
            return;
        }
        if (east > 1 && east > west){
            if (rc.canMopSwing(Direction.EAST)){
                rc.mopSwing(Direction.EAST);
            }
            return;
        }
        if (west > 1){
            if (rc.canMopSwing(Direction.WEST)){
                rc.mopSwing(Direction.WEST);
            }
        }
    }
}
