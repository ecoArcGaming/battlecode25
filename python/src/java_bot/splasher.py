from battlecode25.stubs import *
from .robot import Robot
import constants
from .sensing import Sensing
from .pathfinding import Pathfinding
from .communication import Communication
from .robot_info_codec import RobotInfoCodec
from .map_info_codec import MapInfoCodec

class Splasher(Robot):
    """Class for all methods that a splasher will do"""
    
    @staticmethod
    def low_paint_behavior():
        """Method for splasher to do when low on paint"""
        Robot.low_paint_behavior()
        if get_paint() > constants.LOW_PAINT_THRESHOLD:
            if globals()['ruin_to_fill'] is not None:
                globals()['soldier_state'] = SoldierState.FILLINGTOWER
            else:
                globals()['soldier_state'] = SoldierState.STUCK
            Splasher.reset_variables()

    @staticmethod
    def paint_if_possible(paint_tile=None, paint_location=None):
        """
        Methods for splashers painting, given a MapInfo and/or MapLocation
        Paints when there is no paint or if allied paint is incorrect
        """
        if paint_location is None and paint_tile is not None:
            paint_location = paint_tile.get_map_location()
        elif paint_tile is None and paint_location is not None:
            paint_tile = sense_map_info(get_location())
            
        if (paint_tile.get_paint() == PaintType.EMPTY and 
            can_attack(paint_location) and 
            paint_tile.get_mark() == PaintType.EMPTY):
            attack(paint_location, False)

    @staticmethod
    def attack_if_possible():
        """Attack enemy robots if possible"""
        for enemy_robot in sense_nearby_robots(-1, get_team().opponent()):
            if can_attack(enemy_robot.get_location()):
                attack(enemy_robot.get_location())
                break

    @staticmethod
    def reset_variables():
        """Reset variables for splasher"""
        globals()['enemy_target'] = None
        globals()['enemy_tower'] = None
        globals()['alert_robots'] = False
        globals()['alert_attack_soldiers'] = False
        globals()['broadcast'] = False
        globals()['ruin_to_fill'] = None
        globals()['fill_tower_type'] = None
        globals()['rounds_without_enemy'] = globals().get('rounds_without_enemy', 0) + 1
