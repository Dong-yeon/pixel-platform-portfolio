-- Demo fleet. Robots start OFFLINE; the simulator/ROS bridge brings them online
-- by publishing status telemetry on fleet/{robot_code}/status.
insert into robots (robot_code, name, status, battery_percent, pos_x, pos_y, created_at, updated_at)
values
    ('AMR-01', '1호기', 'OFFLINE', 100, 0, 0, now(), now()),
    ('AMR-02', '2호기', 'OFFLINE', 100, 0, 0, now(), now()),
    ('AMR-03', '3호기', 'OFFLINE', 100, 0, 0, now(), now());
