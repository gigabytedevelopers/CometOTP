"""Check that every call to one of this repository's workflows passes the caller's secrets.

A called workflow sees only the secrets its caller passes, even those of the environment its own
job names; any other resolves to an empty string, with no error. v9.2.1's Android release stopped
that way at its token check, after Auto release had already pushed the tag.

Run with: python3 -m unittest discover -s .github/scripts/tests -v
"""
from pathlib import Path
import re
import unittest

WORKFLOWS = Path(__file__).resolve().parents[2] / 'workflows'


def jobs(text):
    """Each job under `jobs:` as (name, lines), read by indentation rather than with a YAML library."""
    found, name, lines, inside = [], None, [], False
    for line in text.splitlines():
        if re.match(r'^jobs:\s*$', line):
            inside = True
            continue
        if not inside:
            continue
        if re.match(r'^\S', line):
            break
        start = re.match(r'^  ([A-Za-z0-9_-]+):\s*$', line)
        if start:
            if name:
                found.append((name, lines))
            name, lines = start.group(1), []
        elif name:
            lines.append(line)
    if name:
        found.append((name, lines))
    return found


class WorkflowCallsTest(unittest.TestCase):
    def test_local_workflow_calls_pass_their_secrets(self):
        calls = 0
        for path in sorted(WORKFLOWS.glob('*.yml')):
            for name, lines in jobs(path.read_text()):
                if not any(re.match(r'^    uses: \./\.github/workflows/', line) for line in lines):
                    continue
                calls += 1
                with self.subTest(workflow=path.name, job=name):
                    self.assertIn('    secrets: inherit', lines,
                                  f'{path.name} calls a workflow from job {name} without passing secrets')
        # Auto release calls Release Android and Release iOS.
        self.assertGreaterEqual(calls, 2)


if __name__ == '__main__':
    unittest.main()
