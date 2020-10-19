import glob
import os
import matplotlib.pyplot as plt

import librosa
import numpy as np


bands = 60
frames = 40
hop_length=256
n_fft=1024

window_size = hop_length * (frames - 1)
sample_rate = 8000

def windows(data, window_size):
    start = 0
    while start < len(data):
        yield start, start + window_size
        start += (window_size // 2)

log_specgrams = []
sound_clip, _ = librosa.load('aedes_aegypti1.wav', sr=sample_rate)
for (start, end) in windows(sound_clip, window_size):
    print(start)
    print(end)
    input('here')
    if (len(sound_clip[start:end]) == window_size):
        signal = sound_clip[start:end]
        print(signal)
        input('signal')
        melspec = librosa.feature.melspectrogram(signal, n_mels=bands, sr=sample_rate, n_fft=n_fft, hop_length=hop_length)
        print(melspec)
        input('melspec')
        logspec = librosa.power_to_db(melspec, ref=np.max)
        print(logspec)
        input('powertodb')
        logspec = logspec / 80 + 1
        print(logspec)
        input('logspec by 80 plus 1')
        logspec = logspec.T.flatten()[:, np.newaxis].T
        print(logspec)
        input('flattened logspec?')
        log_specgrams.append(logspec)

print (log_specgrams)

