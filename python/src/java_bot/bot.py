from battlecode25.stubs import *
from .constants import Constants
from .communication import Communication
from .robot_info_codec import RobotInfoCodec
from .map_info_codec import MapInfoCodec
from .sensing import Sensing
from .pathfinding import Pathfinding
from .helper import Helper
from .soldier_type import SoldierType
from .soldier_state import SoldierState
from .robot import Robot
from .soldier import Soldier
from .splasher import Splasher
from .mopper import Mopper
from .tower import Tower
from .money_tower import MoneyTower
from collections import deque

# Initialize global variables
globals().update({
    # Initialization Variables
    'turn_count': 0,
    'curr_grid': None,
    'last8': deque(maxlen=16),  # Acts as queue with max size 16
    'last_tower': None,
    'soldier_type': SoldierType.ADVANCE,

    # Pathfinding Variables
    'stuck_turn_count': 0,
    'closest_path': -1,
    'in_bug_nav': False,
    'across_wall': None,
    'prev_location': None,

    # Soldier state variables
    'soldier_state': SoldierState.EXPLORING,
    'stored_state': SoldierState.EXPLORING,

    'fill_empty': None,
    'soldier_msg_cooldown': -1,
    'num_turns_alive': 0,  # Variable keeping track of how many turns alive for the soldier lifecycle

    # Key Soldier Location variables
    'enemy_tile': None,  # location of an enemy paint/tower for a develop/advance robot to report
    'ruin_to_fill': None,  # location of a ruin that the soldier is filling in
    'wander_target': None,  # target for advance robot to pathfind towards during exploration
    'enemy_tower': None,  # location of enemy tower for attack soldiers to pathfind to
    'fill_tower_type': None,
    'intermediate_target': None,  # used to record short-term robot targets
    'prev_intermediate': None,  # Copy of intermediate target
    'srp_location': None,  # location of SRP robot before it went to get more paint

    # Enemy Info variables
    'enemy_target': None,  # location of enemy tower/tile for tower to tell
    'remove_paint': None,

    # Tower Spawning Variables
    'spawn_queue': [],
    'send_type_message': False,
    'spawn_direction': None,
    'num_enemy_visits': 0,
    'rounds_without_enemy': 0,
    'num_soldiers_spawned': 0,

    # Navigation Variables
    'opposite_corner': None,
    'seen_paint_tower': False,
    'bot_round_num': 0,

    # Towers Broadcasting Variables
    'broadcast': False,
    'alert_robots': False,
    'alert_attack_soldiers': False,

    # BugNav Variables
    'is_tracing': False,
    'smallest_distance': 10000000,
    'closest_location': None,
    'tracing_dir': None,
    'stopped_location': None,
    'tracing_turns': 0,
    'bug1_turns': 0,

    # Splasher State Variables
    'is_low_paint': False,
    'prev_loc_info': None,

    # Bytecode Tracker
    'round_num': 0,

    # Filling SRP State
    'srp_center': None
})

def run_soldier():
    """
    Run a single turn for a Soldier.
    This code is wrapped inside the infinite loop in run(), so it is called once per turn.
    """
    # Update locations of last known towers
    Soldier.update_last_paint_tower()

    # Read incoming messages
    Soldier.read_new_messages()
    
    # Sense information about all visible nearby tiles
    nearby_tiles = sense_nearby_map_infos()

    # Get current location
    init_location = get_location()

    # On round 1, just paint tile it is on
    if globals()['bot_round_num'] == 1:
        Soldier.paint_if_possible(get_location())
        globals()['wander_target'] = MapLocation(get_map_width() - get_location().x, get_map_height() - get_location().y)

    # Hard coded robot type for very first exploration
    if get_round_num() <= 3:
        globals()['soldier_type'] = SoldierType.BINLADEN
        globals()['wander_target'] = MapLocation(get_map_width() - get_location().x, get_map_height() - get_location().y)

    # Handle different soldier types
    if globals()['soldier_type'] == SoldierType.BINLADEN:
        if get_round_num() >= (get_map_height() + get_map_width())/2:
            globals()['soldier_type'] = SoldierType.ADVANCE
            return
            
        Soldier.update_state_osama(init_location, nearby_tiles)
        
        if globals()['soldier_state'] == SoldierState.LOWONPAINT:
            set_indicator_string("LOWONPAINT")
            Soldier.low_paint_behavior()
            
        elif globals()['soldier_state'] == SoldierState.DELIVERINGMESSAGE:
            set_indicator_string("DELIVERINGMESSAGE")
            Soldier.msg_tower()
            
        elif globals()['soldier_state'] == SoldierState.FILLINGTOWER:
            set_indicator_string(f"FILLINGTOWER {globals()['ruin_to_fill']}")
            Soldier.fill_in_ruin(globals()['ruin_to_fill'])
            
        elif globals()['soldier_state'] == SoldierState.EXPLORING:
            set_indicator_string("EXPLORING")
            if globals()['wander_target'] is not None:
                direction = Pathfinding.better_explore(init_location, globals()['wander_target'], False)
                if direction is not None and can_move(direction):
                    move(direction)
                    Soldier.paint_if_possible(get_location())
            else:
                globals()['intermediate_target'] = None
                globals()['soldier_state'] = SoldierState.STUCK
                Soldier.reset_variables()
                
            if globals()['intermediate_target'] is not None:
                set_indicator_string(f"EXPLORING {globals()['intermediate_target']}")
                
        elif globals()['soldier_state'] == SoldierState.STUCK:
            set_indicator_string("STUCK")
            Soldier.stuck_behavior()
            
        set_indicator_dot(get_location(), 255, 255, 255)

    elif globals()['soldier_type'] == SoldierType.DEVELOP:
        Soldier.update_state(init_location, nearby_tiles)
        Helper.try_complete_resource_pattern()
        
        nearby_bots = sense_nearby_robots()
        sees_enemy = False
        for nearby_bot in nearby_bots:
            if nearby_bot.get_team().opponent() == get_team():
                sees_enemy = True
                break

        if sees_enemy:
            globals()['num_turns_alive'] = 0
            globals()['soldier_type'] = SoldierType.ADVANCE
            globals()['soldier_state'] = SoldierState.EXPLORING
            Soldier.reset_variables()
            
        elif globals()['num_turns_alive'] > Constants.DEV_LIFE_CYCLE_TURNS and globals()['soldier_state'] == SoldierState.STUCK:
            globals()['num_turns_alive'] = 0
            if get_map_width() <= Constants.SRP_MAP_WIDTH and get_map_height() <= Constants.SRP_MAP_HEIGHT:
                globals()['soldier_state'] = SoldierState.STUCK
            else:
                globals()['soldier_state'] = SoldierState.FILLINGSRP
            globals()['soldier_type'] = SoldierType.SRP
            Soldier.reset_variables()
            return

        if globals()['soldier_state'] == SoldierState.LOWONPAINT:
            set_indicator_string("LOWONPAINT")
            Soldier.low_paint_behavior()
            
        elif globals()['soldier_state'] == SoldierState.DELIVERINGMESSAGE:
            set_indicator_string("DELIVERINGMESSAGE")
            Soldier.msg_tower()
            
        elif globals()['soldier_state'] == SoldierState.FILLINGTOWER:
            set_indicator_string("FILLINGTOWER")
            Soldier.fill_in_ruin(globals()['ruin_to_fill'])
            
        elif globals()['soldier_state'] == SoldierState.EXPLORING:
            set_indicator_string("EXPLORING")
            direction = Pathfinding.explore_unpainted()
            if direction is not None:
                move(direction)
                Soldier.paint_if_possible(get_location())
            elif get_movement_cooldown_turns() < 10:
                globals()['soldier_state'] = SoldierState.STUCK
                Soldier.reset_variables()
                
        elif globals()['soldier_state'] == SoldierState.STUCK:
            set_indicator_string("STUCK")
            Soldier.stuck_behavior()
            if Sensing.find_paintable_tile(get_location(), 20) is not None:
                globals()['soldier_state'] = SoldierState.EXPLORING
                Soldier.reset_variables()
                
        set_indicator_dot(get_location(), 0, 255, 0)
        return

    elif globals()['soldier_type'] == SoldierType.ADVANCE:
        Soldier.update_state(init_location, nearby_tiles)
        
        if globals()['soldier_state'] == SoldierState.LOWONPAINT:
            set_indicator_string("LOWONPAINT")
            Soldier.low_paint_behavior()
            
        elif globals()['soldier_state'] == SoldierState.DELIVERINGMESSAGE:
            set_indicator_string("DELIVERINGMESSAGE")
            Soldier.msg_tower()
            
        elif globals()['soldier_state'] == SoldierState.FILLINGTOWER:
            set_indicator_string(f"FILLINGTOWER {globals()['ruin_to_fill']}")
            Soldier.fill_in_ruin(globals()['ruin_to_fill'])
            
        elif globals()['soldier_state'] == SoldierState.EXPLORING:
            set_indicator_string("EXPLORING")
            if globals()['wander_target'] is not None:
                direction = Pathfinding.better_explore(init_location, globals()['wander_target'], False)
                if direction is not None:
                    move(direction)
                    Soldier.paint_if_possible(get_location())
            else:
                globals()['intermediate_target'] = None
                globals()['soldier_state'] = SoldierState.STUCK
                Soldier.reset_variables()
                
            if globals()['intermediate_target'] is not None:
                set_indicator_string(f"EXPLORING {globals()['intermediate_target']}")
                
        elif globals()['soldier_state'] == SoldierState.STUCK:
            set_indicator_string("STUCK")
            Soldier.stuck_behavior()
            
        set_indicator_dot(get_location(), 0, 0, 255)
        return

    elif globals()['soldier_type'] == SoldierType.ATTACK:
        if globals()['enemy_tower'] is None:
            globals()['soldier_type'] = SoldierType.ADVANCE
            globals()['soldier_state'] = SoldierState.EXPLORING
            Soldier.reset_variables()
        else:
            # Prioritize any towers the attack robot sees
            for nearby_tile in nearby_tiles:
                # If enemy tower detected, then attack if you can or move towards it
                nearby_location = nearby_tile.get_map_location()
                if (nearby_tile.has_ruin() and can_sense_robot_at_location(nearby_location) and 
                    not sense_robot_at_location(nearby_location).get_team().is_player()):
                    globals()['enemy_tower'] = nearby_tile
                    set_indicator_dot(get_location(), 255, 0, 0)
                    return
                    
            # If cannot see any towers, then attack robot tries to pathfind to its assigned enemy tower
            enemy_tower_loc = globals()['enemy_tower'].get_map_location()
            if can_sense_robot_at_location(enemy_tower_loc) and can_attack(enemy_tower_loc):
                attack(enemy_tower_loc)
                back = enemy_tower_loc.direction_to(get_location())
                if can_move(back):
                    move(back)
                else:  # try moving back in other directions
                    left = back.rotate_left()
                    if can_move(left):
                        move(left)
                    else:
                        right = back.rotate_right()
                        if can_move(right):
                            move(right)
            else:
                direction = Pathfinding.pathfind(enemy_tower_loc)
                if direction is not None:
                    move(direction)
                    if can_attack(enemy_tower_loc):
                        attack(enemy_tower_loc)
                        
                # If tower not there anymore when we see it, set enemy_tower to None
                if can_sense_location(enemy_tower_loc) and not can_sense_robot_at_location(enemy_tower_loc):
                    globals()['enemy_tower'] = None
                    
            set_indicator_dot(get_location(), 255, 0, 0)

    elif globals()['soldier_type'] == SoldierType.SRP:
        # check for low paint and numTurnStuck
        Soldier.update_srp_state(init_location, nearby_tiles)
        Helper.try_complete_resource_pattern()
        
        # See if there are enemies nearby, if so, turn to advance bot
        nearby_bots = sense_nearby_robots()
        sees_enemy = False
        for nearby_bot in nearby_bots:
            if nearby_bot.get_team().opponent() == get_team():
                sees_enemy = True
                break
                
        if sees_enemy or (globals()['num_turns_alive'] > Constants.SRP_LIFE_CYCLE_TURNS and globals()['soldier_state'] == SoldierState.STUCK):
            globals()['soldier_type'] = SoldierType.ADVANCE
            globals()['soldier_state'] = SoldierState.EXPLORING
            globals()['num_turns_alive'] = 0
            Soldier.reset_variables()
            
        if globals()['soldier_state'] == SoldierState.LOWONPAINT:
            set_indicator_string("LOWONPAINT")
            Soldier.low_paint_behavior()
            
        elif globals()['soldier_state'] == SoldierState.FILLINGSRP:
            set_indicator_string("FILLING SRP")
            if get_map_width() <= Constants.SRP_MAP_WIDTH and get_map_height() <= Constants.SRP_MAP_HEIGHT:
                Soldier.fill_srp()
            else:
                Soldier.fill_srp_large_map()
                
        elif globals()['soldier_state'] == SoldierState.STUCK:
            set_indicator_string("STUCK")
            Soldier.stuck_behavior()
            if not (get_map_width() <= Constants.SRP_MAP_WIDTH and get_map_height() <= Constants.SRP_MAP_HEIGHT):
                for nearby_tile in nearby_tiles:
                    paint = Helper.resource_pattern_type(nearby_tile.get_map_location())
                    if ((nearby_tile.get_paint() == PaintType.EMPTY and nearby_tile.is_passable()) or
                        (nearby_tile.get_paint().is_ally() and paint != nearby_tile.get_paint())):
                        Soldier.reset_variables()
                        globals()['soldier_state'] = SoldierState.FILLINGSRP
                        globals()['num_turns_alive'] = 0
                        break
            elif (sense_map_info(get_location()).get_mark().is_ally() and 
                  not can_complete_resource_pattern(get_location())):
                Soldier.handle_srp_stuck_state()
                
        set_indicator_dot(get_location(), 255, 0, 255)

def run_mopper():
    """Run a single turn for a Mopper unit."""
    # Set indicator string based on remove_paint target
    if globals()['remove_paint'] is not None:
        set_indicator_string(str(globals()['remove_paint']))
    else:
        set_indicator_string("null")

    # When spawning in, check tile to see if it needs to be cleared
    if globals()['bot_round_num'] == 3 and sense_map_info(get_location()).get_paint().is_enemy():
        attack(get_location())
        return

    # Read all incoming messages
    Mopper.receive_last_message()
    Helper.try_complete_resource_pattern()

    all_tiles = sense_nearby_map_infos()
    
    # Avoid enemy towers with the highest priority
    for nearby_tile in all_tiles:
        bot = sense_robot_at_location(nearby_tile.get_map_location())
        if (bot is not None and bot.get_type().is_tower_type() and 
            not bot.get_team().equals(get_team())):
            if (globals()['remove_paint'] is not None and 
                globals()['remove_paint'].get_map_location().distance_squared_to(bot.get_location()) <= 9):
                globals()['remove_paint'] = None  # ignore target in tower range

            # Move around the tower by rotating 135 degrees
            direction = get_location().direction_to(nearby_tile.get_map_location()).rotate_right().rotate_right().rotate_right()
            if can_move(direction):
                move(direction)
                break

    # Stay safe, stay on ally paint if possible
    if not sense_map_info(get_location()).get_paint().is_ally():
        direction = Mopper.mopper_walk()
        if direction is not None and can_move(direction):
            move(direction)

    curr_paint = None
    # Check around the Mopper's attack radius for bots
    for tile in sense_nearby_map_infos(2):
        bot = sense_robot_at_location(tile.get_map_location())
        if bot is not None:
            if (bot.get_type().is_robot_type() and not bot.get_team().equals(get_team()) and 
                bot.get_paint_amount() > 0):
                if tile.get_paint().is_enemy() and can_attack(tile.get_map_location()):
                    attack(tile.get_map_location())
                direction = get_location().direction_to(bot.get_location())
                if direction in [Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST]:
                    if can_mop_swing(direction):
                        mop_swing(direction)
                        globals()['opposite_corner'] = None
                else:
                    if can_mop_swing(direction.rotate_right()):
                        mop_swing(direction.rotate_right())
                        globals()['opposite_corner'] = None
                return

        if tile.get_paint().is_enemy():
            if can_attack(tile.get_map_location()):
                curr_paint = tile.get_map_location()
            globals()['opposite_corner'] = None

    # Move towards opponent bot in vision range
    for tile in sense_nearby_map_infos():
        # First check for enemy tile, store it
        if tile.get_paint().is_enemy():
            globals()['opposite_corner'] = None
            if curr_paint is None:
                curr_paint = tile.get_map_location()

        bot = sense_robot_at_location(tile.get_map_location())
        if bot is not None:
            if (bot.get_type().is_robot_type() and not bot.get_team().equals(get_team()) and 
                not tile.get_paint().is_enemy()):
                enemy_dir = Pathfinding.pathfind(tile.get_map_location())
                if enemy_dir is not None:
                    globals()['opposite_corner'] = None
                    move(enemy_dir)
                    break

    # Attack nearest paint if exists with lower priority
    if curr_paint is not None:
        if can_attack(curr_paint):
            globals()['opposite_corner'] = None
            attack(curr_paint)
            return
        elif is_action_ready():
            direction = Pathfinding.pathfind(curr_paint)
            if direction is not None:
                globals()['opposite_corner'] = None
                move(direction)
                if can_attack(curr_paint):
                    attack(curr_paint)
        return

    # Path to opposite corner if we can't find enemy paint, lowest priority
    if globals()['remove_paint'] is not None:
        globals()['opposite_corner'] = None
        Mopper.remove_paint(globals()['remove_paint'])
    else:
        # Attack adjacent tiles if possible
        explore_dir = Pathfinding.get_unstuck()
        if explore_dir is not None:
            move(explore_dir)

def run_splasher():
    """Run a single turn for a Splasher unit."""
    if globals()['remove_paint'] is not None:
        set_indicator_string(str(globals()['remove_paint']))
    else:
        set_indicator_string("null")

    # Read input messages for information on enemy tile location
    Splasher.receive_last_message()

    # Check if last tower is still valid
    if (globals()['last_tower'] is not None and 
        can_sense_location(globals()['last_tower'].get_map_location())):
        if not can_sense_robot_at_location(globals()['last_tower'].get_map_location()):
            globals()['last_tower'] = None

    # Update last paint tower location
    Soldier.update_last_paint_tower()

    # If paint is low, go back to refill
    if (Robot.has_low_paint(75) and 
        get_money() < Constants.LOW_PAINT_MONEY_THRESHOLD):
        if not globals()['is_low_paint']:
            globals()['in_bug_nav'] = False
            globals()['across_wall'] = None
            globals()['prev_loc_info'] = sense_map_info(get_location())
        Robot.low_paint_behavior()
        return

    elif globals()['is_low_paint']:
        if globals()['remove_paint'] is None:
            globals()['remove_paint'] = globals()['prev_loc_info']
        globals()['prev_loc_info'] = None
        globals()['in_bug_nav'] = False
        globals()['across_wall'] = None
        globals()['is_low_paint'] = False

    # Move perpendicular to enemy towers if any exists in range
    for bot in sense_nearby_robots():
        if bot.get_type().is_tower_type() and not bot.get_team().equals(get_team()):
            direction = get_location().direction_to(bot.get_location()).rotate_right().rotate_right().rotate_right()
            if can_move(direction):
                move(direction)
            if (globals()['remove_paint'] is not None and 
                globals()['remove_paint'].get_map_location().distance_squared_to(bot.get_location()) <= 9):
                globals()['remove_paint'] = None  # ignore target in tower range

    enemies = Sensing.score_splasher_tiles()

    # Check to see if assigned tile is already filled in with our paint
    # Prevents splasher from painting already painted tiles
    if (globals()['remove_paint'] is not None and 
        can_sense_location(globals()['remove_paint'].get_map_location()) and 
        sense_map_info(globals()['remove_paint'].get_map_location()).get_paint().is_ally()):
        globals()['remove_paint'] = None

    # splash assigned tile or move towards it
    if enemies is not None and can_attack(enemies.get_map_location()):
        attack(enemies.get_map_location())
        return
    elif enemies is not None:
        if globals()['remove_paint'] is None:
            globals()['remove_paint'] = enemies

        direction = Pathfinding.pathfind(enemies.get_map_location())
        if direction is not None:
            move(direction)
        return

    elif globals()['remove_paint'] is not None:
        if can_attack(globals()['remove_paint'].get_map_location()):
            attack(globals()['remove_paint'].get_map_location())
            return
        direction = Pathfinding.pathfind(globals()['remove_paint'].get_map_location())
        if get_action_cooldown_turns() < 10 and direction is not None:
            move(direction)
        return

    if globals()['bot_round_num'] > 1:
        direction = Pathfinding.better_unstuck()
        if direction is not None and can_move(direction):
            move(direction)

def run_tower():
    """Run a single turn for a Tower unit."""
    # Sets spawn direction of each tower when created
    if globals()['spawn_direction'] is None:
        globals()['spawn_direction'] = Tower.spawn_direction()

    globals()['rounds_without_enemy'] += 1  # Update rounds without enemy
    
    # Handle different tower types and their messages
    if (get_type().get_base_type() == UnitType.LEVEL_ONE_MONEY_TOWER or 
        get_type().get_base_type() == UnitType.LEVEL_TWO_MONEY_TOWER):
        MoneyTower.read_new_messages()
        if get_paint() == 500:
            globals()['spawn_queue'].append(0)
    else:
        Tower.read_new_messages()

    # Starting condition
    if get_round_num() == 1:
        build_robot(UnitType.SOLDIER, get_location().add(globals()['spawn_direction']))
    elif get_round_num() == 2:
        center = MapLocation(get_map_width() // 2, get_map_height() // 2)
        if not get_location().is_within_distance_squared(center, 150):
            enemy_tiles = Tower.count_enemy_paint()
            if ((get_type() == UnitType.LEVEL_ONE_MONEY_TOWER or 
                 get_type() == UnitType.LEVEL_TWO_MONEY_TOWER) and 
                enemy_tiles > 3):
                globals()['spawn_queue'].append(3)
            else:
                build_robot(UnitType.SOLDIER, get_location().add(globals()['spawn_direction'].rotate_right()))
        else:
            enemy_tiles = Tower.count_enemy_paint()
            if enemy_tiles == 0 or enemy_tiles > 20:
                build_robot(UnitType.SPLASHER, get_location().add(globals()['spawn_direction'].rotate_right()))
            else:
                build_robot(UnitType.MOPPER, get_location().add(globals()['spawn_direction'].rotate_right()))
                if (len(globals()['spawn_queue']) == 0 and 
                    (get_type() == UnitType.LEVEL_ONE_MONEY_TOWER or 
                     get_type() == UnitType.LEVEL_TWO_MONEY_TOWER)):
                    globals()['spawn_queue'].append(3)
    else:
        if globals()['broadcast']:
            broadcast_message(MapInfoCodec.encode(globals()['enemy_target']))
            globals()['broadcast'] = False

        # If unit has been spawned and communication hasn't happened yet
        if globals()['send_type_message']:
            Tower.send_type_message(globals()['spawn_queue'][0])

        # Otherwise, if the spawn queue isn't empty, spawn the required unit
        elif (len(globals()['spawn_queue']) > 0 and 
              (get_money() > 400 or 
               (get_type() != UnitType.LEVEL_ONE_PAINT_TOWER and 
                get_type() != UnitType.LEVEL_TWO_PAINT_TOWER and 
                get_type() != UnitType.LEVEL_THREE_PAINT_TOWER))):
            unit_type = globals()['spawn_queue'][0]
            if unit_type in [0, 1, 2]:
                Tower.create_soldier()
            elif unit_type == 3:
                Tower.create_mopper()
            elif unit_type == 4:
                Tower.create_splasher()
        elif get_money() > 1200 and get_paint() > 200 and len(globals()['spawn_queue']) < 3:
            Tower.add_random_to_queue()

    # Handle broadcasting and enemy tower alerts
    if globals()['enemy_target'] is not None and globals()['alert_robots']:
        Tower.broadcast_nearby_bots()

    if globals()['enemy_tower'] is not None and get_round_num() % 50 == 0:
        Tower.broadcast_enemy_tower()

    # Handle tower upgrades based on money
    if get_type() == UnitType.LEVEL_ONE_PAINT_TOWER and get_money() > 5000:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_ONE_MONEY_TOWER and get_money() > 7500:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_TWO_PAINT_TOWER and get_money() > 7500:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_TWO_MONEY_TOWER and get_money() > 10000:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_ONE_DEFENSE_TOWER and get_money() > 5000:
        upgrade_tower(get_location())
    if get_type() == UnitType.LEVEL_TWO_DEFENSE_TOWER and get_money() > 7500:
        upgrade_tower(get_location())

    # Attack enemies
    Tower.attack_lowest_robot()
    Tower.aoe_attack_if_possible()

def run():
    """
    run() is the method that is called when a robot is instantiated in the Battlecode world.
    It is like the main function for your robot. If this method returns, the robot dies!
    
    Args:
        : The RobotController object. You use it to perform actions from this robot, and to get
            information on its current status. Essentially your portal to interacting with the world.
    """
    # Initialize the grid
    globals()['curr_grid'] = [[0] * get_map_height() for _ in range(get_map_width())]
    
    while True:
        # This code runs during the entire lifespan of the robot, which is why it is in an infinite
        # loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
        # loop, we call Clock.yield(), signifying that we've done everything we want to do.
        globals()['turn_count'] += 1  # We have now been alive for one more turn!
        globals()['num_turns_alive'] += 1
        
        if globals()['turn_count'] == Constants.RESIGN_AFTER:
            resign()
            
        try:
            # The same run() function is called for every robot on your team, even if they are
            # different types. Here, we separate the control depending on the UnitType, so we can
            # use different strategies on different robots.
            
            # Update round number and cooldowns
            globals()['round_num'] = get_round_num()
            globals()['bot_round_num'] += 1
            if globals()['soldier_msg_cooldown'] != -1:
                globals()['soldier_msg_cooldown'] -= 1

            # Run the appropriate behavior based on robot type
            if get_type() == UnitType.SOLDIER:
                run_soldier()
            elif get_type() == UnitType.MOPPER:
                run_mopper()
            elif get_type() == UnitType.SPLASHER:
                run_splasher()
            else:
                run_tower()
                
            # Check if we went over bytecode limit
            if globals()['round_num'] != get_round_num():
                print("I WENT OVER BYTECODE LIMIT BRUH")
                
            # Update the last eight locations list
            globals()['last8'].append(get_location())
            
        except GameActionException as e:
            # Oh no! It looks like we did something illegal in the Battlecode world. You should
            # handle GameActionExceptions judiciously, in case unexpected events occur in the game
            # world. Remember, uncaught exceptions cause your robot to explode!
            print("GameActionException")
            e.print_stack_trace()
            
        except Exception as e:
            # Oh no! It looks like our code tried to do something bad. This isn't a
            # GameActionException, so it's more likely to be a bug in our code.
            print("Exception")
            e.print_stack_trace()
            
        finally:
            # Signify we've done everything we want to do, thereby ending our turn.
            # This will make our code wait until the next turn, and then perform this loop again.
            yield_turn()
