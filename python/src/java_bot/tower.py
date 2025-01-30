from battlecode25.stubs import *
import communication
import robot_info_codec
import map_info_codec
import constants
import random

def read_new_messages():
    """Reads new messages and does stuff"""
    # Looks at all incoming messages
    for message in read_messages(get_round_num() - 1):
        bytes_msg = message.get_bytes()
        if communication.is_robot_info(bytes_msg):
            msg = robot_info_codec.decode(bytes_msg)
        else:
            msg = map_info_codec.decode(bytes_msg)
            # Check if message is enemy tower
            if msg.has_ruin():
                globals()['rounds_without_enemy'] = 0
                # If tower receives enemy message from robots, broadcast the information to other
                # towers. Additionally, spawn a splasher and a mopper
                if Sensing.is_robot(message.get_sender_id()):
                    globals()['broadcast'] = True
                    globals()['alert_attack_soldiers'] = True
                    globals()['spawn_queue'].append(3)  # Spawns a mopper
                    globals()['spawn_queue'].append(4)  # Spawns a splasher
                    globals()['num_enemy_visits'] = globals()['num_enemy_visits'] + 1  # Increases probability of spawning a splasher

                # If tower receives message from tower, just alert the surrounding bots to target the enemy paint
                globals()['alert_robots'] = True

                # Update enemy tile regardless
                globals()['enemy_target'] = msg
                globals()['enemy_tower'] = msg

            # Check if message is enemy paint
            elif msg.get_paint().is_enemy():
                globals()['rounds_without_enemy'] = 0
                # If tower receives enemy message from robots, broadcast the information to other
                # towers. Additionally, spawn a splasher and a mopper
                if Sensing.is_robot(message.get_sender_id()):
                    globals()['broadcast'] = True
                    if random.random() <= 0.5:
                        globals()['spawn_queue'].append(4)  # Spawns a splasher
                    else:
                        globals()['spawn_queue'].append(3)  # Spawns a mopper
                    globals()['num_enemy_visits'] = globals()['num_enemy_visits'] + 1  # Increases probability of spawning a splasher

                # If tower receives message from tower, just alert the surrounding bots
                globals()['alert_robots'] = True

                # Update enemy tile regardless
                globals()['enemy_target'] = msg

def build_if_possible(robot_type, location):
    """Builds a robot of type robot_type at location"""
    if can_build_robot(robot_type, location):
        build_robot(robot_type, location)

def add_random_to_queue():
    """Builds an advance/develop soldier, weighted by how long it has been since the tower last saw a robot"""
    if (random.random() < globals()['num_enemy_visits'] * 0.2 or 
        (globals()['num_soldiers_spawned'] > constants.SPLASHER_CUTOFF and random.random() < constants.SPLASHER_SOLDIER_SPLIT)):
        globals()['spawn_queue'].append(4)
        globals()['num_enemy_visits'] = 0
    else:
        num_soldiers_spawned = globals()['num_soldiers_spawned'] + 1
        globals()['num_soldiers_spawned'] = num_soldiers_spawned
        # odds of explore robot increases linearly from 30-70 to 60-40
        if random.random() < min((globals()['rounds_without_enemy'] + constants.INIT_PROBABILITY_DEVELOP) / constants.DEVELOP_BOT_PROB_SCALING,
                               constants.DEVELOP_BOT_PROBABILITY_CAP):
            globals()['spawn_queue'].append(0)
        else:
            globals()['spawn_queue'].append(1)

def fire_attack_if_possible(location):
    """Fires an attack at location if possible"""
    if can_attack(location):
        attack(location)

def attack_lowest_robot():
    """Attacks the robot with the lowest HP within attack range"""
    nearest_low_bot = Sensing.find_nearest_lowest_hp()
    if nearest_low_bot is not None:
        fire_attack_if_possible(nearest_low_bot.get_location())

def aoe_attack_if_possible():
    """Does an AOE attack if possible"""
    if can_attack(None):
        attack(None)

def create_soldier():
    """Creates a soldier at location NORTH if possible"""
    added_dir = get_location().add(globals()['spawn_direction'])
    if start_square_covered():
        if can_build_robot(UnitType.MOPPER, added_dir):
            build_robot(UnitType.MOPPER, added_dir)
            return
    if can_build_robot(UnitType.SOLDIER, added_dir):
        build_robot(UnitType.SOLDIER, added_dir)
        globals()['send_type_message'] = True

def create_mopper():
    """Creates a mopper at location NORTH if possible"""
    added_dir = get_location().add(globals()['spawn_direction'])
    if can_build_robot(UnitType.MOPPER, added_dir):
        build_robot(UnitType.MOPPER, added_dir)
        globals()['send_type_message'] = True

def create_splasher():
    """Creates a splasher at the north"""
    added_dir = get_location().add(globals()['spawn_direction'])
    if can_build_robot(UnitType.SPLASHER, added_dir):
        build_robot(UnitType.SPLASHER, added_dir)
        globals()['send_type_message'] = True

def send_type_message(robot_type):
    """Send message to the robot indicating what type of bot it is"""
    added_dir = get_location().add(globals()['spawn_direction'])
    if can_sense_robot_at_location(added_dir) and can_send_message(added_dir):
        send_message(added_dir, robot_type)
        # If robot is an attack soldier or mopper, send enemy tile location as well
        if robot_type in [4, 3, 2]:
            communication.send_map_information(globals()['enemy_target'], added_dir)
    globals()['send_type_message'] = False
    globals()['spawn_queue'].pop(0)

def start_square_covered():
    """Checks to see if that spawning square is covered with enemy paint"""
    return sense_map_info(get_location().add(globals()['spawn_direction'])).get_paint().is_enemy()

def spawn_direction():
    """Finds spawning direction for a given tower"""
    height = get_map_height()
    width = get_map_width()
    loc = get_location()
    
    # Prefer spawning towards center
    center_x = width // 2
    center_y = height // 2
    
    best_dir = None
    min_dist = float('inf')
    
    for dir in constants.DIRECTIONS:
        new_loc = loc.add(dir)
        if can_build_robot(UnitType.SOLDIER, new_loc):
            dist = abs(new_loc.x - center_x) + abs(new_loc.y - center_y)
            if dist < min_dist:
                min_dist = dist
                best_dir = dir
    
    return best_dir
