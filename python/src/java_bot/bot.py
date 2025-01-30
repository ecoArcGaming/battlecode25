"""
RobotPlayer is the class that describes your main robot strategy.
The run() method inside this class is like your main function: this is what we'll call once your robot
is created!

FIXME (General issues we noticed)
    - Clumped robots is a bit problematic
    - Exploration around walls is ass(?)
    - Differential behavior given map size
    - Splasher improvements
        - Survivability
        - Don't paint on our own patterns
        - Paint underneath them en route to enemy?
        - Prioritize enemy over our own side?
    - Strategy kinda sucks for smaller maps

TODO (Specific issues we noticed that currently have a solution)
    - Fix exploration for soldiers so that when a mopper goes and takes over area, the soldier can come and
        finish the ruin pattern
    - Low health behavior to improve survivability
    - Handle the 25 tower limit
    - Bug1 shenanigans (maybe we should try bug0 and take the L if bots do get stuck)
    - Check out robot distributions on varying map sizes and stuff (idk seems like tower queues are clogged up by splashers/moppers)
    - Robot lifecycle should be based around map size probably
    - Do we do SRPs too late?
    - Idea: somehow figure out symmetry of the map so we can tell robots to go in a certain direction
    - Have a better strategy for attacking the enemy
    - lifecycle idea: stuck && alive for x turns
    - if a bot is on low paint behavior and it runs out of paint standing next to the tower, if the tower doesnt have enough paint to refill to max, it just gets all the paint remaining in the tower and then leaves
"""

from battlecode25.stubs import *
from .constants import Constants
from .soldier_type import SoldierType
from .soldier_state import SoldierState
from .soldier import Soldier
from .splasher import Splasher
from .mopper import Mopper
from .tower import Tower
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

def run(rc):
    """
    run() is the method that is called when a robot is instantiated in the Battlecode world.
    It is like the main function for your robot. If this method returns, the robot dies!
    
    Args:
        rc: The RobotController object. You use it to perform actions from this robot, and to get
            information on its current status. Essentially your portal to interacting with the world.
    """
    # Initialize the grid
    globals()['curr_grid'] = [[0] * rc.get_map_height() for _ in range(rc.get_map_width())]
    
    while True:
        # This code runs during the entire lifespan of the robot, which is why it is in an infinite
        # loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
        # loop, we call Clock.yield(), signifying that we've done everything we want to do.
        globals()['turn_count'] += 1  # We have now been alive for one more turn!
        globals()['num_turns_alive'] += 1
        
        if globals()['turn_count'] == Constants.RESIGN_AFTER:
            rc.resign()
            
        try:
            # The same run() function is called for every robot on your team, even if they are
            # different types. Here, we separate the control depending on the UnitType, so we can
            # use different strategies on different robots.
            
            # Update round number and cooldowns
            globals()['round_num'] = rc.get_round_num()
            globals()['bot_round_num'] += 1
            if globals()['soldier_msg_cooldown'] != -1:
                globals()['soldier_msg_cooldown'] -= 1

            # Run the appropriate behavior based on robot type
            if rc.get_type() == UnitType.SOLDIER:
                Soldier.run_soldier(rc)
            elif rc.get_type() == UnitType.MOPPER:
                Mopper.run_mopper(rc)
            elif rc.get_type() == UnitType.SPLASHER:
                Splasher.run_splasher(rc)
            else:
                Tower.run_tower(rc)
                
            # Check if we went over bytecode limit
            if globals()['round_num'] != rc.get_round_num():
                print("I WENT OVER BYTECODE LIMIT BRUH")
                
            # Update the last eight locations list
            globals()['last8'].append(rc.get_location())
            
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
            Clock.yield_()
