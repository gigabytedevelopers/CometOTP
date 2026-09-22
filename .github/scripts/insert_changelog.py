#!/usr/bin/env python3
"""Inserts a freshly generated section into CHANGELOG.md, just under the file's heading.

git-cliff can prepend, but only to the very top, which would push the new release above the
"# Changelog" heading and its explanatory paragraph. This puts it where it belongs: after the
heading and before the newest existing release.

Usage: insert_changelog.py CHANGELOG.md NEW_SECTION.md
"""
import io
import sys


def main(changelog_path, section_path):
    section = io.open(section_path, encoding='utf-8').read().strip()
    if not section:
        print('No new section to add; leaving %s alone.' % changelog_path)
        return 0

    lines = io.open(changelog_path, encoding='utf-8').read().splitlines()

    # The first release heading, which the new one goes above.
    insert_at = next((i for i, line in enumerate(lines) if line.startswith('## ')), len(lines))

    if section in '\n'.join(lines):
        print('That section is already present; leaving %s alone.' % changelog_path)
        return 0

    updated = lines[:insert_at] + section.splitlines() + [''] + lines[insert_at:]
    io.open(changelog_path, 'w', encoding='utf-8', newline='\n').write('\n'.join(updated) + '\n')
    print('Added %d lines to %s at line %d.' % (len(section.splitlines()), changelog_path, insert_at + 1))
    return 0


if __name__ == '__main__':
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2]))
