import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from tools.legacy.sync_registry_values import convert
from verify_config import MAX_CENTS, load_values


ROOT = Path(__file__).resolve().parents[1]


class DataHelperTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.directory = Path(self.temporary.name)

    def config(self, values):
        path = self.directory.joinpath("values.json")
        path.write_text(json.dumps(values), encoding="utf-8")
        return path

    def current(self):
        return {"prices": {"minecraft:stone": 25}, "currencyItems": {"statecraft_economy:currency_1": 100}}

    def test_current_cli_uses_explicit_paths_from_another_directory(self):
        config = self.config(self.current())
        original = config.read_bytes()
        result = subprocess.run([sys.executable, str(ROOT.joinpath("verify_config.py")),
                                 str(config), "--item", "minecraft:stone"],
                                cwd=self.directory, capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("25 cents", result.stdout)
        self.assertEqual(original, config.read_bytes())

    def test_current_schema_rejects_invalid_prices_ids_and_units(self):
        for value in [0, -1, True, 1.25, "25", MAX_CENTS + 1]:
            with self.subTest(value=value):
                config = self.current()
                config["prices"]["minecraft:stone"] = value
                with self.assertRaises(ValueError):
                    load_values(self.config(config))
        for item in ["stone", "minecraft:air", "Minecraft:stone"]:
            with self.subTest(item=item):
                config = self.current()
                config["prices"] = {item: 10}
                with self.assertRaises(ValueError):
                    load_values(self.config(config))

    def test_current_schema_rejects_duplicates_overlap_and_legacy_shapes(self):
        config = self.config(self.current())
        config.write_text('{"prices":{"minecraft:stone":25,"minecraft:stone":30},"currencyItems":{}}',
                          encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            load_values(config)
        for values in [
            {"wood": {"minecraft:stone": 1}},
            {"prices": [], "currencyItems": {}},
            {"prices": {}, "currencyItems": {}},
            {"prices": {"minecraft:stone": 10}, "currencyItems": {"minecraft:stone": 100}},
        ]:
            with self.subTest(values=values), self.assertRaises(ValueError):
                load_values(self.config(values))

    def test_current_cli_reports_errors_with_failure_exit(self):
        config = self.config(self.current())
        result = subprocess.run([sys.executable, str(ROOT.joinpath("verify_config.py")),
                                 str(config), "--item", "minecraft:missing"],
                                capture_output=True, text=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("No value is configured", result.stderr)

    def test_bundled_current_values_have_the_supported_structure(self):
        values = load_values(ROOT.joinpath("statecraft-economy", "src", "main", "resources",
                                          "data", "statecraft_economy", "default_item_values.json"))
        self.assertEqual(100, values["currencyItems"]["statecraft_economy:currency_1"])

    def test_legacy_conversion_preserves_unknown_and_computed_entries(self):
        content = '{ item: "example:part/one", value: 2 }, { item: "minecraft:stone", value: 3 }, value: source * 2'
        result, matched, changed = convert({"old": {"example:part/one": 7}}, content)
        self.assertEqual(1, matched)
        self.assertEqual(1, changed)
        self.assertEqual(content.replace("value: 2", "value: 7"), result)

    def test_legacy_conversion_rejects_current_cents_and_implicit_rounding(self):
        for values in [self.current(), {"old": {"minecraft:stone": 1.5}},
                       {"old": {"minecraft:stone": True}}]:
            with self.subTest(values=values), self.assertRaises(ValueError):
                convert(values, '{ item: "minecraft:stone", value: 2 }')
        with self.assertRaisesRegex(ValueError, "No supported"):
            convert({"old": {"minecraft:stone": 3}}, "value: computed()")

    def test_legacy_cli_previews_and_never_overwrites_existing_files(self):
        config = self.config({"old": {"minecraft:stone": 3}})
        registry = self.directory.joinpath("registry.js")
        original = '{ item: "minecraft:stone", value: 2 }'
        registry.write_text(original, encoding="utf-8")
        command = [sys.executable, str(ROOT.joinpath("tools", "legacy", "sync_registry_values.py")),
                   str(config), str(registry)]
        preview = subprocess.run(command, cwd=self.directory, capture_output=True, text=True)
        self.assertEqual(0, preview.returncode, preview.stderr)
        self.assertIn("no files written", preview.stdout)
        self.assertEqual(original, registry.read_text(encoding="utf-8"))
        protected = subprocess.run(command + ["--output", str(registry)], capture_output=True, text=True)
        self.assertNotEqual(0, protected.returncode)
        self.assertEqual(original, registry.read_text(encoding="utf-8"))
        output = self.directory.joinpath("converted.js")
        written = subprocess.run(command + ["--output", str(output)], capture_output=True, text=True)
        self.assertEqual(0, written.returncode, written.stderr)
        self.assertEqual(original.replace("value: 2", "value: 3"), output.read_text(encoding="utf-8"))
        self.assertEqual(original, registry.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
