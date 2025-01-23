package v2;

import battlecode.common.*;

/**
 * RobotInfoCodec encodes and decodes a RobotInfo to/from an int so that it can be sent in a message.
 */
public class RobotInfoCodec {

    // basic test driver
    public static void main(String[] args) {
        try {
            MapLocation locationInput = new MapLocation(5, 4);
            RobotInfo infoInput = new RobotInfo(0, Team.B, UnitType.LEVEL_ONE_DEFENSE_TOWER, 100, locationInput, 100);
            int messagePayload = encode(infoInput);
            RobotInfo infoOutput = decode(messagePayload);
            if (!equals(infoInput, infoOutput)) {
                throw new IllegalStateException();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // coding uses the bottom 31 of the available 32 bits: _ppppppp Thhhhhhh UUUUyyyy yyxxxxxx
    // paint (p) and health (h) are approximate, since it won't fit otherwise. ID is not sent.

    /**
     * Encode the given RobotInfo into an integer value.
     * Approx 69 bytecode.
     *
     * @param robotInfo payload object value
     * @return encoded format
     */
    public static int encode(RobotInfo robotInfo) {
        int i = 0;
        i += robotInfo.getLocation().x;
        i += robotInfo.getLocation().y << 6;
        i += robotInfo.getType().ordinal() << 12;
        int healthPercent = (100 * robotInfo.getHealth()) / robotInfo.getType().health;
        i += healthPercent << 16;
        i += robotInfo.getTeam().ordinal() << 23;
        int paintPercent = (100 * robotInfo.getPaintAmount()) / robotInfo.getType().paintCapacity;
        i += paintPercent << 24;

        return i;
    }

    /**
     * Decode the given integer into a RobotInfo.
     * Approx 71 bytecode.
     *
     * @param i encoded format
     * @return payload object value.
     */
    public static RobotInfo decode(int i) {
        int locationMask = (1 << 6) - 1;
        int x = i & locationMask;
        int y = (i >> 6) & locationMask;
        UnitType unitType = UnitType.values()[(i >> 12) & ((1 << 4) - 1)];
        int healthPercent = (i >> 16) & ((1 << 7) - 1);
        Team team = Team.values()[(i >> 23) & 1];
        int paintPercent = (i >> 24) & ((1 << 7) - 1); // TODO finish me, equals.
        return new RobotInfo(0, team, unitType, (int)Math.ceil (((unitType.health / 100.0) * healthPercent)),
                new MapLocation(x, y), (int)Math.ceil (((unitType.paintCapacity / 100.0) * paintPercent)) );
    }

    public static boolean equals(RobotInfo a, RobotInfo b) {
        return a.getLocation().equals(b.getLocation()) &&
                a.getType() == b.getType() && a.getTeam() == b.getTeam() &&
                ((100 * a.getHealth()) / a.getType().health) == ((100 * b.getHealth()) / b.getType().health)
                && ((100 * a.getPaintAmount()) / a.getType().paintCapacity) == ((100 * b.getPaintAmount()) / b.getType().paintCapacity);
    }
}