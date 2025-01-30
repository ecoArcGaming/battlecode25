from battlecode25.stubs import *
import pathfinding
import math
import constants
import random

def low_paint_behavior():
    """Method for robot behavior when they are low on paint"""
    globals()['is_low_paint'] = True
    # If last tower is null, then just random walk on paint
    for enemy_robot in sense_nearby_robots(-1, get_team().opponent()):
        if enemy_robot.get_type().is_tower_type():
            if can_attack(enemy_robot.get_location()):
                attack(enemy_robot.get_location())
                break

    if globals()['last_tower'] is None:
        move_to = pathfinding.random_painted_walk()
        if move_to is not None and can_move(move_to):
            move(move_to)
        return

    dir = pathfinding.return_to_tower()
    if dir is not None:
        move(dir)

    # Otherwise, pathfind to the tower
    tower_location = globals()['last_tower'].get_map_location()
    complete_ruin_if_possible(tower_location)
    amt_to_transfer = get_paint() - get_type().paint_capacity
    
    if can_sense_robot_at_location(tower_location):
        tower_paint = sense_robot_at_location(tower_location).paint_amount
        if get_paint() < 5 and can_transfer_paint(tower_location, -tower_paint) and tower_paint > constants.MIN_PAINT_GIVE:
            transfer_paint(tower_location, -tower_paint)

    if can_transfer_paint(tower_location, amt_to_transfer):
        transfer_paint(tower_location, amt_to_transfer)

def check_allied_tower(loc):
    """Given MapInfo loc, return True if there is an allied tower at loc"""
    location = loc.get_map_location()
    if loc.has_ruin() and can_sense_robot_at_location(location) and sense_robot_at_location(location).get_team() == get_team():
        return True
    return False

def update_last_paint_tower():
    """Updates the lastTower variable to any allied paint tower currently in range"""
    min_distance = -1
    last_tower = None
    for loc in sense_nearby_map_infos():
        if check_allied_tower(loc):
            tower_type = sense_robot_at_location(loc.get_map_location()).get_type()
            if tower_type.get_base_type() == UnitType.LEVEL_ONE_PAINT_TOWER.get_base_type():
                globals()['seen_paint_tower'] = True
                distance = loc.get_map_location().distance_squared_to(get_location())
                if min_distance == -1 or min_distance > distance:
                    last_tower = loc
                    min_distance = distance

    if min_distance != -1:
        globals()['last_tower'] = last_tower
    elif globals()['last_tower'] is not None and globals()['last_tower'].get_map_location().is_within_distance_squared(get_location(), 20):
        globals()['last_tower'] = None

def has_low_paint(threshold):
    """Check if the robot  has less paint than the threshold"""
    return get_paint() < threshold

def gen_tower_type(ruin_location):
    """Returns a random tower with a Constants.TOWER_SPLIT split and defense tower only if in range"""
    if get_num_towers() <= 3:
        return UnitType.LEVEL_ONE_MONEY_TOWER

    prob_defense = min(1, (get_num_towers()) / (get_map_height() + get_map_width()) * 5)
    prob_from_center = 1 - 2.5 * (abs(get_map_width() / 2 - ruin_location.x) + abs(get_map_height() / 2 - ruin_location.y)) / (get_map_height() + get_map_width())
    haha = random.random()
    
    if haha < prob_defense * prob_from_center:
        return UnitType.LEVEL_ONE_DEFENSE_TOWER
            
    hehe = random.random()
    return (UnitType.LEVEL_ONE_PAINT_TOWER 
            if hehe < min((get_num_towers()) / math.sqrt(get_map_height() + get_map_width()), constants.PERCENT_PAINT) 
            else UnitType.LEVEL_ONE_MONEY_TOWER)

def complete_ruin_if_possible(ruin_location):
    """Completes the ruin at the given location if possible"""
    if can_complete_tower_pattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin_location):
        complete_tower_pattern(UnitType.LEVEL_ONE_MONEY_TOWER, ruin_location)
    if can_complete_tower_pattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin_location):
        complete_tower_pattern(UnitType.LEVEL_ONE_PAINT_TOWER, ruin_location)
    if can_complete_tower_pattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruin_location):
        complete_tower_pattern(UnitType.LEVEL_ONE_DEFENSE_TOWER, ruin_location)

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
