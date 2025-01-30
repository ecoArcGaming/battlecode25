from battlecode25.stubs import *
from .constants import *
from .pathfinding import Pathfinding
import math

class Robot:
    """Base class for all robot types"""

    @staticmethod
    def low_paint_behavior(rc):
        """Method for robot behavior when they are low on paint"""
        globals()['is_low_paint'] = True
        # If last tower is null, then just random walk on paint
        for enemy_robot in rc.sense_nearby_robots(-1, rc.get_team().opponent()):
            if enemy_robot.get_type().is_tower_type():
                if rc.can_attack(enemy_robot.get_location()):
                    rc.attack(enemy_robot.get_location())
                    break

        if globals()['last_tower'] is None:
            move_to = Pathfinding.random_painted_walk(rc)
            if move_to is not None and rc.can_move(move_to):
                rc.move(move_to)
            return

        dir = Pathfinding.return_to_tower(rc)
        if dir is not None:
            rc.move(dir)

        # Otherwise, pathfind to the tower
        tower_location = globals()['last_tower'].get_map_location()
        Robot.complete_ruin_if_possible(rc, tower_location)
        amt_to_transfer = rc.get_paint() - rc.get_type().paint_capacity
        
        if rc.can_sense_robot_at_location(tower_location):
            tower_paint = rc.sense_robot_at_location(tower_location).paint_amount
            if rc.get_paint() < 5 and rc.can_transfer_paint(tower_location, -tower_paint) and tower_paint > MIN_PAINT_GIVE:
                rc.transfer_paint(tower_location, -tower_paint)

        if rc.can_transfer_paint(tower_location, amt_to_transfer):
            rc.transfer_paint(tower_location, amt_to_transfer)

    @staticmethod
    def check_allied_tower(rc, loc):
        """Given MapInfo loc, return True if there is an allied tower at loc"""
        location = loc.get_map_location()
        if loc.has_ruin() and rc.can_sense_robot_at_location(location) and rc.sense_robot_at_location(location).get_team() == rc.get_team():
            return True
        return False

    @staticmethod
    def update_last_paint_tower(rc):
        """Updates the lastTower variable to any allied paint tower currently in range"""
        min_distance = -1
        last_tower = None
        for loc in rc.sense_nearby_map_infos():
            if Robot.check_allied_tower(rc, loc):
                tower_type = rc.sense_robot_at_location(loc.get_map_location()).get_type()
                if tower_type.get_base_type() == UnitType.LEVEL_ONE_PAINT_TOWER.get_base_type():
                    globals()['seen_paint_tower'] = True
                    distance = loc.get_map_location().distance_squared_to(rc.get_location())
                    if min_distance == -1 or min_distance > distance:
                        last_tower = loc
                        min_distance = distance

        if min_distance != -1:
            globals()['last_tower'] = last_tower
        elif globals()['last_tower'] is not None and globals()['last_tower'].get_map_location().is_within_distance_squared(rc.get_location(), 20):
            globals()['last_tower'] = None

    @staticmethod
    def has_low_paint(rc, threshold):
        """Check if the robot rc has less paint than the threshold"""
        return rc.get_paint() < threshold

    @staticmethod
    def gen_tower_type(rc, ruin_location):
        """Returns a random tower with a Constants.TOWER_SPLIT split and defense tower only if in range"""
        if rc.get_number_towers() <= 3:
            return UnitType.LEVEL_ONE_MONEY_TOWER

        prob_defense = min(1, (rc.get_number_towers()) / (rc.get_map_height() + rc.get_map_width()) * 5)
        prob_from_center = 1 - 2.5 * (abs(rc.get_map_width() / 2 - ruin_location.x) + abs(rc.get_map_height() / 2 - ruin_location.y)) / (rc.get_map_height() + rc.get_map_width())
        haha = random.random()
        
        if haha < prob_defense * prob_from_center:
            return UnitType.LEVEL_ONE_DEFENSE_TOWER
            
        hehe = random.random()
        return (UnitType.LEVEL_ONE_PAINT_TOWER 
                if hehe < min((rc.get_number_towers()) / math.sqrt(rc.get_map_height() + rc.get_map_width()), PERCENT_PAINT) 
                else UnitType.LEVEL_ONE_MONEY_TOWER)

    @staticmethod
    def complete_ruin_if_possible(rc, ruin_location):
        """Completes the ruin at the given location if possible"""
        if rc.can_complete_tower_pattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin_location):
            rc.complete_tower_pattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin_location)
        if rc.can_complete_tower_pattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin_location):
            rc.complete_tower_pattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin_location)
        if rc.can_complete_tower_pattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruin_location):
            rc.complete_tower_pattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruin_location)

    @staticmethod
    def reset_variables():
        """
        Resets pathfinding variables
        Meant to be called when the robot has found else to do
        """
        globals()['is_tracing'] = False
        globals()['smallest_distance'] = 10000000
        globals()['closest_location'] = None
        globals()['tracing_dir'] = None
        globals()['stuck_turn_count'] = 0
        globals()['closest_path'] = -1
        globals()['fill_tower_type'] = None
        globals()['stopped_location'] = None
        globals()['tracing_turns'] = 0
        globals()['bug1_turns'] = 0
        globals()['in_bug_nav'] = False
        globals()['across_wall'] = None
