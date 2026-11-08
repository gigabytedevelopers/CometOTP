#!/usr/bin/env bash
# Downloads the Gradle distribution the wrapper names (gradle/wrapper/gradle-wrapper.properties),
# trying again when the download fails. It is served from GitHub's releases, behind
# services.gradle.org, and a brief outage there (a 504 on 2026-11-08) failed whole CI jobs before
# any of our code ran. With the distribution already in place, from setup-gradle's cache or an
# earlier attempt, it returns at once.
#
# Run from the repository root, before the first ./gradlew step:
#   bash .github/scripts/download_gradle.sh [attempts] [first-wait-seconds]
# The wait doubles after each failed attempt: 15, 30, 60 and 120 seconds by default.
set -u

attempts=${1:-5}
wait=${2:-15}

for attempt in $(seq 1 "$attempts"); do
  if ./gradlew --no-daemon --version; then
    exit 0
  fi
  if [ "$attempt" -lt "$attempts" ]; then
    echo "::warning::Downloading Gradle failed (attempt $attempt of $attempts); trying again in ${wait}s."
    sleep "$wait"
    wait=$((wait * 2))
  fi
done

echo "::error::Could not download Gradle after $attempts attempts."
exit 1
