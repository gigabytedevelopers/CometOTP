"""Check which pull request Auto merge picks, from `gh pr list` fixtures.

Run with: python3 -m unittest discover -s .github/scripts/tests -v
"""
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from auto_merge_candidate import pick  # noqa: E402

AUTHORS = {'enwokoma', 'gigabytedevelopersinc'}


def pr(number, **changes):
    fields = {
        'number': number,
        'author': {'login': 'enwokoma'},
        'isDraft': False,
        'isCrossRepository': False,
        'baseRefName': 'master',
        'headRefOid': f'{number:040x}',
        'reviewDecision': 'APPROVED',
        'mergeStateStatus': 'CLEAN',
        'labels': [],
    }
    fields.update(changes)
    return fields


class AutoMergeCandidateTest(unittest.TestCase):
    def test_ready_pull_request_is_picked(self):
        self.assertEqual(pick([pr(7)], AUTHORS), (7, f'{7:040x}', False))

    def test_oldest_ready_pull_request_goes_first(self):
        number, _, _ = pick([pr(12), pr(9), pr(10)], AUTHORS)
        self.assertEqual(number, 9)

    def test_nothing_ready_picks_nothing(self):
        self.assertEqual(pick([], AUTHORS), ('', '', False))

    def test_only_owners_pull_requests_are_merged(self):
        outsider = pr(5, author={'login': 'Jumzeey'})
        self.assertEqual(pick([outsider], AUTHORS), ('', '', False))

    def test_forks_are_never_merged(self):
        self.assertEqual(pick([pr(5, isCrossRepository=True)], AUTHORS)[0], '')

    def test_drafts_wait(self):
        self.assertEqual(pick([pr(5, isDraft=True)], AUTHORS)[0], '')

    def test_other_bases_wait(self):
        self.assertEqual(pick([pr(5, baseRefName='feat/stacked')], AUTHORS)[0], '')

    def test_unapproved_pull_requests_wait(self):
        for decision in ('REVIEW_REQUIRED', 'CHANGES_REQUESTED', None):
            with self.subTest(decision=decision):
                self.assertEqual(pick([pr(5, reviewDecision=decision)], AUTHORS)[0], '')

    def test_hold_label_keeps_it_out(self):
        held = pr(5, labels=[{'name': 'hold'}])
        self.assertEqual(pick([held, pr(6)], AUTHORS)[0], 6)

    def test_only_clean_merge_state_is_merged(self):
        for state in ('BEHIND', 'BLOCKED', 'DIRTY', 'UNSTABLE', 'HAS_HOOKS', 'DRAFT'):
            with self.subTest(state=state):
                self.assertEqual(pick([pr(5, mergeStateStatus=state)], AUTHORS), ('', '', False))

    def test_unknown_merge_state_asks_for_a_retry(self):
        self.assertEqual(pick([pr(5, mergeStateStatus='UNKNOWN')], AUTHORS), ('', '', True))

    def test_clean_pull_request_wins_over_a_retry(self):
        prs = [pr(5, mergeStateStatus='UNKNOWN'), pr(6)]
        self.assertEqual(pick(prs, AUTHORS), (6, f'{6:040x}', False))


if __name__ == '__main__':
    unittest.main()
