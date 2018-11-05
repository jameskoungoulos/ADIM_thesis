HIGH_TICK = 45 # 22.22ms delay
MEDIUM_TICK = 30 # 33.33ms delay
LOW_TICK = 10 # 100ms delay

# POLL_RATE = 0.2

# SERVER_DELAY = 0.01
# PLAYER_DELAY = 0.2
# RECV_POLL_RATE = 0.1

SAMPLE_PERIOD = 0.453 # e.g. TPP_GROUP_WIN has 7468 rows and goes for 1698 seconds - 1698 / 3711 (windowed) ~= 0.458 samples a second - 0.005 (average cost of a single loop) = 0.453