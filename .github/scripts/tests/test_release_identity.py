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
            'GITHUB_ENV': str(self.directory / 'github-env'),
            'FIXTURE_LOGIN': 'enwokoma',
            'FIXTURE_KEYS': json.dumps([{'key': self.public}]),
            'GITHUB_REF_TYPE': 'tag',
            'GITHUB_REF_NAME': 'v99.0.0',
        }
        self.git('init', '-q')
        # Actions checkout is shallow; preserve its boundary when importing the existing commit.
        self.git('fetch', '-q', '--depth=1', str(ROOT), 'HEAD')
        self.git('checkout', '-q', '--detach', 'FETCH_HEAD')
        (self.repo / '.github').mkdir(exist_ok=True)
        (self.repo / '.github/release-signing.pub').write_text(self.public + '\n')
        (self.repo / '.github/scripts/cleanup_release_identity.sh').write_text(
            (SCRIPTS / 'cleanup_release_identity.sh').read_text())
        self.addCleanup(self.run_script, 'cleanup_release_identity.sh')

    def git(self, *args):
        return subprocess.run(['git', *args], cwd=self.repo, env=self.env,
                              text=True, capture_output=True, check=True)

    def run_script(self, name):
        return subprocess.run(['bash', str(SCRIPTS / name)], cwd=self.repo,
                              env=self.env, text=True, capture_output=True, timeout=30)

    def configure(self):
        result = self.run_script('configure_release_identity.sh')
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        for line in Path(self.env['GITHUB_ENV']).read_text().splitlines():
            name, value = line.split('=', 1)
            self.env[name] = value

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
        self.env.pop('SSH_AUTH_SOCK', None)
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

    def test_encrypted_existing_key_can_sign(self):
        subprocess.run(['ssh-keygen', '-q', '-p', '-P', '', '-N', 'offline-fixture-passphrase',
                        '-f', str(self.key)], check=True, capture_output=True)
        self.env['RELEASE_SIGNING_KEY'] = self.key.read_text()
        self.env['RELEASE_SIGNING_PASSPHRASE'] = 'offline-fixture-passphrase'
        self.configure()
        self.git('tag', '-s', 'v99.0.0', '-m', 'Encrypted signature fixture')
        self.assertEqual(self.run_script('verify_release_tag.sh').returncode, 0)
        self.assertFalse((self.runner / 'enwokoma-release-signing/key').exists())
        self.assertFalse((self.runner / 'enwokoma-release-signing/askpass').exists())
        self.assertNotIn('offline-fixture-passphrase', Path(self.env['GITHUB_ENV']).read_text())

    def test_wrong_passphrase_cleans_up_owned_agent_and_key(self):
        subprocess.run(['ssh-keygen', '-q', '-p', '-P', '', '-N', 'offline-fixture-passphrase',
                        '-f', str(self.key)], check=True, capture_output=True)
        self.env['RELEASE_SIGNING_KEY'] = self.key.read_text()
        self.env['RELEASE_SIGNING_PASSPHRASE'] = 'wrong-fixture-passphrase'
        result = self.run_script('configure_release_identity.sh')
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse((self.runner / 'enwokoma-release-signing').exists())

    def test_existing_agent_needs_no_key_secret_and_is_not_stopped(self):
        self.configure()
        original_runner = self.env['RUNNER_TEMP']
        self.env['RELEASE_SIGNING_KEY'] = ''
        self.env['RUNNER_TEMP'] = str(self.directory / 'external-agent-runner')
        self.configure()
        self.assertEqual(self.run_script('cleanup_release_identity.sh').returncode, 0)
        self.git('tag', '-s', 'v99.0.0', '-m', 'Existing agent fixture')
        self.env['RUNNER_TEMP'] = original_runner

    def test_different_registered_key_is_rejected(self):
        other_key = self.directory / 'other-key'
        subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(other_key)], check=True)
        self.env['FIXTURE_KEYS'] = json.dumps([{'key': self.public},
                                              {'key': other_key.with_suffix('.pub').read_text()}])
        self.env['RELEASE_SIGNING_KEY'] = other_key.read_text()
        self.assertNotEqual(self.run_script('configure_release_identity.sh').returncode, 0)

    def test_cleanup_stops_the_owned_agent(self):
        self.configure()
        self.assertEqual(self.run_script('cleanup_release_identity.sh').returncode, 0)
        result = subprocess.run(['ssh-add', '-l'], env=self.env, capture_output=True)
        self.assertNotEqual(result.returncode, 0)

    def test_tag_from_different_registered_key_is_rejected(self):
        self.configure()
        other_key = self.directory / 'other-key'
        subprocess.run(['ssh-keygen', '-q', '-t', 'ed25519', '-N', '', '-f', str(other_key)], check=True)
        self.env['FIXTURE_KEYS'] = json.dumps([{'key': self.public},
                                              {'key': other_key.with_suffix('.pub').read_text()}])
        self.git('-c', 'user.signingkey=' + str(other_key), 'tag', '-s', 'v99.0.0', '-m', 'Other key fixture')
        self.assertNotEqual(self.run_script('verify_release_tag.sh').returncode, 0)

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
