#!/usr/bin/env python3
"""Adds a release to the changelog page on gigabytedevelopers.com.

The page (apps/cometotp/changelog/ on the website) is LogLive: it reads log/CHANGELOG.md next to
it, which starts with a "---" front matter block and "# CometOTP", then one section per release:

    ## 8.1.1 (2026-09-27T19:00:00)
    ### Improvement
    - ...

The file on the server is the only copy with every release in it, and older entries there are
edited by hand, so this never rebuilds it: it takes the file as it is on the server and puts the
new release above the newest one, leaving everything else byte for byte, including its CRLF line
endings.

The entries come from the hand-written Play notes for the version when there are some, because
those are written for people using the app, as the rest of that page is. Each "Label: text"
paragraph becomes a section; the unlabelled first paragraph ("CometOTP 8.1.1 polishes search and
dialogs.") is a summary and is left out. Without Play notes, git-cliff's "### Group" / "- entry"
release notes are used as they are.

Exits 0 having written OUTPUT, 3 if the file already has this version (OUTPUT is not written),
and 1 if the file on the server does not look like the changelog or there is nothing to add.

Usage: website_changelog.py CURRENT.md NOTES VERSION DATE OUTPUT
       NOTES is a Play notes .txt or git-cliff .md; DATE is YYYY-MM-DDTHH:MM:SS.
"""
import io
import re
import sys

# The order the page has always used.
ORDER = ['Breaking', 'New', 'Improvement', 'Security', 'Fix']

# The labels the Play notes use, as the section each belongs in.
PLAY_LABELS = {
    'important': 'Breaking',
    'breaking': 'Breaking',
    'new': 'New',
    'improved': 'Improvement',
    'improvement': 'Improvement',
    'security': 'Security',
    'fixed': 'Fix',
    'fix': 'Fix',
}


def upper_first(text):
    return text[:1].upper() + text[1:]


def parse_play_notes(text):
    """Returns {section: [entry, ...]} from Play notes written as "Label: text" paragraphs."""
    sections = {}
    for paragraph in re.split(r'\n\s*\n', text.strip()):
        paragraph = ' '.join(line.strip() for line in paragraph.splitlines()).strip()
        match = re.match(r'([A-Za-z]+):\s*(.+)', paragraph)
        if not match or match.group(1).lower() not in PLAY_LABELS:
            continue
        section = PLAY_LABELS[match.group(1).lower()]
        sections.setdefault(section, []).extend(split_list(match.group(2).strip()))
    return sections


def split_list(text):
    """Turns "a Tags screen; Support & FAQs; and a choice of app icon." into one entry each."""
    items = [item.strip() for item in text.split(';') if item.strip()]
    if len(items) == 1:
        return [upper_first(text)]
    items = [re.sub(r'^and\s+', '', item).rstrip('.') for item in items]
    return [upper_first(item) for item in items]


def parse_cliff_notes(text):
    """Returns {section: [entry, ...]} from git-cliff's "### Group" / "- entry" output."""
    sections = {}
    current = None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith('### '):
            current = line[4:].strip()
        elif line.startswith('- ') and current is not None:
            sections.setdefault(current, []).append(line[2:].strip())
    return sections


def parse_notes(path):
    text = io.open(path, encoding='utf-8').read()
    return parse_cliff_notes(text) if path.endswith('.md') else parse_play_notes(text)


def render(version, date, sections):
    lines = ['## %s (%s)' % (version, date)]
    for section in [s for s in ORDER if s in sections] + sorted(s for s in sections if s not in ORDER):
        lines.append('### ' + section)
        lines.extend('- ' + entry for entry in sections[section])
        lines.append('')
    return lines


def insert(current, version, date, sections):
    """Returns the new file, or None when it already has this version."""
    newline = '\r\n' if '\r\n' in current else '\n'
    lines = current.split(newline)
    if not lines or lines[0].strip() != '---' or not any(l.startswith('# ') for l in lines):
        raise ValueError('it does not start with the "---" front matter and a "# " title')
    if any(re.match(r'##\s+%s\s*\(' % re.escape(version), l) for l in lines):
        return None
    # Above the newest release; at the end when there are none yet.
    at = next((i for i, l in enumerate(lines) if l.startswith('## ')), None)
    if at is None:
        body = lines[:]
        while body and body[-1] == '':
            body.pop()
        return newline.join(body + [''] + render(version, date, sections)) + newline
    return newline.join(lines[:at] + render(version, date, sections) + lines[at:])


def main(current_path, notes_path, version, date, out_path):
    # newline='' keeps the file's own line endings rather than turning CRLF into LF.
    current = io.open(current_path, encoding='utf-8', newline='').read()
    sections = parse_notes(notes_path)
    if not any(sections.values()):
        print('%s has no entries to add.' % notes_path)
        return 1
    try:
        updated = insert(current, version, date, sections)
    except ValueError as error:
        print('%s does not look like the changelog: %s.' % (current_path, error))
        return 1
    if updated is None:
        print('The changelog already has %s; leaving it alone.' % version)
        return 3
    io.open(out_path, 'w', encoding='utf-8', newline='').write(updated)
    print('Added %s with %d entries.' % (version, sum(len(e) for e in sections.values())))
    return 0


if __name__ == '__main__':
    if len(sys.argv) != 6:
        print(__doc__)
        sys.exit(2)
    sys.exit(main(*sys.argv[1:]))
