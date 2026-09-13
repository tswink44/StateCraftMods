"""Generate the original empty structure used by StateCraft's Forge smoke tests."""
import gzip
import struct
from pathlib import Path


def text(value):
    encoded = value.encode("utf-8")
    return struct.pack(">H", len(encoded)) + encoded


def named(kind, name, payload):
    return bytes([kind]) + text(name) + payload


root = Path(__file__).resolve().parents[1]
target = root / "statecraft" / "src" / "main" / "resources" / "data" / "statecraft" / "structures" / "test_empty.nbt"
palette = named(8, "Name", text("minecraft:air")) + b"\0"
payload = (
    named(9, "size", b"\3" + struct.pack(">iiii", 3, 5, 5, 5))
    + named(9, "palette", b"\12" + struct.pack(">i", 1) + palette)
    + named(9, "blocks", b"\12" + struct.pack(">i", 0))
    + named(9, "entities", b"\12" + struct.pack(">i", 0))
    + named(3, "DataVersion", struct.pack(">i", 3465))
    + b"\0"
)
target.parent.mkdir(parents=True, exist_ok=True)
target.write_bytes(gzip.compress(named(10, "", payload), mtime=0))
print(f"Generated {target.name} ({target.stat().st_size} bytes)")
