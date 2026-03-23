#!/bin/bash
# Download Models Script for Binti
# Downloads AI models from GitHub Releases

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
MODELS_DIR="$PROJECT_ROOT/app/src/main/assets/models"
VOICES_DIR="$PROJECT_ROOT/app/src/main/assets/voices"

# GitHub repository
GITHUB_REPO="waleedmandour/binti2"
GITHUB_API="https://api.github.com/repos/$GITHUB_REPO"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}🎙️ Binti Model Downloader${NC}"
echo "================================"

# Create directories
mkdir -p "$MODELS_DIR/wake"
mkdir -p "$MODELS_DIR/asr"
mkdir -p "$MODELS_DIR/nlu"
mkdir -p "$VOICES_DIR/ar-EG-female"

# Get latest release
get_latest_release() {
    curl -s "$GITHUB_API/releases/latest" | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/'
}

# Download file with progress
download_file() {
    local url="$1"
    local output="$2"
    
    echo -e "${YELLOW}Downloading: $output${NC}"
    
    if command -v wget &> /dev/null; then
        wget -q --show-progress -O "$output" "$url"
    elif command -v curl &> /dev/null; then
        curl -L --progress-bar -o "$output" "$url"
    else
        echo -e "${RED}Error: Neither wget nor curl found${NC}"
        exit 1
    fi
}

# Main download function
download_models() {
    local version="$1"
    local base_url="https://github.com/$GITHUB_REPO/releases/download/$version"
    
    echo -e "${GREEN}Downloading models for version: $version${NC}"
    echo ""
    
    # Wake Word Model
    if [ ! -f "$MODELS_DIR/wake/ya_binti_detector.tflite" ]; then
        download_file "$base_url/ya_binti_detector.tflite" "$MODELS_DIR/wake/ya_binti_detector.tflite"
    else
        echo -e "${GREEN}✓ Wake word model already exists${NC}"
    fi
    
    # ASR Model
    if [ ! -f "$MODELS_DIR/asr/hubert_egyptian_int8.onnx" ]; then
        download_file "$base_url/hubert_egyptian_int8.onnx" "$MODELS_DIR/asr/hubert_egyptian_int8.onnx"
    else
        echo -e "${GREEN}✓ ASR model already exists${NC}"
    fi
    
    # ASR Vocabulary
    if [ ! -f "$MODELS_DIR/asr/vocab.txt" ]; then
        download_file "$base_url/asr_vocab.txt" "$MODELS_DIR/asr/vocab.txt"
    else
        echo -e "${GREEN}✓ ASR vocabulary already exists${NC}"
    fi
    
    # NLU Model
    if [ ! -f "$MODELS_DIR/nlu/egybert_tiny_int8.tflite" ]; then
        download_file "$base_url/egybert_tiny_int8.tflite" "$MODELS_DIR/nlu/egybert_tiny_int8.tflite"
    else
        echo -e "${GREEN}✓ NLU model already exists${NC}"
    fi
    
    # NLU Vocabulary
    if [ ! -f "$MODELS_DIR/nlu/vocab.txt" ]; then
        download_file "$base_url/nlu_vocab.txt" "$MODELS_DIR/nlu/vocab.txt"
    else
        echo -e "${GREEN}✓ NLU vocabulary already exists${NC}"
    fi
    
    # NLU Labels
    if [ ! -f "$MODELS_DIR/nlu/intent_labels.txt" ]; then
        download_file "$base_url/intent_labels.txt" "$MODELS_DIR/nlu/intent_labels.txt"
    else
        echo -e "${GREEN}✓ Intent labels already exists${NC}"
    fi
    
    # Voice Pack
    if [ ! -f "$VOICES_DIR/ar-EG-female/voice_pack.zip" ]; then
        download_file "$base_url/ar_eg_female_voice.zip" "$VOICES_DIR/ar-EG-female/voice_pack.zip"
    else
        echo -e "${GREEN}✓ Voice pack already exists${NC}"
    fi
    
    echo ""
    echo -e "${GREEN}✅ All models downloaded successfully!${NC}"
}

# Verify checksums
verify_checksums() {
    local checksum_file="$PROJECT_ROOT/models/checksums.sha256"
    
    if [ -f "$checksum_file" ]; then
        echo -e "${YELLOW}Verifying checksums...${NC}"
        sha256sum -c "$checksum_file" || {
            echo -e "${RED}Checksum verification failed!${NC}"
            exit 1
        }
        echo -e "${GREEN}✅ All checksums verified${NC}"
    else
        echo -e "${YELLOW}No checksum file found, skipping verification${NC}"
    fi
}

# Main
main() {
    # Check for version argument
    if [ -n "$1" ]; then
        VERSION="$1"
    else
        echo "Fetching latest release..."
        VERSION=$(get_latest_release)
    fi
    
    if [ -z "$VERSION" ]; then
        echo -e "${RED}Error: Could not determine latest version${NC}"
        exit 1
    fi
    
    download_models "$VERSION"
    verify_checksums
    
    echo ""
    echo -e "${GREEN}🎉 Setup complete! You can now build the project.${NC}"
}

# Run main function with all arguments
main "$@"
