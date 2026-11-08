"""Check that download_gradle.sh tries again when the Gradle download fails, and gives up.

Run with: python3 -m unittest discover -s .github/scripts/tests -v
"""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'download_gradle.sh'


class DownloadGradleTest(unittest.TestCase):
    def run_with_failures(self, failures, attempts):
        """Runs the script against a stand-in ./gradlew that fails its first `failures` runs."""
        with tempfile.TemporaryDirectory() as root:
            calls = Path(root, 'calls')
            gradlew = Path(root, 'gradlew')
            gradlew.write_text(
                '#!/usr/bin/env bash\n'
                f'echo run >> "{calls}"\n'
                f'[ "$(wc -l < "{calls}")" -gt {failures} ]\n')
            gradlew.chmod(0o755)
            result = subprocess.run(['bash', str(SCRIPT), str(attempts), '0'], cwd=root,
                                    capture_output=True, text=True)
            runs = len(calls.read_text().splitlines()) if calls.exists() else 0
            return result, runs

    def test_a_download_that_works_runs_gradle_once(self):
        result, runs = self.run_with_failures(0, 5)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(runs, 1)

    def test_a_failed_download_is_tried_again_until_it_works(self):
        result, runs = self.run_with_failures(2, 5)
        self.assertEqual(result.returncode, 0)
        self.assertEqual(runs, 3)
        self.assertIn('attempt 2 of 5', result.stdout)

    def test_it_gives_up_after_the_last_attempt(self):
        result, runs = self.run_with_failures(9, 3)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(runs, 3)
        self.assertIn('after 3 attempts', result.stdout)
        # No pointless wait after the last attempt.
        self.assertNotIn('attempt 3 of 3', result.stdout)


if __name__ == '__main__':
    unittest.main()
