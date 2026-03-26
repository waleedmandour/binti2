#!/usr/bin/env python3
"""
Wake Word Training Script for Binti2
Trains a TensorFlow Lite model to detect "يا بنتي" (Ya Binti)

Usage:
    python train_wake_word.py --data_dir=./dataset --output=./ya_binti_detector.tflite

Prerequisites:
    pip install tensorflow tflite-model-maker

Dataset Structure:
    dataset/
        ya_binti/          # Positive samples - recordings of "يا بنتي"
            sample1.wav
            sample2.wav
            ...
        not_ya_binti/      # Negative samples - other speech
            other1.wav
            other2.wav
            ...
        _background_noise_/  # Background noise (optional)
            noise1.wav
            noise2.wav
            ...

Author: Dr. Waleed Mandour
"""

import os
import argparse
import numpy as np
import tensorflow as tf
from pathlib import Path

# Audio parameters matching WakeWordDetector.kt
SAMPLE_RATE = 16000
NUM_MFCC = 40
DURATION_MS = 1000

def parse_args():
    parser = argparse.ArgumentParser(description='Train wake word detector')
    parser.add_argument('--data_dir', type=str, required=True,
                        help='Path to training data directory')
    parser.add_argument('--output', type=str, default='ya_binti_detector.tflite',
                        help='Output TFLite model path')
    parser.add_argument('--epochs', type=int, default=50,
                        help='Number of training epochs')
    parser.add_argument('--batch_size', type=int, default=32,
                        help='Batch size for training')
    parser.add_argument('--model_type', type=str, default='micro_speech',
                        choices=['micro_speech', 'custom_cnn', 'resnet'],
                        help='Model architecture type')
    return parser.parse_args()


def load_audio_files(data_dir):
    """Load and preprocess audio files from directory structure"""
    import librosa
    
    X = []
    y = []
    
    # Load positive samples (wake word)
    wake_dir = Path(data_dir) / 'ya_binti'
    if wake_dir.exists():
        for audio_file in wake_dir.glob('*.wav'):
            audio, sr = librosa.load(audio_file, sr=SAMPLE_RATE, duration=DURATION_MS/1000)
            # Pad or truncate to fixed length
            if len(audio) < SAMPLE_RATE:
                audio = np.pad(audio, (0, SAMPLE_RATE - len(audio)))
            else:
                audio = audio[:SAMPLE_RATE]
            X.append(audio)
            y.append(1)  # Wake word = 1
        print(f"Loaded {len([f for f in wake_dir.glob('*.wav')])} positive samples")
    
    # Load negative samples (not wake word)
    neg_dir = Path(data_dir) / 'not_ya_binti'
    if neg_dir.exists():
        for audio_file in neg_dir.glob('*.wav'):
            audio, sr = librosa.load(audio_file, sr=SAMPLE_RATE, duration=DURATION_MS/1000)
            if len(audio) < SAMPLE_RATE:
                audio = np.pad(audio, (0, SAMPLE_RATE - len(audio)))
            else:
                audio = audio[:SAMPLE_RATE]
            X.append(audio)
            y.append(0)  # Not wake word = 0
        print(f"Loaded {len([f for f in neg_dir.glob('*.wav')])} negative samples")
    
    # Load background noise
    noise_dir = Path(data_dir) / '_background_noise_'
    if noise_dir.exists():
        for noise_file in noise_dir.glob('*.wav'):
            audio, sr = librosa.load(noise_file, sr=SAMPLE_RATE, duration=DURATION_MS/1000)
            if len(audio) < SAMPLE_RATE:
                audio = np.pad(audio, (0, SAMPLE_RATE - len(audio)))
            else:
                audio = audio[:SAMPLE_RATE]
            X.append(audio)
            y.append(0)  # Noise = 0
        print(f"Loaded {len([f for f in noise_dir.glob('*.wav')])} noise samples")
    
    return np.array(X), np.array(y)


def extract_mfcc(audio, sr=SAMPLE_RATE, n_mfcc=NUM_MFCC):
    """Extract MFCC features from audio"""
    import librosa
    
    # Compute MFCCs
    mfcc = librosa.feature.mfcc(
        y=audio,
        sr=sr,
        n_mfcc=n_mfcc,
        n_fft=400,  # 25ms
        hop_length=160,  # 10ms
        n_mels=40
    )
    
    # Normalize
    mfcc = (mfcc - np.mean(mfcc)) / (np.std(mfcc) + 1e-8)
    
    # Transpose to (time, features)
    return mfcc.T


def build_micro_speech_model(input_shape):
    """Build Micro Speech architecture model"""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=input_shape),
        
        # Reshape for CNN
        tf.keras.layers.Reshape((input_shape[0], input_shape[1], 1)),
        
        # First conv block
        tf.keras.layers.Conv2D(8, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.Dropout(0.2),
        
        # Second conv block
        tf.keras.layers.Conv2D(16, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.Dropout(0.2),
        
        # Third conv block
        tf.keras.layers.Conv2D(32, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.MaxPooling2D((2, 2)),
        tf.keras.layers.Dropout(0.3),
        
        # Flatten and dense
        tf.keras.layers.Flatten(),
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.4),
        tf.keras.layers.Dense(2, activation='softmax')  # [not_wake_word, wake_word]
    ])
    
    return model


def build_custom_cnn_model(input_shape):
    """Build custom CNN model optimized for wake word detection"""
    model = tf.keras.Sequential([
        tf.keras.layers.Input(shape=input_shape),
        tf.keras.layers.Reshape((input_shape[0], input_shape[1], 1)),
        
        # Depthwise separable conv for efficiency
        tf.keras.layers.SeparableConv2D(32, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.MaxPooling2D((2, 2)),
        
        tf.keras.layers.SeparableConv2D(64, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.MaxPooling2D((2, 2)),
        
        tf.keras.layers.SeparableConv2D(128, (3, 3), activation='relu', padding='same'),
        tf.keras.layers.BatchNormalization(),
        tf.keras.layers.GlobalAveragePooling2D(),
        
        tf.keras.layers.Dense(64, activation='relu'),
        tf.keras.layers.Dropout(0.5),
        tf.keras.layers.Dense(2, activation='softmax')
    ])
    
    return model


def build_resnet_model(input_shape):
    """Build ResNet-inspired model for better accuracy"""
    inputs = tf.keras.Input(shape=input_shape)
    x = tf.keras.layers.Reshape((input_shape[0], input_shape[1], 1))(inputs)
    
    # Initial conv
    x = tf.keras.layers.Conv2D(32, (3, 3), activation='relu', padding='same')(x)
    x = tf.keras.layers.BatchNormalization()(x)
    
    # Residual blocks
    for filters in [32, 64, 128]:
        # Residual connection
        shortcut = x
        if x.shape[-1] != filters:
            shortcut = tf.keras.layers.Conv2D(filters, (1, 1))(shortcut)
        
        x = tf.keras.layers.Conv2D(filters, (3, 3), activation='relu', padding='same')(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.Conv2D(filters, (3, 3), padding='same')(x)
        x = tf.keras.layers.BatchNormalization()(x)
        x = tf.keras.layers.Add()([x, shortcut])
        x = tf.keras.layers.Activation('relu')(x)
        x = tf.keras.layers.MaxPooling2D((2, 2))(x)
    
    # Output
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dense(64, activation='relu')(x)
    x = tf.keras.layers.Dropout(0.5)(x)
    outputs = tf.keras.layers.Dense(2, activation='softmax')(x)
    
    return tf.keras.Model(inputs, outputs)


def train_model(X, y, model_type, epochs, batch_size):
    """Train the wake word detection model"""
    from sklearn.model_selection import train_test_split
    
    # Extract MFCC features
    print("Extracting MFCC features...")
    X_features = np.array([extract_mfcc(x) for x in X])
    print(f"Feature shape: {X_features.shape}")
    
    # Split data
    X_train, X_test, y_train, y_test = train_test_split(
        X_features, y, test_size=0.2, random_state=42, stratify=y
    )
    
    # Build model
    input_shape = X_features.shape[1:]
    print(f"Building {model_type} model...")
    
    if model_type == 'micro_speech':
        model = build_micro_speech_model(input_shape)
    elif model_type == 'custom_cnn':
        model = build_custom_cnn_model(input_shape)
    elif model_type == 'resnet':
        model = build_resnet_model(input_shape)
    
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
        loss='sparse_categorical_crossentropy',
        metrics=['accuracy']
    )
    
    model.summary()
    
    # Callbacks
    callbacks = [
        tf.keras.callbacks.EarlyStopping(
            monitor='val_loss',
            patience=10,
            restore_best_weights=True
        ),
        tf.keras.callbacks.ReduceLROnPlateau(
            monitor='val_loss',
            factor=0.5,
            patience=5,
            min_lr=1e-6
        ),
        tf.keras.callbacks.ModelCheckpoint(
            'best_model.h5',
            monitor='val_accuracy',
            save_best_only=True
        )
    ]
    
    # Train
    print("Training model...")
    history = model.fit(
        X_train, y_train,
        validation_data=(X_test, y_test),
        epochs=epochs,
        batch_size=batch_size,
        callbacks=callbacks,
        class_weight={0: 1.0, 1: 2.0}  # Weight wake word more
    )
    
    # Evaluate
    loss, accuracy = model.evaluate(X_test, y_test)
    print(f"\nTest Accuracy: {accuracy:.4f}")
    
    return model, history


def convert_to_tflite(model, output_path):
    """Convert model to TensorFlow Lite format"""
    print("Converting to TFLite...")
    
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    
    # Optimizations for size
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    # Quantize for smaller size (optional)
    # converter.target_spec.supported_types = [tf.float16]
    
    tflite_model = converter.convert()
    
    # Save model
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    print(f"Model saved to: {output_path}")
    print(f"Model size: {len(tflite_model) / 1024:.2f} KB")


def main():
    args = parse_args()
    
    # Check data directory
    if not os.path.exists(args.data_dir):
        print(f"Error: Data directory {args.data_dir} not found")
        return
    
    # Load data
    print(f"Loading data from {args.data_dir}...")
    X, y = load_audio_files(args.data_dir)
    
    if len(X) == 0:
        print("Error: No audio files found")
        return
    
    print(f"Total samples: {len(X)}")
    print(f"Positive samples (ya_binti): {sum(y)}")
    print(f"Negative samples (not_ya_binti): {len(y) - sum(y)}")
    
    # Train model
    model, history = train_model(
        X, y,
        model_type=args.model_type,
        epochs=args.epochs,
        batch_size=args.batch_size
    )
    
    # Convert to TFLite
    convert_to_tflite(model, args.output)
    
    print("\nTraining complete!")
    print(f"Copy {args.output} to: binti2/app/src/main/assets/models/wake/")


if __name__ == '__main__':
    main()
