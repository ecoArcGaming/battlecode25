package v1;

import battlecode.common.*;

import java.util.Random;

public class Constants {
    public static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };
    public static final Random rng = new Random(6147);
    public static final double TOWER_SPLIT = 0.3;
    public static final int RESIGN_AFTER = 300;
}
