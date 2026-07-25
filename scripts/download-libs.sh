#!/usr/bin/env bash
# Installs the GitHub-hosted dependencies (ceres, sedna, sedna-buildroot) into
# libs/ in a Maven repository layout, so the build can resolve them without
# GitHub Packages authentication.
#
# Layout produced (under libs/):
#
#     li/cil/ceres/ceres/0.0.4/
#         ceres-0.0.4.jar
#         ceres-0.0.4.pom
#     li/cil/sedna/sedna/2.0.13/
#         sedna-2.0.13.jar
#         sedna-2.0.13.pom
#     li/cil/sedna/sedna-buildroot/0.0.64/
#         sedna-buildroot-0.0.64.jar
#         sedna-buildroot-0.0.64.pom
#
# Run this once after cloning the repo. Re-run with --force after bumping
# the dep versions in build.gradle / gradle.properties.
#
# Usage:
#   ./download-libs.sh          # download everything
#   ./download-libs.sh --force  # re-download even if files exist
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p libs

FORCE=0
[ "${1:-}" = "--force" ] && FORCE=1

# install_maven <group-path> <artifact> <version> <release-url>
#
# group-path is the slash-separated Maven groupId, e.g. "li/cil/ceres".
# Downloads the jar, writes a minimal POM, both into
# libs/<group-path>/<artifact>/<version>/.
install_maven() {
    local group_path="$1"
    local artifact="$2"
    local version="$3"
    local release_url="$4"

    local dir="libs/${group_path}/${artifact}/${version}"
    local jar="${dir}/${artifact}-${version}.jar"
    local pom="${dir}/${artifact}-${version}.pom"

    if [ "$FORCE" = "0" ] && [ -f "$jar" ] && [ -f "$pom" ]; then
        echo "[skip] ${jar#libs/} already exists"
        return 0
    fi

    mkdir -p "$dir"

    echo "[download] ${jar#libs/}"
    echo "  from: ${release_url}"
    curl -fL "${release_url}" -o "${jar}.tmp"
    mv "${jar}.tmp" "$jar"

    # Group ID = group_path with '/' replaced by '.'
    local group_id="${group_path//\//.}"

    cat > "$pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>${group_id}</groupId>
    <artifactId>${artifact}</artifactId>
    <version>${version}</version>
    <packaging>jar</packaging>
</project>
EOF

    echo "  -> ${jar#libs/}"
    echo "  -> ${pom#libs/}"
}

# Versions must match build.gradle / gradle.properties
CERES_VERSION="0.0.4"
SEDNA_VERSION="2.0.13"
SEDNA_BUILDROOT_VERSION="0.0.64"

install_maven "li/cil/ceres" "ceres" "${CERES_VERSION}" \
    "https://github.com/fnuecke/ceres/releases/download/${CERES_VERSION}/ceres-${CERES_VERSION}%2Ba598bbd.jar"

install_maven "li/cil/sedna" "sedna" "${SEDNA_VERSION}" \
    "https://github.com/North-Western-Development/sedna/releases/download/${SEDNA_VERSION}/sedna-${SEDNA_VERSION}%2Bbe63555.jar"

install_maven "li/cil/sedna" "sedna-buildroot" "${SEDNA_BUILDROOT_VERSION}" \
    "https://github.com/North-Western-Development/minux/releases/download/${SEDNA_BUILDROOT_VERSION}/sedna-buildroot-${SEDNA_BUILDROOT_VERSION}%2Bunknown.jar"

echo
echo "Done. Maven layout under libs/:"
find libs -type f \( -name '*.jar' -o -name '*.pom' \) | sort
