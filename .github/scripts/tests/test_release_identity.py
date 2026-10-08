"""Exercise release identity gates with isolated keys and an offline GitHub fixture.

No commits are created or published: tag fixtures point to an existing repository commit.
Run with: python3 -m unittest discover -s .github/scripts/tests -v
"""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
SCRIPTS = ROOT / '.github/scripts'


class ReleaseIdentityTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='cometotp-identity-test-')
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.repo = self.directory / 'repo'
        self.repo.mkdir()
        self.bin = self.directory / 'bin'
        self.bin.mkdir()
        self.runner = self.directory / 'runner'
        self.runner.mkdir()
        self.key = self.directory / 'fixture-key'
        subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(self.key)], check=True)
        self.public = self.key.with_suffix('.pub').read_text().strip()
        # Mock only GitHub. Real Git/OpenSSH check that the setup can sign and verify a tag.
        gh = self.bin / 'gh'
        gh.write_text('''#!/usr/bin/env python3
import json, os, sys
if sys.argv[1:3] == ['api', 'user']:
    print(os.environ['FIXTURE_LOGIN'])
elif sys.argv[1:3] == ['api', 'users/enwokoma/ssh_signing_keys']:
    keys = json.loads(os.environ['FIXTURE_KEYS'])
    if '--jq' in sys.argv:
        print('\\n'.join('enwokoma ' + key['key'] for key in keys))
    else:
        print(json.dumps([keys]))
else:
    sys.exit('Unexpected GitHub operation: ' + repr(sys.argv[1:]))
''')
        gh.chmod(0o755)
        self.env = {
            **os.environ,
            'PATH': str(self.bin) + os.pathsep + os.environ['PATH'],
            'GH_TOKEN': 'offline-fixture-token',
            'RELEASE_SIGNING_KEY': self.key.read_text(),
            'RUNNER_TEMP': str(self.runner),
            'FIXTURE_LOGIN': 'enwokoma',
            'FIXTURE_KEYS': json.dumps([{'key': self.public}]),
            'GITHUB_REF_TYPE': 'tag',
            'GITHUB_REF_NAME': 'v99.0.0',
        }
        self.git('init', '-q')
        self.git('fetch', '-q', str(ROOT), 'HEAD')
        self.git('checkout', '-q', '--detach', 'FETCH_HEAD')

    def git(self, *args):
        return subprocess.run(['git', *args], cwd=self.repo, env=self.env,
                              text=True, capture_output=True, check=True)

    def run_script(self, name):
        return subprocess.run(['bash', str(SCRIPTS / name)], cwd=self.repo,
                              env=self.env, text=True, capture_output=True)

    def configure(self):
        result = self.run_script('configure_release_identity.sh')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_missing_token_does_not_restore_key(self):
        self.env['GH_TOKEN'] = ''
        result = self.run_script('configure_release_identity.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.runner / 'enwokoma-release-signing/key').exists())

    def test_wrong_account_does_not_restore_key(self):
        self.env['FIXTURE_LOGIN'] = 'github-actions[bot]'
        result = self.run_script('configure_release_identity.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.runner / 'enwokoma-release-signing/key').exists())

    def test_missing_signing_key_fails(self):
        self.env['RELEASE_SIGNING_KEY'] = ''
        result = self.run_script('configure_release_identity.sh')
        self.assertNotEqual(result.returncode, 0)

    def test_unregistered_key_is_rejected(self):
        self.env['FIXTURE_KEYS'] = '[]'
        result = self.run_script('configure_release_identity.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('not registered', result.stderr)

    def test_registered_key_can_sign_and_verify_tag(self):
        self.configure()
        self.assertEqual(self.git('config', '--local', 'commit.gpgsign').stdout.strip(), 'true')
        self.git('tag', '-s', 'v99.0.0', '-m', 'Offline signature fixture')
        result = self.run_script('verify_release_tag.sh')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_lightweight_tag_is_rejected(self):
        self.configure()
        self.git('-c', 'tag.gpgsign=false', 'tag', 'v99.0.0')
        result = self.run_script('verify_release_tag.sh')
        self.assertNotEqual(result.returncode, 0)

    def test_tag_from_unregistered_signer_is_rejected(self):
        self.configure()
        self.git('tag', '-s', 'v99.0.0', '-m', 'Offline signature fixture')
        self.env['FIXTURE_KEYS'] = '[]'
        result = self.run_script('verify_release_tag.sh')
        self.assertNotEqual(result.returncode, 0)

    def test_wrong_tagger_email_is_rejected(self):
        self.configure()
        self.git('-c', 'user.email=fixture@example.test', 'tag', '-s', 'v99.0.0', '-m', 'Offline signature fixture')
        result = self.run_script('verify_release_tag.sh')
        self.assertNotEqual(result.returncode, 0)

    def test_branch_release_is_rejected(self):
        self.env['GITHUB_REF_TYPE'] = 'branch'
        result = self.run_script('verify_release_tag.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('not on a branch', result.stdout)


if __name__ == '__main__':
    unittest.main()
