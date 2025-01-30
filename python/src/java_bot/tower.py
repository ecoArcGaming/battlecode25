from battlecode25.stubs import *
from .communication import Communication
from .robot_info_codec import RobotInfoCodec
from .map_info_codec import MapInfoCodec
from .constants import *
from .sensing import Sensing
import random

class Tower:
    """Class for all general-purpose tower methods"""

    @staticmethod
    def read_new_messages(rc):
        """Reads new messages and does stuff"""
        # Looks at all incoming messages
        for message in rc.read_messages(rc.get_round_num() - 1):
            bytes_msg = message.get_bytes()
            if Communication.is_robot_info(bytes_msg):
                msg = RobotInfoCodec.decode(bytes_msg)
            else:
                msg = MapInfoCodec.decode(bytes_msg)
                # Check if message is enemy tower
                if msg.has_ruin():
                    globals()['rounds_without_enemy'] = 0
                    # If tower receives enemy message from robots, broadcast the information to other
                    # towers. Additionally, spawn a splasher and a mopper
                    if Sensing.is_robot(rc, message.get_sender_id()):
                        globals()['broadcast'] = True
                        globals()['alert_attack_soldiers'] = True
                        globals()['spawn_queue'].append(3)  # Spawns a mopper
                        globals()['spawn_queue'].append(4)  # Spawns a splasher
                        globals()['num_enemy_visits'] += 1  # Increases probability of spawning a splasher

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
                    if Sensing.is_robot(rc, message.get_sender_id()):
                        globals()['broadcast'] = True
                        if random.random() <= 0.5:
                            globals()['spawn_queue'].append(4)  # Spawns a splasher
                        else:
                            globals()['spawn_queue'].append(3)  # Spawns a mopper
                        globals()['num_enemy_visits'] += 1  # Increases probability of spawning a splasher

                    # If tower receives message from tower, just alert the surrounding bots
                    globals()['alert_robots'] = True

                    # Update enemy tile regardless
                    globals()['enemy_target'] = msg

    @staticmethod
    def build_if_possible(rc, robot_type, location):
        """Builds a robot of type robot_type at location"""
        if rc.can_build_robot(robot_type, location):
            rc.build_robot(robot_type, location)

    @staticmethod
    def add_random_to_queue(rc):
        """Builds an advance/develop soldier, weighted by how long it has been since the tower last saw a robot"""
        if (random.random() < globals()['num_enemy_visits'] * 0.2 or 
            (globals()['num_soldiers_spawned'] > SPLASHER_CUTOFF and random.random() < SPLASHER_SOLDIER_SPLIT)):
            globals()['spawn_queue'].append(4)
            globals()['num_enemy_visits'] = 0
        else:
            globals()['num_soldiers_spawned'] += 1
            # odds of explore robot increases linearly from 30-70 to 60-40
            if random.random() < min((globals()['rounds_without_enemy'] + INIT_PROBABILITY_DEVELOP) / DEVELOP_BOT_PROB_SCALING,
                                   DEVELOP_BOT_PROBABILITY_CAP):
                globals()['spawn_queue'].append(0)
            else:
                globals()['spawn_queue'].append(1)

    @staticmethod
    def fire_attack_if_possible(rc, location):
        """Fires an attack at location if possible"""
        if rc.can_attack(location):
            rc.attack(location)

    @staticmethod
    def attack_lowest_robot(rc):
        """Attacks the robot with the lowest HP within attack range"""
        nearest_low_bot = Sensing.find_nearest_lowest_hp(rc)
        if nearest_low_bot is not None:
            Tower.fire_attack_if_possible(rc, nearest_low_bot.get_location())

    @staticmethod
    def aoe_attack_if_possible(rc):
        """Does an AOE attack if possible"""
        if rc.can_attack(None):
            rc.attack(None)

    @staticmethod
    def create_soldier(rc):
        """Creates a soldier at location NORTH if possible"""
        added_dir = rc.get_location().add(globals()['spawn_direction'])
        if Tower.start_square_covered(rc):
            if rc.can_build_robot(UnitType.MOPPER, added_dir):
                rc.build_robot(UnitType.MOPPER, added_dir)
                return
        if rc.can_build_robot(UnitType.SOLDIER, added_dir):
            rc.build_robot(UnitType.SOLDIER, added_dir)
            globals()['send_type_message'] = True

    @staticmethod
    def create_mopper(rc):
        """Creates a mopper at location NORTH if possible"""
        added_dir = rc.get_location().add(globals()['spawn_direction'])
        if rc.can_build_robot(UnitType.MOPPER, added_dir):
            rc.build_robot(UnitType.MOPPER, added_dir)
            globals()['send_type_message'] = True

    @staticmethod
    def create_splasher(rc):
        """Creates a splasher at the north"""
        added_dir = rc.get_location().add(globals()['spawn_direction'])
        if rc.can_build_robot(UnitType.SPLASHER, added_dir):
            rc.build_robot(UnitType.SPLASHER, added_dir)
            globals()['send_type_message'] = True

    @staticmethod
    def send_type_message(rc, robot_type):
        """Send message to the robot indicating what type of bot it is"""
        added_dir = rc.get_location().add(globals()['spawn_direction'])
        if rc.can_sense_robot_at_location(added_dir) and rc.can_send_message(added_dir):
            rc.send_message(added_dir, robot_type)
            # If robot is an attack soldier or mopper, send enemy tile location as well
            if robot_type in [4, 3, 2]:
                Communication.send_map_information(rc, globals()['enemy_target'], added_dir)
        globals()['send_type_message'] = False
        globals()['spawn_queue'].pop(0)

    @staticmethod
    def start_square_covered(rc):
        """Checks to see if that spawning square is covered with enemy paint"""
        return rc.sense_map_info(rc.get_location().add(globals()['spawn_direction'])).get_paint().is_enemy()

    @staticmethod
    def spawn_direction(rc):
        """Finds spawning direction for a given tower"""
        height = rc.get_map_height()
        width = rc.get_map_width()
        loc = rc.get_location()
        
        # Prefer spawning towards center
        center_x = width // 2
        center_y = height // 2
        
        best_dir = None
        min_dist = float('inf')
        
        for dir in directions:
            new_loc = loc.add(dir)
            if rc.can_build_robot(UnitType.SOLDIER, new_loc):
                dist = abs(new_loc.x - center_x) + abs(new_loc.y - center_y)
                if dist < min_dist:
                    min_dist = dist
                    best_dir = dir
        
        return best_dir
