
from production_lstm import one_hot, run_with_config

import numpy as np

import os


#--------------------------------------------
# Neural net's config.
#--------------------------------------------

class Config(object):
    """
    define a class to store parameters,
    the input should be feature mat of training and testing
    """

    def __init__(self, X_train, X_test):
        # Data shaping
        self.train_count = len(X_train)  # 7352 training series
        self.test_data_count = len(X_test)  # 2947 testing series
        self.n_steps = len(X_train[0]) # 128 time_steps per series
        self.n_classes = 3 # Final output classes

        # Training
        self.learning_rate = 0.001
        self.lambda_loss_amount = 0.005
        self.training_epochs = 300
        self.batch_size = 50
        self.clip_gradients = 15.0
        self.gradient_noise_scale = None
        # Dropout is added on inputs and after each stacked layers (but not
        # between residual layers).
        self.keep_prob_for_dropout = 3.0

        # Linear+relu structure
        self.bias_mean = 3.0
        # I would recommend between 0.1 and 1.0 or to change and use a xavier
        # initializer
        self.weights_stddev = 3.0

        ########
        # NOTE: I think that if any of the below parameters are changed,
        # the best is to readjust every parameters in the "Training" section
        # above to properly compare the architectures only once optimised.
        ########

        # LSTM structure
        # Features count is of 9: three 3D sensors features over time
        self.n_inputs = len(X_train[0][0])
        self.n_hidden = 32  # nb of neurons inside the neural network
        # Use bidir in every LSTM cell, or not:
        self.use_bidirectionnal_cells = True

        # High-level deep architecture
        self.also_add_dropout_between_stacked_cells = True  # True
        # NOTE: values of exactly 1 (int) for those 2 high-level parameters below totally disables them and result in only 1 starting LSTM.
        self.n_layers_in_highway = 3 # Number of residual connections to the LSTMs (highway-style), this is did for each stacked block (inside them).
        self.n_stacked_layers = 3 # Stack multiple blocks of residual layers.


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

# Output classes to learn how to classify
LABELS = [
    "LOW",
    "MID",
    "HIGH"
]

DATASET_PATH = "C:\\Users\\james\\Documents\\GitHub\\ADIM\\Activity Recognition\\HAR-stacked-residual-bidir-LSTMs-master\\OTrainOTest"

TRAIN = "\\train\\"
TEST = "\\test\\"

# Load "X" (the neural network's training and testing inputs)

def load_X(X_signals_paths):
    """
    Given attribute (train or test) of feature, read all 9 features into an
    np ndarray of shape [sample_sequence_idx, time_step, feature_num]
        argument:   X_signals_paths str attribute of feature: 'train' or 'test'
        return:     np ndarray, tensor of features
    """
    X_signals = []

    for signal_type_path in X_signals_paths:
        file = open(signal_type_path, 'r')
        # Read dataset from disk, dealing with text files' syntax
        X_signals.append(
            [np.array(serie, dtype=np.float32) for serie in [
                row.replace('  ', ' ').strip().split(' ') for row in file
            ]]
        )
        file.close()

    return np.transpose(np.array(X_signals), (1, 2, 0))

X_train_features_paths = [
    DATASET_PATH + TRAIN + "features_windowed\\" + feature + "_train.txt" for feature in INPUT_FEATURES
]

X_test_features_paths = [
    DATASET_PATH + TEST + "features_windowed\\" + feature + "_test.txt" for feature in INPUT_FEATURES
]

X_train = load_X(X_train_features_paths)
X_test = load_X(X_test_features_paths)

# Load "y" (the neural network's training and testing outputs)

def load_y(y_path):
    """
    Read Y file of values to be predicted
        argument: y_path str attibute of Y: 'train' or 'test'
        return: Y ndarray / tensor of the 6 one_hot labels of each sample
    """
    file = open(y_path, 'r')
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

y_train = load_y(DATASET_PATH + TRAIN + "features_windowed\\" + "y_train.txt")
y_test = load_y(DATASET_PATH + TEST + "features_windowed\\" + "y_test.txt")

run_with_config(Config, X_train, y_train, X_test, y_test)

# #--------------------------------------------
# # Training (maybe multiple) experiment(s)
# #--------------------------------------------

# n_layers_in_highway = 0
# n_stacked_layers = 3
# trial_name = "{}x{}".format(n_layers_in_highway, n_stacked_layers)

# for learning_rate in [0.001]:  # [0.01, 0.007, 0.001, 0.0007, 0.0001]:
#     for lambda_loss_amount in [0.005]:
#         for clip_gradients in [12.0]:
#             print("learning_rate: {}".format(learning_rate))
#             print("lambda_loss_amount: {}".format(lambda_loss_amount))
#             print("")

#             class EditedConfig(Config):
#                 def __init__(self, X, Y):
#                     super(EditedConfig, self).__init__(X, Y)

#                     # Edit only some parameters:
#                     self.learning_rate = learning_rate
#                     self.lambda_loss_amount = lambda_loss_amount
#                     #self.clip_gradients = clip_gradients
#                     # Architecture params:
#                     #self.n_layers_in_highway = n_layers_in_highway
#                     #self.n_stacked_layers = n_stacked_layers

#             # # Useful catch upon looping (e.g.: not enough memory)
#             # try:
#             #     accuracy_out, best_accuracy = run_with_config(EditedConfig)
#             # except:
#             #     accuracy_out, best_accuracy = -1, -1
#             accuracy_out, best_accuracy, f1_score_out, best_f1_score = (
#                 run_with_config(EditedConfig, X_train, y_train, X_test, y_test)
#             )
#             print (accuracy_out, best_accuracy, f1_score_out, best_f1_score)

#             with open('{}_result_HAR_6.txt'.format(trial_name), 'a') as f:
#                 f.write(str(learning_rate) + ' \t' + str(lambda_loss_amount) + ' \t' + str(clip_gradients) + ' \t' + str(
#                     accuracy_out) + ' \t' + str(best_accuracy) + ' \t' + str(f1_score_out) + ' \t' + str(best_f1_score) + '\n\n')

#             print("________________________________________________________")
#         print("")
# print("Done.")