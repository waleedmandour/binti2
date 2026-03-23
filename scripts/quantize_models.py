#!/usr/bin/env python3
"""
Model Quantization Script for Binti

Converts PyTorch models to quantized TFLite/ONNX format for mobile deployment.

Usage:
    python quantize_models.py --model wake_word --input model.pt --output model.tflite
    python quantize_models.py --model asr --input model.pt --output model.onnx
    python quantize_models.py --model nlu --input model.pt --output model.tflite
"""

import argparse
import os
import sys
from pathlib import Path

try:
    import torch
    import torch.onnx
    import onnx
    from onnxruntime.quantization import quantize_dynamic, QuantType
    import tensorflow as tf
except ImportError:
    print("Please install required packages:")
    print("pip install torch onnx onnxruntime tensorflow")
    sys.exit(1)


def quantize_wake_word(input_path: str, output_path: str):
    """Quantize wake word model to TFLite INT8"""
    print(f"Quantizing wake word model: {input_path}")
    
    # Load PyTorch model
    model = torch.jit.load(input_path)
    model.eval()
    
    # Convert to ONNX first
    dummy_input = torch.randn(1, 40, 98)  # MFCC features
    onnx_path = output_path.replace('.tflite', '.onnx')
    
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        input_names=['input'],
        output_names=['output'],
        dynamic_axes={'input': {0: 'batch', 1: 'time'}}
    )
    
    # Convert to TFLite via TensorFlow
    # This is a simplified version - actual conversion may need more steps
    converter = tf.lite.TFLiteConverter.from_onnx_model(onnx_path)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.int8]
    
    tflite_model = converter.convert()
    
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    # Clean up intermediate file
    os.remove(onnx_path)
    
    print(f"Wake word model saved to: {output_path}")
    print(f"Size: {os.path.getsize(output_path) / 1024 / 1024:.2f} MB")


def quantize_asr(input_path: str, output_path: str):
    """Quantize ASR model to ONNX INT8"""
    print(f"Quantizing ASR model: {input_path}")
    
    # Load model
    model = torch.jit.load(input_path)
    model.eval()
    
    # Export to ONNX
    dummy_input = torch.randn(1, 1000, 80)  # Log-mel features
    
    temp_onnx = output_path.replace('.onnx', '_temp.onnx')
    
    torch.onnx.export(
        model,
        dummy_input,
        temp_onnx,
        input_names=['input'],
        output_names=['logits'],
        dynamic_axes={
            'input': {0: 'batch', 1: 'time'},
            'logits': {0: 'batch', 1: 'time'}
        }
    )
    
    # Dynamic quantization for ONNX
    quantize_dynamic(
        temp_onnx,
        output_path,
        weight_type=QuantType.QInt8
    )
    
    # Clean up
    os.remove(temp_onnx)
    
    print(f"ASR model saved to: {output_path}")
    print(f"Size: {os.path.getsize(output_path) / 1024 / 1024:.2f} MB")


def quantize_nlu(input_path: str, output_path: str):
    """Quantize NLU model to TFLite INT8"""
    print(f"Quantizing NLU model: {input_path}")
    
    # Load PyTorch model
    model = torch.jit.load(input_path)
    model.eval()
    
    # Create representative dataset for full integer quantization
    def representative_dataset():
        for _ in range(100):
            yield [torch.randint(0, 30000, (1, 128)).numpy()]
    
    # Export to ONNX
    dummy_input = torch.randint(0, 30000, (1, 128))
    onnx_path = output_path.replace('.tflite', '.onnx')
    
    torch.onnx.export(
        model,
        dummy_input,
        onnx_path,
        input_names=['input_ids'],
        output_names=['logits'],
        dynamic_axes={
            'input_ids': {0: 'batch', 1: 'sequence'},
            'logits': {0: 'batch'}
        }
    )
    
    # Convert to TFLite
    converter = tf.lite.TFLiteConverter.from_onnx_model(onnx_path)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = representative_dataset
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.int8
    converter.inference_output_type = tf.int8
    
    tflite_model = converter.convert()
    
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    # Clean up
    os.remove(onnx_path)
    
    print(f"NLU model saved to: {output_path}")
    print(f"Size: {os.path.getsize(output_path) / 1024 / 1024:.2f} MB")


def main():
    parser = argparse.ArgumentParser(description='Quantize models for Binti')
    parser.add_argument('--model', required=True, choices=['wake_word', 'asr', 'nlu'],
                        help='Model type to quantize')
    parser.add_argument('--input', required=True, help='Input model path')
    parser.add_argument('--output', required=True, help='Output model path')
    
    args = parser.parse_args()
    
    # Ensure output directory exists
    os.makedirs(os.path.dirname(args.output), exist_ok=True)
    
    if args.model == 'wake_word':
        quantize_wake_word(args.input, args.output)
    elif args.model == 'asr':
        quantize_asr(args.input, args.output)
    elif args.model == 'nlu':
        quantize_nlu(args.input, args.output)


if __name__ == '__main__':
    main()
