#!/usr/bin/env python3
"""
Binti2 B2 Model Upload Script

This script uploads AI model files to Backblaze B2 cloud storage.
It handles authentication, bucket creation, and file uploads with SHA256 verification.

Usage:
    python scripts/upload_to_b2.py --key-id YOUR_KEY_ID --app-key YOUR_APP_KEY
    
Or with environment variables:
    export B2_KEY_ID=your_key_id
    export B2_APP_KEY=your_app_key
    python scripts/upload_to_b2.py
"""

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime
from pathlib import Path

try:
    from b2sdk.v2 import (
        B2Api,
        InMemoryAccountInfo,
        Bucket,
    )
except ImportError:
    print("Installing b2sdk...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "b2sdk", "-q"])
    from b2sdk.v2 import (
        B2Api,
        InMemoryAccountInfo,
        Bucket,
    )

# Configuration
BUCKET_NAME = "binti2-models"
MODELS_DIR = Path(__file__).parent.parent / "models_to_upload"
APP_DIR = Path(__file__).parent.parent

# Model files to upload
MODELS = [
    {
        "name": "Wake Word Detector",
        "local_path": "ya_binti_detector.tflite",
        "b2_path": "wake/ya_binti_detector.tflite",
        "required": True,
        "size_mb": 5,
    },
    {
        "name": "Arabic ASR (Vosk MGB2)",
        "local_path": "vosk-model-ar-mgb2.zip",
        "b2_path": "asr/vosk-model-ar-mgb2.zip",
        "required": True,
        "size_mb": 1247,
    },
    {
        "name": "Intent Classifier (EgyBERT)",
        "local_path": "egybert_tiny_int8.onnx",
        "b2_path": "nlu/egybert_tiny_int8.onnx",
        "required": True,
        "size_mb": 25,
    },
    {
        "name": "Egyptian TTS Voice",
        "local_path": "ar-eg-female.zip",
        "b2_path": "tts/ar-eg-female.zip",
        "required": False,
        "size_mb": 80,
    },
    {
        "name": "Intent Patterns",
        "local_path": "nlp/dilink_intent_map.json",
        "b2_path": "nlp/dilink_intent_map.json",
        "required": True,
        "size_mb": 0.1,
        "source": "app",
    },
]


def calculate_sha256(file_path: Path) -> str:
    """Calculate SHA256 hash of a file."""
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            sha256_hash.update(chunk)
    return sha256_hash.hexdigest()


def format_size(size_bytes: int) -> str:
    """Format file size in human readable format."""
    for unit in ["B", "KB", "MB", "GB"]:
        if size_bytes < 1024:
            return f"{size_bytes:.1f} {unit}"
        size_bytes /= 1024
    return f"{size_bytes:.1f} TB"


class B2Uploader:
    """Handles B2 authentication and file uploads."""

    def __init__(self, key_id: str, app_key: str):
        self.key_id = key_id
        self.app_key = app_key
        self.api = None
        self.bucket = None

    def connect(self):
        """Connect to B2 and authenticate."""
        print("🔐 Authenticating with Backblaze B2...")
        
        info = InMemoryAccountInfo()
        self.api = B2Api(info)
        
        try:
            self.api.authorize_account("production", self.key_id, self.app_key)
            print("✅ Authentication successful!")
            
            # Get account info
            account_info = self.api.account_info
            print(f"   Account: {account_info.get_account_id()}")
            
        except Exception as e:
            print(f"❌ Authentication failed: {e}")
            sys.exit(1)

    def get_or_create_bucket(self) -> Bucket:
        """Get existing bucket or create a new one."""
        print(f"\n📦 Checking bucket: {BUCKET_NAME}")
        
        try:
            self.bucket = self.api.get_bucket_by_name(BUCKET_NAME)
            print(f"✅ Bucket '{BUCKET_NAME}' found")
        except Exception:
            print(f"Creating bucket '{BUCKET_NAME}'...")
            self.bucket = self.api.create_bucket(BUCKET_NAME, "public")
            print(f"✅ Bucket created")
        
        return self.bucket

    def upload_file(self, local_path: Path, b2_path: str, model_name: str) -> dict:
        """Upload a single file to B2."""
        if not local_path.exists():
            print(f"⚠️  File not found: {local_path}")
            return None
        
        size = local_path.stat().st_size
        sha256 = calculate_sha256(local_path)
        
        print(f"\n📤 Uploading: {model_name}")
        print(f"   File: {local_path}")
        print(f"   Size: {format_size(size)}")
        print(f"   SHA256: {sha256}")
        
        try:
            file_info = self.bucket.upload_local_file(
                local_file=str(local_path),
                file_name=b2_path,
            )
            
            print(f"✅ Uploaded: {b2_path}")
            print(f"   URL: https://f001.backblazeb2.com/file/{BUCKET_NAME}/{b2_path}")
            
            return {
                "name": model_name,
                "file": b2_path,
                "url": f"https://f001.backblazeb2.com/file/{BUCKET_NAME}/{b2_path}",
                "size_bytes": size,
                "sha256": sha256,
                "id": file_info.id_,
            }
            
        except Exception as e:
            print(f"❌ Upload failed: {e}")
            return None

    def upload_manifest(self, results: list):
        """Create and upload manifest.json."""
        manifest = {
            "version": "1.0.0",
            "updated": datetime.now().isoformat(),
            "bucket": BUCKET_NAME,
            "base_url": f"https://f001.backblazeb2.com/file/{BUCKET_NAME}",
            "models": {},
        }
        
        for model, result in zip(MODELS, results):
            if result:
                manifest["models"][model["b2_path"].split("/")[0]] = {
                    "name": model["name"],
                    "file": model["b2_path"],
                    "url": result["url"],
                    "size_mb": model["size_mb"],
                    "sha256": result["sha256"],
                    "required": model["required"],
                }
        
        # Write local manifest
        manifest_path = MODELS_DIR / "manifest.json"
        manifest_path.write_text(json.dumps(manifest, indent=2))
        
        # Upload to B2
        print("\n📤 Uploading: manifest.json")
        self.bucket.upload_local_file(
            local_file=str(manifest_path),
            file_name="manifest.json",
        )
        print("✅ Uploaded: manifest.json")


def main():
    parser = argparse.ArgumentParser(description="Upload Binti2 models to Backblaze B2")
    parser.add_argument("--key-id", help="B2 Key ID", default=os.environ.get("B2_KEY_ID"))
    parser.add_argument("--app-key", help="B2 Application Key", default=os.environ.get("B2_APP_KEY"))
    parser.add_argument("--bucket", help="Bucket name", default=BUCKET_NAME)
    
    args = parser.parse_args()
    
    # Check credentials
    if not args.key_id or not args.app_key:
        print("❌ Error: B2 credentials required")
        print("\nSet environment variables:")
        print("  export B2_KEY_ID=your_key_id")
        print("  export B2_APP_KEY=your_app_key")
        print("\nOr pass as arguments:")
        print("  python scripts/upload_to_b2.py --key-id KEY_ID --app-key APP_KEY")
        sys.exit(1)
    
    # Create models directory
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    
    print("╔════════════════════════════════════════════════════════════╗")
    print("║       Binti2 - Backblaze B2 Model Upload Script            ║")
    print("╚════════════════════════════════════════════════════════════╝")
    
    # Initialize uploader
    uploader = B2Uploader(args.key_id, args.app_key)
    uploader.connect()
    uploader.get_or_create_bucket()
    
    # Copy intent map to models directory
    intent_src = APP_DIR / "app/src/main/assets/commands/dilink_intent_map.json"
    intent_dst = MODELS_DIR / "nlp/dilink_intent_map.json"
    intent_dst.parent.mkdir(parents=True, exist_ok=True)
    if intent_src.exists():
        intent_dst.write_text(intent_src.read_text())
    
    # Upload models
    print("\n════════════════════════════════════════════════════════════")
    print("Uploading Model Files")
    print("════════════════════════════════════════════════════════════")
    
    results = []
    for model in MODELS:
        if model.get("source") == "app":
            local_path = MODELS_DIR / model["local_path"]
        else:
            local_path = MODELS_DIR / model["local_path"]
        
        result = uploader.upload_file(local_path, model["b2_path"], model["name"])
        results.append(result)
    
    # Upload manifest
    uploader.upload_manifest(results)
    
    # Completion message
    print("\n════════════════════════════════════════════════════════════")
    print("✅ Upload Complete!")
    print("════════════════════════════════════════════════════════════")
    print(f"\nBase URL: https://f001.backblazeb2.com/file/{BUCKET_NAME}")
    print(f"Manifest: https://f001.backblazeb2.com/file/{BUCKET_NAME}/manifest.json")
    print("\nTest your setup:")
    print(f"  curl -I https://f001.backblazeb2.com/file/{BUCKET_NAME}/manifest.json")


if __name__ == "__main__":
    main()
