from battlecode25.stubs import *
import communication
import robot_info_codec
import map_info_codec
import sensing

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
                globals()['alert_robots'] = True
                globals()['enemy_target'] = msg
                globals()['enemy_tower'] = msg
            # Check if message is enemy paint
            elif msg.get_paint().is_enemy():
                globals()['rounds_without_enemy'] = 0
                if sensing.is_robot(rc, message.get_sender_id()):
                    globals()['broadcast'] = True
                    globals()['num_enemy_visits'] = globals()['num_enemy_visits'] + 1  # Increases probability of spawning a splasher
                # If tower receives message from tower, just alert the surrounding bots
                globals()['alert_robots'] = True
                # Update enemy tile regardless
                globals()['enemy_target'] = msg
