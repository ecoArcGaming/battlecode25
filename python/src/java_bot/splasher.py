from battlecode25.stubs import *
import robot
import communication
import map_info_codec
import robot_info_codec

def receive_last_message():
    """Receives and processes messages from the last round"""
    for message in read_messages(get_round_num() - 1):
        bytes = message.get_bytes()
        # Skip splasher type message
        if bytes == 4:
            continue
            
        if communication.is_robot_info(bytes):
            robot_info = robot_info_codec.RobotInfoCodec.decode(bytes)
            continue
        else:
            map_info = map_info_codec.MapInfoCodec.decode(bytes)
            # If enemy paint, store enemy paint
            if map_info.get_paint().is_enemy():
                robot_loc = get_location()
                if (globals()['remove_paint'] is None or 
                    robot_loc.distance_squared_to(map_info.get_map_location()) < 
                    robot_loc.distance_squared_to(globals()['remove_paint'].get_map_location())):
                    globals()['remove_paint'] = map_info
                    robot.reset_variables()
            # If enemy tower, go to enemy tower location
            elif map_info.has_ruin():
                if globals()['remove_paint'] is None:
                    globals()['remove_paint'] = map_info
                    robot.reset_variables()