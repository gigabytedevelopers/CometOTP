"""Exercise iOS release lane behavior without uploading a build or requiring Apple keys."""
import json
import os
from pathlib import Path
import subprocess
import unittest


ROOT = Path(__file__).resolve().parents[3]
HARNESS = r'''
require "json"
class OfflineLaneRunner
  attr_reader :calls
  def initialize
    @lanes = {}
    @calls = []
  end
  def default_platform(name); end
  def platform(name, &block); instance_eval(&block); end
  def desc(text); end
  def lane(name, &block); @lanes[name] = block; end
  alias private_lane lane
  def app_store_connect_api_key(**options)
    @calls << {action: "credentials", options: options}
    {offline: true}
  end
  def upload_to_app_store(**options)
    @calls << {action: "appstore", options: options}
  end
  def upload_to_testflight(**options)
    @calls << {action: "testflight", options: options}
  end
  def method_missing(name, *args)
    instance_exec(*args, &@lanes.fetch(name))
  end
end
runner = OfflineLaneRunner.new
runner.instance_eval(File.read(ARGV[0]), ARGV[0])
runner.public_send(ARGV[1])
puts JSON.generate(runner.calls)
'''


class IOSReleaseTest(unittest.TestCase):
    def setUp(self):
        self.env = {
            **os.environ,
            'APPSTORE_KEY_ID': 'offline-key-id',
            'APPSTORE_ISSUER_ID': 'offline-issuer-id',
            'APPSTORE_KEY_FILE': '/offline/existing-appstore-key.p8',
            'IOS_BUNDLE_ID': 'com.example.offline',
            'IOS_IPA_PATH': '/offline/CometOTP.ipa',
            'IOS_VERSION': '99.0.0',
            'IOS_BUILD_NUMBER': '12.2',
        }

    def run_lane(self, lane):
        return subprocess.run(['ruby', '-e', HARNESS, str(ROOT / 'fastlane/Fastfile'), lane],
                              env=self.env, text=True, capture_output=True, timeout=30)

    def test_appstore_submits_exact_build_and_releases_after_approval(self):
        result = self.run_lane('appstore')
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = json.loads(result.stdout)
        self.assertEqual([call['action'] for call in calls], ['credentials', 'appstore'])
        options = calls[-1]['options']
        self.assertTrue(options['submit_for_review'])
        self.assertTrue(options['automatic_release'])
        self.assertFalse(options['phased_release'])
        self.assertEqual(options['ipa'], self.env['IOS_IPA_PATH'])
        self.assertEqual(options['app_version'], self.env['IOS_VERSION'])
        self.assertEqual(options['build_number'], self.env['IOS_BUILD_NUMBER'])
        self.assertTrue(options['skip_metadata'])
        self.assertTrue(options['skip_screenshots'])

    def test_explicit_testflight_does_not_submit_to_appstore(self):
        result = self.run_lane('testflight')
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = json.loads(result.stdout)
        self.assertEqual([call['action'] for call in calls], ['credentials', 'testflight'])
        self.assertFalse(calls[-1]['options']['distribute_external'])

    def test_missing_bundle_identifier_stops_submission(self):
        del self.env['IOS_BUNDLE_ID']
        result = self.run_lane('appstore')
        self.assertNotEqual(result.returncode, 0)
        self.assertIn('IOS_BUNDLE_ID', result.stderr)

    def test_unknown_lane_is_rejected(self):
        self.assertNotEqual(self.run_lane('unsupported').returncode, 0)


if __name__ == '__main__':
    unittest.main()
