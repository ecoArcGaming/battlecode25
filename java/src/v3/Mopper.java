package v3;

import static v3.RobotPlayer.*;

import battlecode.common.*;

import java.util.ArrayList;

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
            } else {
                MapInfo message = MapInfoCodec.decode(bytes);
                if (message.getPaint().isEnemy()) {
                    MapLocation robotLoc = rc.getLocation();
                    if (RobotPlayer.removePaint == null || robotLoc.distanceSquaredTo(message.getMapLocation()) < robotLoc.distanceSquaredTo(removePaint.getMapLocation())){
                        removePaint = message;
                        Robot.resetVariables();
                    }
                }
                // If enemy tower, then go to enemy tower location
                else if (message.hasRuin()) {
                    MapLocation robotLoc = rc.getLocation();
                    if (removePaint == null || robotLoc.distanceSquaredTo(message.getMapLocation()) < robotLoc.distanceSquaredTo(removePaint.getMapLocation())){
                        removePaint = message;
                        Robot.resetVariables();
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
            Robot.resetVariables();
        }
        else {
            Direction moveDir = Pathfinding.pathfind(rc, enemyLoc);
            if (moveDir != null) {
                rc.move(moveDir);
            }
        }
    }

    public static MapLocation MopperScoring(RobotController rc) throws GameActionException {
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MapInfo map: nearbyTiles) {
            int curr = 0;
            RobotInfo bot = rc.senseRobotAtLocation(map.getMapLocation());
            if (bot != null){
                if (!bot.getTeam().isPlayer()){
                    if (bot.type.isRobotType()){
                        curr += 100;
                    }
                    if (bot.type.isTowerType()){
                        curr -= 100;
                    }
                }
            }
            if (curr > bestScore){
                best = map.getMapLocation();
                bestScore = curr;
            }
        }
        return best;
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

    public static Direction mopperWalk(RobotController rc) throws GameActionException {
        ArrayList<MapInfo> safe = new ArrayList<MapInfo>();

        for (MapInfo map: rc.senseNearbyMapInfos(2)) {
            if (map.getPaint().isAlly() && !last8.contains(map.getMapLocation())){
                safe.add(map);
            }
        }
        if (safe.isEmpty()){
            return null;
        }
        int index = (int) (Math.random()* safe.size());
        MapInfo map = safe.get(index);
        return rc.getLocation().directionTo(map.getMapLocation());

    }
}
