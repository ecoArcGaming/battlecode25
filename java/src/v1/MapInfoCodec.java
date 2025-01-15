package v1;

import battlecode.common.GameConstants;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;

import java.util.HashSet;
import java.util.Set;

/**
 * MapInfoCodec encodes and decodes a MapInfo to/from an int so that it can be sent in a message.
 */
public class MapInfoCodec {

    // basic test harness
    public static void main(String[] args) {
        try {
            Set<Integer> set = new HashSet<>();
            for(int x = 0; x < GameConstants.MAP_MAX_WIDTH; x++) {
                for(int y = 0; y < GameConstants.MAP_MAX_HEIGHT; y++) {
                    MapLocation locationInput = new MapLocation(x, y);
                    MapInfo infoInput = new MapInfo(locationInput, true, true, PaintType.EMPTY, PaintType.ENEMY_SECONDARY, true, false);
                    int messagePayload = encode(infoInput);
                    System.out.println(x+" "+y+" "+messagePayload);
                    if(set.contains(messagePayload)) {
                        throw new IllegalStateException();
                    }
                    set.add(messagePayload);
                    MapInfo infoOutput = decode(messagePayload);
                    if(!equals(infoInput, infoOutput)) {
                        decode(messagePayload);
                        throw new IllegalStateException();
                    }
                }
            }
            System.out.println(GameConstants.MAP_MAX_WIDTH*GameConstants.MAP_MAX_HEIGHT+" "+set.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // coding uses the bottom 21 of the available 32 bits: ________ ___Rmmmp ppWPyyyy yyxxxxxx

    /**
     * Encode the given MapInfo into an integer value.
     * Approx 55 bytecode.
     * @param mapInfo payload object value
     * @return encoded format
     */
    public static int encode(MapInfo mapInfo) {
        int i = 0;
        i += mapInfo.getMapLocation().x;
        i += mapInfo.getMapLocation().y << 6;
        if(mapInfo.isPassable()) {
            i += 1<<12;
        }
        if(mapInfo.isWall()) {
            i += 1<<13;
        }
        i += mapInfo.getPaint().ordinal()<<14;
        i += mapInfo.getMark().ordinal()<<17;
        if(mapInfo.hasRuin()) {
            i += 1<<20;
        }
        return i;
    }

    /**
     * Decode the given integer into a MapInfo.
     * Approx 62 bytecode.
     * @param i encoded format
     * @return payload object value.
     */
    public static MapInfo decode(int i) {
        int mask = (1<<6)-1;
        int x = i&mask;
        int y = (i>>6)&mask;
        boolean isPassable = (i&(1<<12)) != 0;
        boolean isWall = (i&(1<<13)) != 0;
        PaintType paint = PaintType.values()[ (i>>14)&((1<<3)-1) ];
        PaintType mark = PaintType.values()[ (i>>17)&((1<<3)-1) ];
        boolean hasRuin = (i&(1<<20)) != 0;
        return new MapInfo(new MapLocation(x, y), isPassable, isWall, paint, mark, hasRuin, false);
    }

    public static boolean equals(MapInfo a, MapInfo b) {
        return a.getMapLocation().equals(b.getMapLocation()) &&
                a.isPassable() == b.isPassable() && a.isWall() == b.isWall() &&
                a.getPaint() == b.getPaint() && a.getMark() == b.getMark() &&
                a.hasRuin() == b.hasRuin();
    }
}
