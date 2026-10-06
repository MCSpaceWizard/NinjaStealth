#!/usr/bin/env bash
# Cloud-session setup for Emergent Stealth (Claude Code on the web / remote environments).
# Installs Temurin JDK 25 (checksum-verified) and points Gradle at it. Idempotent and safe to re-run.
# Use as the environment's setup script:  bash scripts/cloud-setup.sh
set -euo pipefail

JDK_DIR=/opt/jdk25
JDK_TAG="jdk-25.0.4.1%2B1"
JDK_FILE="OpenJDK25U-jdk_x64_linux_hotspot_25.0.4.1_1.tar.gz"
JDK_URL="https://github.com/adoptium/temurin25-binaries/releases/download/${JDK_TAG}/${JDK_FILE}"

if [ ! -x "${JDK_DIR}/bin/java" ]; then
  tmp="$(mktemp -d)"
  curl -fsSL -o "${tmp}/jdk.tgz" "${JDK_URL}"
  curl -fsSL -o "${tmp}/jdk.sha256" "${JDK_URL}.sha256.txt"
  echo "$(cut -d' ' -f1 "${tmp}/jdk.sha256")  ${tmp}/jdk.tgz" | sha256sum -c -
  mkdir -p "${JDK_DIR}"
  tar -xzf "${tmp}/jdk.tgz" -C "${JDK_DIR}" --strip-components=1
  rm -rf "${tmp}"
fi

# Let Gradle's toolchain resolution find JDK 25 without downloading one.
mkdir -p "${HOME}/.gradle"
props="${HOME}/.gradle/gradle.properties"
touch "${props}"
grep -q '^org.gradle.java.installations.paths=' "${props}" \
  || echo "org.gradle.java.installations.paths=${JDK_DIR}" >> "${props}"

"${JDK_DIR}/bin/java" -version
