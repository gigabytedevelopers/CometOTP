#!/usr/bin/env python3
"""Turns git-cliff's release notes into plain text that fits Play's "What's new" box.

Play shows the text as it is, so the Markdown headings and list marks git-cliff writes would
appear literally, and it rejects anything over 500 characters. This keeps the sections, writes
them as plain text, and stops at the last whole entry that fits, saying how many were left out,
rather than cutting a line in half.

Only used when there is no hand-written file for the version; see "Play release notes" in
.github/RELEASING.md.

Usage: play_notes.py RELEASE_NOTES.md OUTPUT [LIMIT]
"""
import io
import sys

# Most useful to someone reading the store listing first. Improvement goes last because it is
# mostly internal work (refactors, build changes).
ORDER = ['Breaking', 'New', 'Fix', 'Improvement']
LABELS = {'Breaking': 'Important', 'New': 'New', 'Fix': 'Fixed', 'Improvement': 'Improved'}


def parse(text):
    """Returns {group: [entry, ...]} from git-cliff's "### Group" / "- entry" output."""
    groups = {}
    current = None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith('### '):
            current = line[4:].strip()
            groups.setdefault(current, [])
        elif line.startswith('- ') and current is not None:
            groups[current].append(line[2:].strip())
    return groups


def trailer(omitted):
    return '\n\nPlus %d more change%s.' % (omitted, '' if omitted == 1 else 's')


def render(groups, limit):
    ordered = [g for g in ORDER if g in groups] + sorted(g for g in groups if g not in ORDER)
    total = sum(len(groups[g]) for g in ordered)
    # Room for the longest trailer this could need, so adding it never pushes past the limit.
    reserve = len(trailer(total)) if total else 0

    blocks = []
    used = 0
    kept = 0
    for group in ordered:
        heading = LABELS.get(group, group) + ':'
        lines = []
        for entry in groups[group]:
            line = '• ' + entry
            # The heading, and the blank line before a new section, only cost anything once the
            # section has its first entry.
            extra = len(line) + 1
            if not lines:
                extra += len(heading) + (2 if blocks else 0)
            if used + extra + reserve > limit:
                continue
            lines.append(line)
            used += extra
            kept += 1
        if lines:
            blocks.append('\n'.join([heading] + lines))

    text = '\n\n'.join(blocks)
    if kept < total:
        text += trailer(total - kept)
    return text.strip(), kept, total


def main(notes_path, out_path, limit=500):
    groups = parse(io.open(notes_path, encoding='utf-8').read())
    text, kept, total = render(groups, limit)
    if not text:
        return 1
    if len(text) > limit:
        print('Generated %d characters, over the limit of %d.' % (len(text), limit))
        return 1
    io.open(out_path, 'w', encoding='utf-8', newline='\n').write(text + '\n')
    print('%d of %d entries fit in %d characters.' % (kept, total, len(text)))
    return 0


if __name__ == '__main__':
    if len(sys.argv) not in (3, 4):
        print(__doc__)
        sys.exit(2)
    sys.exit(main(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) == 4 else 500))
