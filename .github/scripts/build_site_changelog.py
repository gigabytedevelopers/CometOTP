#!/usr/bin/env python3
"""Builds the file the CometOTP changelog page on gigabytedevelopers.com reads, log/CHANGELOG.md.

The page shows the file's first-level heading as its title ("CometOTP changelog"), links the
front matter's website, and draws each "## <version> (<date>)" with its sections. So the file is:
that front matter and heading, then every release in CHANGELOG.md (whose own title and notes for
developers are left out), then the releases before 8.0.0, which CHANGELOG.md does not have and
.github/website/changelog-before-8.0.0.md keeps.

Usage: build_site_changelog.py CHANGELOG.md HISTORY.md OUTPUT
"""
import re
import sys

HEADER = '---\nwebsite: http://bit.ly/CometOTP_changelog\n---\n\n# CometOTP\n\n'
RELEASE = re.compile(r'^## (\S+)', re.M)


def releases(text):
    """Everything from the first release heading on, and the versions it lists."""
    first = RELEASE.search(text)
    if not first:
        raise ValueError('no "## <version>" release heading found')
    body = text[first.start():].rstrip() + '\n'
    return body, RELEASE.findall(body)


def build(changelog, history):
    recent, recent_versions = releases(changelog)
    older, older_versions = releases(history)
    both = set(recent_versions) & set(older_versions)
    if both:
        raise ValueError('releases in both files: ' + ', '.join(sorted(both)))
    return HEADER + recent + '\n' + older


def main(changelog_path, history_path, output_path):
    with open(changelog_path, encoding='utf-8') as f:
        changelog = f.read()
    with open(history_path, encoding='utf-8') as f:
        history = f.read()
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(build(changelog, history))


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__.strip().splitlines()[-1])
    try:
        main(*sys.argv[1:])
    except ValueError as e:
        sys.exit(f'build_site_changelog.py: {e}')
