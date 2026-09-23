#!/usr/bin/env bash
# Removes every locally-built Docker image this module's acceptance tests produced (see
# support/Images.java's CLEANUP_LABEL), independent of whether the failsafe-forked test JVM shut
# down cleanly. Bound to this module's pom.xml at the post-integration-test phase, which Maven
# always runs after integration-test - even after test failures - and before the verify phase
# evaluates results, so cleanup happens on every real test run regardless of outcome.
#
# Deliberately uses `docker images --filter label=...` + `docker rmi`, not `docker image prune`:
# prune's default filters only remove dangling (untagged) images, and every image this module
# builds is tagged (ImageFromDockerfile assigns a random localhost/testcontainers/<id> tag), so
# `docker image prune --filter label=...` would silently remove nothing.
set -euo pipefail

readonly LABEL="io.eventbob.acceptance=true"

if ! command -v docker >/dev/null 2>&1; then
  echo "cleanup-acceptance-images: docker CLI not found on PATH; skipping image cleanup" >&2
  exit 0
fi

image_ids="$(docker images -a --filter "label=${LABEL}" -q | sort -u)"

if [ -z "${image_ids}" ]; then
  echo "cleanup-acceptance-images: no images labeled '${LABEL}' found; nothing to remove"
  exit 0
fi

count="$(printf '%s\n' "${image_ids}" | wc -l | tr -d ' ')"
echo "cleanup-acceptance-images: removing ${count} image(s) labeled '${LABEL}'"
printf '%s\n' "${image_ids}" | xargs -r docker rmi -f
