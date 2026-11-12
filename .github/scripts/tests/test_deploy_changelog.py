"""Check that deploy_changelog.sh uploads the changelog safely and confirms the site serves it.

A stand-in curl records each call and plays the website: an upload of CHANGELOG.md becomes what
the site serves (unless the test makes the site stale), and a download returns it.

Run with: python3 -m unittest discover -s .github/scripts/tests -v
"""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / 'deploy_changelog.sh'
HTACCESS = Path(__file__).resolve().parents[2] / 'website' / 'changelog-log.htaccess'

FAKE_CURL = r'''#!/usr/bin/env bash
printf '%s\n' "$*" >> "$CALLS"
config=""; out=""; headers=""; upload=""; url=""
while [ $# -gt 0 ]; do
  case "$1" in
    --config) config=$2; shift ;;
    -o) out=$2; shift ;;
    -D) headers=$2; shift ;;
    -T) upload=$2; shift ;;
    ftp://*|https://*) url=$1 ;;
  esac
  shift
done
[ -n "$config" ] && cat "$config" >> "$CONFIGS"
if [ -n "$upload" ] && [ "${url##*/}" = CHANGELOG.md ]; then
  cp "$upload" "$UPLOADED"
  [ -z "$STALE" ] && cp "$upload" "$SERVED"
fi
if [ -n "$out" ]; then
  printf 'HTTP/2 200\r\ncache-control: %s\r\n' "${SERVED_CACHE_CONTROL:-no-cache}" > "$headers"
  cp "$SERVED" "$out"
fi
exit 0
'''


class DeployChangelogTest(unittest.TestCase):
    def run_script(self, user='cometotp-log', password='p@ss "w\\rd"', stale=False, cache='no-cache'):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            changelog = root / 'CHANGELOG.md'
            changelog.write_text('# Changelog\n\nNotes.\n\n## 9.2.2 (2026-11-12)\n### New\n- Icons\n')
            history = root / 'history.md'
            history.write_text('## 1.0.0 (2018-01-08)\n### Breaking\n- Initial release\n')
            (root / 'bin').mkdir()
            curl = root / 'bin' / 'curl'
            curl.write_text(FAKE_CURL)
            curl.chmod(0o755)
            site = root / 'served.md'
            site.write_text('# An old changelog\n')
            env = dict(os.environ,
                       PATH=f'{root / "bin"}:{os.environ["PATH"]}',
                       CALLS=str(root / 'calls'), CONFIGS=str(root / 'configs'), SERVED=str(site),
                       UPLOADED=str(root / 'uploaded.md'), STALE='1' if stale else '',
                       SERVED_CACHE_CONTROL=cache, WEBSITE_FTP_HOST='ftp.example.test',
                       CHANGELOG=str(changelog), HISTORY=str(history), HTACCESS=str(HTACCESS),
                       CHECK_ATTEMPTS='2', CHECK_WAIT='0')
            for name, value in (('WEBSITE_FTP_USER', user), ('WEBSITE_FTP_PASSWORD', password)):
                env.pop(name, None)
                if value is not None:
                    env[name] = value
            result = subprocess.run(['bash', str(SCRIPT)], env=env, capture_output=True, text=True)
            calls = (root / 'calls').read_text().splitlines() if (root / 'calls').exists() else []
            configs = (root / 'configs').read_text() if (root / 'configs').exists() else ''
            self.uploaded = (root / 'uploaded.md').read_text() if (root / 'uploaded.md').exists() else None
            return result, calls, configs

    def test_without_credentials_it_warns_and_uploads_nothing(self):
        result, calls, _ = self.run_script(user=None, password=None)
        self.assertEqual(result.returncode, 0)
        self.assertIn('::warning::', result.stdout)
        self.assertEqual(calls, [])

    def test_half_the_credentials_is_an_error(self):
        result, calls, _ = self.run_script(password=None)
        self.assertEqual(result.returncode, 1)
        self.assertEqual(calls, [])

    def test_it_uploads_both_files_over_tls_and_checks_the_site(self):
        result, calls, _ = self.run_script()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        uploads = [c for c in calls if ' -T ' in f' {c} ']
        self.assertEqual(len(uploads), 2)
        self.assertTrue(uploads[0].endswith('ftp://ftp.example.test/.htaccess'))
        self.assertTrue(uploads[1].endswith('ftp://ftp.example.test/CHANGELOG.md'))
        for call in uploads:
            self.assertIn('--ssl-reqd', call)
        self.assertIn('serves the uploaded changelog', result.stdout)

    def test_it_uploads_the_page_built_from_both_files(self):
        self.run_script()
        self.assertTrue(self.uploaded.startswith('---\nwebsite: '))
        self.assertIn('# CometOTP\n', self.uploaded)
        self.assertIn('## 9.2.2 (2026-11-12)', self.uploaded)
        self.assertTrue(self.uploaded.endswith('- Initial release\n'))
        self.assertNotIn('Notes.', self.uploaded)

    def test_the_password_is_never_on_a_command_line(self):
        _, calls, configs = self.run_script()
        self.assertTrue(all('p@ss' not in call for call in calls))
        # Quotes and backslashes reach curl escaped, so it reads the password intact.
        self.assertIn(r'user = "cometotp-log:p@ss \"w\\rd\""', configs)

    def test_a_site_serving_something_else_fails(self):
        result, _, _ = self.run_script(stale=True)
        self.assertEqual(result.returncode, 1)
        self.assertIn('does not match', result.stdout)

    def test_it_warns_when_browsers_would_keep_an_old_copy(self):
        result, _, _ = self.run_script(cache='public, max-age=31536000')
        self.assertEqual(result.returncode, 0)
        self.assertIn('cache the changelog for a long time', result.stdout)


if __name__ == '__main__':
    unittest.main()
