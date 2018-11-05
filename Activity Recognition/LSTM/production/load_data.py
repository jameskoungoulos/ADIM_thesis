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

DATASET_PATH = "C:\\Users\\james\\Documents\\GitHub\\ADIM\\Activity Recognition\\HAR-stacked-residual-bidir-LSTMs-master\\UOUOTrace\\test\\"

X_test_features_paths = [
    DATASET_PATH + "features_windowed\\" + feature + "_test.txt" for feature in INPUT_FEATURES
]

def one_hot(y):
    y = y.reshape(len(y))
    n_values = np.max(y) + 1
    return np.eye(n_values)[np.array(y, dtype=np.int32)]  # Returns FLOATS

# Load "y" (the neural network's training and testing outputs)
def load_Y():
    """
    Read Y file of values to be predicted
        argument: y_path str attibute of Y: 'train' or 'test'
        return: Y ndarray / tensor of the 6 one_hot labels of each sample
    """
    file = open(DATASET_PATH + "features_windowed\\" + "y_test.txt", 'r')
    # Read dataset from disk, dealing with text file's syntax
    y_ = np.array(
        [elem for elem in [
            row.replace('  ', ' ').strip().split(' ') for row in file
        ]],
        dtype=np.int32
    )
    file.close()

    # Substract 1 to each output class for friendly 0-based indexing
    return one_hot(y_ - 1)

# Load "X" (the neural network's training and testing inputs)
def load_X():

    X_signals = []

    for signal_type_path in X_test_features_paths:
        file = open(signal_type_path, 'r')
        # Read dataset from disk, dealing with text files' syntax
        X_signals.append(
            [np.array(serie, dtype=np.float32) for serie in [
                row.replace('  ', ' ').strip().split(' ') for row in file
            ]]
        )
        file.close()

    return np.transpose(np.array(X_signals), (1, 2, 0))
