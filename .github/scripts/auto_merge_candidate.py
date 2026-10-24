#!/usr/bin/env python3
"""Pick the pull request Auto merge may fast-forward master to, from `gh pr list` JSON.

Prints GitHub Actions outputs: `number` and `sha` of the oldest ready pull request (empty when
none is), and `retry=true` when one would be ready but GitHub has not finished working out its
merge state yet. Reads nothing but the file it is given and never touches the repository.
"""
import json
import sys

# A hold label keeps a ready pull request out of the queue until it is removed.
HOLD_LABEL = 'hold'


def ready(pr, authors):
    """Whether this pull request is one Auto merge may push, apart from its merge state."""
    return (pr['author']['login'] in authors
            and not pr['isDraft']
            and not pr['isCrossRepository']
            and pr['baseRefName'] == 'master'
            and pr['reviewDecision'] == 'APPROVED'
            and HOLD_LABEL not in {label['name'] for label in pr['labels']})


def pick(prs, authors):
    """Return (number, sha, retry) for the oldest pull request GitHub calls clean."""
    retry = False
    for pr in sorted(prs, key=lambda pr: pr['number']):
        if not ready(pr, authors):
            continue
        # CLEAN: approved on its latest push, every required check green, up to date with master.
        # BEHIND, BLOCKED, UNSTABLE and the rest wait. UNKNOWN is GitHub still computing it.
        if pr['mergeStateStatus'] == 'CLEAN':
            return pr['number'], pr['headRefOid'], False
        if pr['mergeStateStatus'] == 'UNKNOWN':
            retry = True
    return '', '', retry


def main(path, authors):
    with open(path, encoding='utf-8') as f:
        number, sha, retry = pick(json.load(f), set(authors.split()))
    print(f'number={number}')
    print(f'sha={sha}')
    print(f'retry={"true" if retry else "false"}')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit('usage: auto_merge_candidate.py <gh pr list JSON> "<author> ..."')
    main(sys.argv[1], sys.argv[2])
