#!/usr/bin/env python3
"""
DiLink Intent Reverse-Engineering Helper

This script helps discover DiLink intents by monitoring ADB logcat output
when interacting with the DiLink system.

Usage:
    python test_dilink_commands.py --device <device_ip>:5555
    python test_dilink_commands.py --discover
    python test_dilink_commands.py --test-command "navigate_home"
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

# Discovered DiLink intents
DILINK_INTENTS = {
    "navigation": {
        "navigate": {
            "action": "com.byd.navigation.ACTION_NAVIGATE",
            "extras": {
                "destination": "String",
                "destination_type": "String (home, work, favorite, custom)",
                "latitude": "double",
                "longitude": "double",
                "language": "String"
            }
        },
        "cancel_navigation": {
            "action": "com.byd.navigation.ACTION_CANCEL"
        }
    },
    "climate": {
        "power_on": {
            "action": "com.byd.climate.ACTION_CONTROL",
            "extras": {
                "power": "on"
            }
        },
        "power_off": {
            "action": "com.byd.climate.ACTION_CONTROL",
            "extras": {
                "power": "off"
            }
        },
        "set_temperature": {
            "action": "com.byd.climate.ACTION_CONTROL",
            "extras": {
                "temperature": "int (16-30)",
                "zone": "String (driver, passenger, all)"
            }
        },
        "set_fan_speed": {
            "action": "com.byd.climate.ACTION_CONTROL",
            "extras": {
                "fan_speed": "int (1-7)",
                "mode": "String (auto, manual)"
            }
        },
        "set_mode": {
            "action": "com.byd.climate.ACTION_CONTROL",
            "extras": {
                "mode": "String (cool, heat, auto, fan)"
            }
        }
    },
    "media": {
        "play": {
            "action": "com.byd.media.ACTION_CONTROL",
            "extras": {
                "action": "play"
            }
        },
        "pause": {
            "action": "com.byd.media.ACTION_CONTROL",
            "extras": {
                "action": "pause"
            }
        },
        "next": {
            "action": "com.byd.media.ACTION_CONTROL",
            "extras": {
                "action": "next"
            }
        },
        "previous": {
            "action": "com.byd.media.ACTION_CONTROL",
            "extras": {
                "action": "previous"
            }
        },
        "set_volume": {
            "action": "com.byd.media.ACTION_CONTROL",
            "extras": {
                "volume": "int (0-100)"
            }
        }
    },
    "voice": {
        "activate": {
            "action": "com.byd.voice.ACTION_ACTIVATE"
        },
        "deactivate": {
            "action": "com.byd.voice.ACTION_DEACTIVATE"
        }
    }
}

# Intent output file
INTENT_MAP_FILE = Path(__file__).parent.parent / "app" / "src" / "main" / "assets" / "commands" / "dilink_intent_map.json"


class ADBClient:
    """Simple ADB client for DiLink testing"""
    
    def __init__(self, device_address: str = None):
        self.device = device_address
        self.connected = False
        
    def connect(self) -> bool:
        """Connect to device via wireless ADB"""
        if not self.device:
            print("No device specified")
            return False
            
        result = subprocess.run(
            ["adb", "connect", self.device],
            capture_output=True,
            text=True
        )
        
        if "connected" in result.stdout.lower():
            self.connected = True
            print(f"Connected to {self.device}")
            return True
        else:
            print(f"Failed to connect: {result.stderr}")
            return False
    
    def disconnect(self):
        """Disconnect from device"""
        if self.device:
            subprocess.run(["adb", "disconnect", self.device])
            self.connected = False
            
    def shell(self, command: str) -> str:
        """Execute shell command on device"""
        if self.device:
            cmd = ["adb", "-s", self.device, "shell", command]
        else:
            cmd = ["adb", "shell", command]
            
        result = subprocess.run(cmd, capture_output=True, text=True)
        return result.stdout
    
    def logcat(self, filter_spec: str = ""):
        """Stream logcat output"""
        cmd = ["adb"]
        if self.device:
            cmd.extend(["-s", self.device])
        cmd.extend(["logcat", "-s", filter_spec] if filter_spec else ["logcat"])
        
        process = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True
        )
        
        return process


def discover_intents(adb: ADBClient, duration: int = 60):
    """
    Monitor logcat for intent launches.
    Run this while manually interacting with DiLink.
    """
    print(f"\n🔍 Discovering intents for {duration} seconds...")
    print("Interact with DiLink now. Press Ctrl+C to stop.\n")
    
    discovered = []
    
    try:
        # Monitor relevant log tags
        logcat = adb.logcat("ActivityManager:I PackageManager:I IntentBot:I *:S")
        
        intent_pattern = re.compile(
            r'(Intent\s*\{.*?\}|START.*?Intent|Broadcasting.*?Intent)',
            re.IGNORECASE
        )
        
        start_time = time.time()
        
        for line in logcat.stdout:
            if time.time() - start_time > duration:
                break
                
            match = intent_pattern.search(line)
            if match:
                intent_info = parse_intent_line(line)
                if intent_info:
                    discovered.append(intent_info)
                    print(f"  Found: {intent_info}")
                    
    except KeyboardInterrupt:
        print("\nDiscovery interrupted")
    finally:
        logcat.terminate()
    
    # Save discovered intents
    if discovered:
        save_discovered_intents(discovered)
        print(f"\n✅ Discovered {len(discovered)} intents")
    else:
        print("\n❌ No intents discovered")
    
    return discovered


def parse_intent_line(line: str) -> dict:
    """Parse intent information from logcat line"""
    result = {}
    
    # Extract action
    action_match = re.search(r'action=([^\s\}]+)', line)
    if action_match:
        result['action'] = action_match.group(1)
    
    # Extract package/component
    pkg_match = re.search(r'pkg=([^\s\}]+)', line)
    if pkg_match:
        result['package'] = pkg_match.group(1)
    
    cmp_match = re.search(r'cmp=([^\s\}]+)', line)
    if cmp_match:
        result['component'] = cmp_match.group(1)
    
    # Extract extras (simplified)
    extras_match = re.search(r'\(has extras\)', line)
    if extras_match:
        result['has_extras'] = True
    
    return result if result else None


def test_command(adb: ADBClient, command_name: str):
    """Test a specific DiLink command"""
    # Parse command (e.g., "navigate_home" -> navigation.navigate)
    parts = command_name.split('_')
    if len(parts) < 2:
        print(f"Invalid command format: {command_name}")
        print("Use format: category_action (e.g., navigation_navigate)")
        return
    
    category = parts[0]
    action = '_'.join(parts[1:])
    
    if category not in DILINK_INTENTS:
        print(f"Unknown category: {category}")
        print(f"Available: {list(DILINK_INTENTS.keys())}")
        return
    
    if action not in DILINK_INTENTS[category]:
        print(f"Unknown action: {action}")
        print(f"Available: {list(DILINK_INTENTS[category].keys())}")
        return
    
    intent = DILINK_INTENTS[category][action]
    
    print(f"\n🧪 Testing command: {command_name}")
    print(f"   Action: {intent['action']}")
    
    # Build adb command
    adb_cmd = f"am broadcast -a {intent['action']}"
    
    if 'extras' in intent:
        print(f"   Extras: {intent['extras']}")
        # Add sample extras
        for key, value_type in intent['extras'].items():
            if 'String' in value_type:
                adb_cmd += f" --es {key} 'test'"
            elif 'int' in value_type.lower():
                adb_cmd += f" --ei {key} 22"
            elif 'double' in value_type.lower():
                adb_cmd += f" --ed {key} 30.0"
    
    print(f"\n   Executing: adb shell {adb_cmd}")
    result = adb.shell(adb_cmd)
    print(f"   Result: {result}")


def save_discovered_intents(intents: list):
    """Save discovered intents to JSON file"""
    INTENT_MAP_FILE.parent.mkdir(parents=True, exist_ok=True)
    
    with open(INTENT_MAP_FILE, 'w', encoding='utf-8') as f:
        json.dump(intents, f, indent=2, ensure_ascii=False)
    
    print(f"Saved to: {INTENT_MAP_FILE}")


def list_known_intents():
    """List all known DiLink intents"""
    print("\n📋 Known DiLink Intents:")
    print("=" * 60)
    
    for category, actions in DILINK_INTENTS.items():
        print(f"\n{category.upper()}:")
        for action_name, intent in actions.items():
            print(f"  • {action_name}: {intent['action']}")


def generate_intent_map():
    """Generate intent_map.json from discovered intents"""
    output = {
        "version": "1.0",
        "intents": DILINK_INTENTS
    }
    
    INTENT_MAP_FILE.parent.mkdir(parents=True, exist_ok=True)
    
    with open(INTENT_MAP_FILE, 'w', encoding='utf-8') as f:
        json.dump(output, f, indent=2, ensure_ascii=False)
    
    print(f"Generated intent map: {INTENT_MAP_FILE}")


def main():
    parser = argparse.ArgumentParser(description='DiLink Intent Testing Tool')
    parser.add_argument('--device', help='Device IP:port for wireless ADB')
    parser.add_argument('--discover', action='store_true', help='Discover intents via logcat')
    parser.add_argument('--duration', type=int, default=60, help='Discovery duration in seconds')
    parser.add_argument('--test-command', help='Test a specific command (e.g., navigation_navigate)')
    parser.add_argument('--list', action='store_true', help='List known intents')
    parser.add_argument('--generate', action='store_true', help='Generate intent_map.json')
    
    args = parser.parse_args()
    
    if args.list:
        list_known_intents()
        return
    
    if args.generate:
        generate_intent_map()
        return
    
    adb = ADBClient(args.device)
    
    if args.device:
        if not adb.connect():
            sys.exit(1)
    
    try:
        if args.discover:
            discover_intents(adb, args.duration)
        elif args.test_command:
            test_command(adb, args.test_command)
        else:
            parser.print_help()
    finally:
        if args.device:
            adb.disconnect()


if __name__ == '__main__':
    main()
