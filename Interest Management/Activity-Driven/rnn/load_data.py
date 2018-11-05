import numpy as np
import os

#--------------------------------------------
# Dataset-specific constants and functions + loading
#--------------------------------------------

# Useful Constants

# Those are separate normalised input features for the neural network
INPUT_FEATURES = [
    "selfX",
    "selfY",
    "playerSpeed",
    "directionDelta",
    "NumAlivePlayers",
    "gearScore",
    "directCombatWeightedScore",
    "indirectCombatWeightedScore",
    "enemyWeightedProximity",
    "corpseWeightedProximity",
    "vehicleProximity",
    "itemProximity",
    "airDropWeightedProximity",
    "safeZoneProximity",
    "redZoneProximity"
]

DATASET_PATH = "C:\\Users\\james\\Documents\\GitHub\\ADIM\\Interest Management\\Activity-Driven\\rnn\\Datasets\\AllTraces\\"

def one_hot(y):
    y = y.reshape(len(y))
    n_values = np.max(y) + 1
    return np.eye(n_values)[np.array(y, dtype=np.int32)]  # Returns FLOATS


# Load "X" (the neural network's training and testing inputs)
def load_X(player_ids):

    player_samples = []

    for player_id in player_ids:
        X_signals = []
        X_test_features_paths = [
            DATASET_PATH +str(player_id)+"\\features_windowed\\" + feature + "_test.txt" for feature in INPUT_FEATURES
        ]
        for signal_type_path in X_test_features_paths:
            file = open(signal_type_path, 'r')
            # Read dataset from disk, dealing with text files' syntax
            X_signals.append(
                [np.array(serie, dtype=np.float32) for serie in [
                    row.replace('  ', ' ').strip().split(' ') for row in file
                ]]
            )
            file.close()
        player_samples.append(np.transpose(np.array(X_signals), (1, 2, 0)))
    print("Samples loaded")
    return player_samples

# Load "y" (the neural network's training and testing outputs)
def load_Y(player_ids):
    player_labels = []

    for player_id in player_ids:
        file = open(DATASET_PATH +str(player_id)+"\\features_windowed\\" + "y_test.txt", 'r')
        # Read dataset from disk, dealing with text file's syntax
        y_ = np.array(
            [elem for elem in [
                row.replace('  ', ' ').strip().split(' ') for row in file
            ]],
            dtype=np.int32
        )
        file.close()

        # Substract 1 to each output class for friendly 0-based indexing
        player_labels.append(one_hot(y_ - 1))
    print("Labels loaded")
    return player_labels