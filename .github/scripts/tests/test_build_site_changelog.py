"""Check that build_site_changelog.py makes the file the website's changelog page expects.

Run with: python3 -m unittest discover -s .github/scripts/tests -v
"""
from pathlib import Path
import importlib.util
import unittest

SCRIPTS = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('build_site_changelog', SCRIPTS / 'build_site_changelog.py')
site = importlib.util.module_from_spec(spec)
spec.loader.exec_module(site)

CHANGELOG = '''# Changelog

All notable changes to CometOTP are recorded here. See `.github/RELEASING.md`.

## 9.2.2 (2026-11-12)

### New

- Add icons for 14 more services

## 8.0.0 (2026-09-22)

### Fix
- Fixed a crash
'''

HISTORY = '''## 7.0.0 (2026-09-05T18:00:00)
### New
- Added an Otter.ai account thumbnail

## 1.0.0 (2018-01-08)
### Breaking
- Initial release of **CometOTP**
'''


class BuildSiteChangelogTest(unittest.TestCase):
    def test_the_page_gets_its_title_and_link_then_every_release_newest_first(self):
        text = site.build(CHANGELOG, HISTORY)
        self.assertTrue(text.startswith('---\nwebsite: http://bit.ly/CometOTP_changelog\n---\n\n# CometOTP\n\n'))
        self.assertEqual(site.RELEASE.findall(text), ['9.2.2', '8.0.0', '7.0.0', '1.0.0'])
        self.assertTrue(text.endswith('- Initial release of **CometOTP**\n'))

    def test_the_repository_title_and_notes_for_developers_are_left_out(self):
        text = site.build(CHANGELOG, HISTORY)
        self.assertNotIn('# Changelog', text)
        self.assertNotIn('RELEASING.md', text)
        self.assertEqual(text.count('\n# '), 1)

    def test_a_release_in_both_files_is_refused(self):
        with self.assertRaises(ValueError):
            site.build(CHANGELOG, HISTORY.replace('7.0.0', '8.0.0'))

    def test_the_real_files_build(self):
        root = SCRIPTS.parents[1]
        text = site.build((root / 'CHANGELOG.md').read_text(encoding='utf-8'),
                          (root / '.github/website/changelog-before-8.0.0.md').read_text(encoding='utf-8'))
        versions = site.RELEASE.findall(text)
        self.assertEqual(versions[-1], '1.0.0')
        self.assertIn('8.0.0', versions)
        self.assertIn('7.0.0', versions)
        self.assertEqual(len(versions), len(set(versions)))


if __name__ == '__main__':
    unittest.main()
