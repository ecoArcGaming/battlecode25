package v3;

import battlecode.common.*;

import static v3.RobotPlayer.*;

public class MoneyTower extends Tower {
    /**
     * Reads new messages and does stuff
     */
    public static void readNewMessages(RobotController rc) throws GameActionException{
        // Looks at all incoming messages
        for (Message message: rc.readMessages(rc.getRoundNum()-1)){
            int bytes = message.getBytes();
            if (Communication.isRobotInfo(bytes)){
                RobotInfo msg = RobotInfoCodec.decode(bytes);
            }
            else{
                MapInfo msg = MapInfoCodec.decode(bytes);
                // Check if message is enemy tower
                if (msg.hasRuin()){
                    roundsWithoutEnemy = 0;
                    RobotPlayer.alertRobots = true;
                    RobotPlayer.enemyTarget = msg;
                    enemyTower = msg;
                }
                // Check if message is enemy paint
                else if (msg.getPaint().isEnemy()){
                    roundsWithoutEnemy = 0;
                    if (Sensing.isRobot(rc, message.getSenderID())){
                        broadcast = true;
                        numEnemyVisits += 1; //   Increases probability of spawning a splasher
                    }
                    // If tower receives message from tower, just alert the surrounding bots to target the enemy
                    // paint
                    alertRobots = true;
                    // Update enemy tile regardless
                    enemyTarget = msg;
                }
            }
        }
    }
}
