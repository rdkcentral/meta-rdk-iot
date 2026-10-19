#!/usr/bin/env bash
# Create, upload and verify a Gecko SDK tarball with all Git LFS content materialized.
# See README.md in this directory.
set -euo pipefail
set +x

SDK_REPO_URL="https://github.com/SiliconLabs/gecko_sdk.git"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
    cat <<'EOF'
Usage: make-gecko-sdk-tarball.sh <version> [options]

Creates gecko_sdk-<version>.tar.gz from the pristine tag v<version> of the public
Silicon Labs Gecko SDK, with Git LFS files materialized, and optionally uploads or
verifies it on an Artifactory server. The tarball holds only the directories listed in
GECKO_SDK_STAGE_DIRS in silabs-gecko-sdk_<version>.bb (plus the files at the SDK root),
so it is a small fraction of the full SDK.

Arguments:
  <version>                 SDK version, for example 4.4.6 or v4.4.6 (exactly one)

Options:
  --out-dir DIR             Output directory (default: ./out)
  --upload [BASE_URL]       Upload to <BASE_URL>/silabs-zigbee/silabs/gsdk/<version>/gecko_sdk-<version>.tar.gz.
                            BASE_URL defaults to $RDK_ARTIFACTS_BASE_URL.
  --verify BASE_URL         Download the artifact from BASE_URL and compare its sha256
                            instead of building a tarball.
  --sha256 SHA              Expected sha256 for --verify (default: GECKO_SDK_SHA256 from
                            silabs-gecko-sdk_<version>.bb next to this script)
  --reuse                   Reuse an existing tarball in the output directory (verified against
                            its .sha256 file and the current GECKO_SDK_STAGE_DIRS) instead of cloning and building again
  --force                   Overwrite an existing artifact on upload
  -h, --help                Show this help

Credentials: curl uses ~/.netrc when it has an entry for the host (curl --netrc-optional).
ARTIFACTORY_USER and ARTIFACTORY_TOKEN, when both set, override it.

Example:
  make-gecko-sdk-tarball.sh 4.4.6 --upload https://artifacts.example.com/artifactory
EOF
}

die() { echo "error: $*" >&2; exit 1; }

normalize_version() {
    local v="${1#v}"
    [[ "$v" =~ ^[0-9]+(\.[0-9]+){1,3}$ ]] || die "invalid version '$1' (expected e.g. 4.4.6 or v4.4.6)"
    echo "$v"
}

require_tools() {
    local t
    for t in "$@"; do
        command -v "$t" >/dev/null 2>&1 || die "required tool not found: $t"
    done
}

host_of() {
    local rest="${1#*://}"
    rest="${rest%%/*}"
    rest="${rest##*@}"
    echo "${rest%%:*}"
}

warn_netrc_perms() {
    local f="$HOME/.netrc" mode
    [ -f "$f" ] || return 0
    mode="$(stat -c '%a' "$f")"
    if [ "${mode: -2}" != "00" ]; then
        echo "warning: $f is accessible by group/others (mode $mode); run: chmod 600 $f" >&2
    fi
}

# curl wrapper; secrets are passed via a config on stdin, never on the command line.
do_curl() {
    if [ -n "${ARTIFACTORY_USER:-}" ] && [ -n "${ARTIFACTORY_TOKEN:-}" ]; then
        printf 'user = "%s:%s"\n' "$ARTIFACTORY_USER" "$ARTIFACTORY_TOKEN" | curl -K - --netrc-optional "$@"
    else
        curl --netrc-optional "$@"
    fi
}

auth_help() {
    local host="$1"
    cat >&2 <<EOF
error: authentication failed for $host.
Add a line to ~/.netrc and restrict its permissions:
  machine $host login <user> password <token>
  chmod 600 ~/.netrc
Alternatively set ARTIFACTORY_USER and ARTIFACTORY_TOKEN.
EOF
}

http_code() {
    # args: curl args; prints the HTTP status code
    do_curl -sS -o /dev/null -w '%{http_code}' "$@"
}

recipe_sha() {
    local f="$SCRIPT_DIR/silabs-gecko-sdk_$1.bb"
    [ -f "$f" ] || return 0
    sed -n 's/^GECKO_SDK_SHA256[[:space:]]*?\{0,1\}=[[:space:]]*"\([0-9a-fA-F]*\)".*/\1/p' "$f" | head -n 1
}

# Directories staged by the recipe (GECKO_SDK_STAGE_DIRS in silabs-gecko-sdk_<ver>.bb); the tarball
# contains only these plus the files at the SDK root.
stage_dirs() {
    local f="$SCRIPT_DIR/silabs-gecko-sdk_$1.bb"
    [ -f "$f" ] || die "recipe not found: $f"
    awk '/^GECKO_SDK_STAGE_DIRS/ {on=1; next} on && /^"/ {exit} on {gsub(/[\\ \t]/, ""); if ($0 != "") print}' "$f"
}

VERSION=""
OUT_DIR="./out"
DO_UPLOAD=0
UPLOAD_URL=""
VERIFY_URL=""
EXPECTED_SHA=""
FORCE=0
REUSE=0

while [ $# -gt 0 ]; do
    case "$1" in
        -h|--help) usage; exit 0 ;;
        --out-dir) [ $# -ge 2 ] || die "--out-dir needs an argument"; OUT_DIR="$2"; shift 2 ;;
        --upload)
            DO_UPLOAD=1
            if [ $# -ge 2 ] && [[ "$2" != -* ]] && [[ "$2" == *://* ]]; then UPLOAD_URL="$2"; shift; fi
            shift ;;
        --verify) [ $# -ge 2 ] || die "--verify needs BASE_URL"; VERIFY_URL="$2"; shift 2 ;;
        --sha256) [ $# -ge 2 ] || die "--sha256 needs an argument"; EXPECTED_SHA="$2"; shift 2 ;;
        --force) FORCE=1; shift ;;
        --reuse) REUSE=1; shift ;;
        -*) die "unknown option: $1 (see --help)" ;;
        *)
            [ -z "$VERSION" ] || die "exactly one version argument is allowed"
            VERSION="$(normalize_version "$1")"
            shift ;;
    esac
done

[ -n "$VERSION" ] || { usage >&2; die "missing <version>"; }

TAG="v$VERSION"
ARTIFACT_PATH="silabs-zigbee/silabs/gsdk/$VERSION"
FILE_NAME="gecko_sdk-$VERSION.tar.gz"
TOP_DIR="gecko_sdk-$VERSION"

if [ -n "$VERIFY_URL" ]; then
    require_tools curl sha256sum mktemp
    warn_netrc_perms
    [ -n "$EXPECTED_SHA" ] || EXPECTED_SHA="$(recipe_sha "$VERSION")"
    [ -n "$EXPECTED_SHA" ] || die "no expected sha256: pass --sha256 or set GECKO_SDK_SHA256 in silabs-gecko-sdk_$VERSION.bb"
    mkdir -p "$OUT_DIR"
    tmp="$(mktemp -d "$OUT_DIR/verify.XXXXXX")"
    trap 'rm -rf "$tmp"' EXIT
    url="${VERIFY_URL%/}/$ARTIFACT_PATH/$FILE_NAME"
    code="$(do_curl -sS -L -o "$tmp/$FILE_NAME" -w '%{http_code}' "$url")" || die "download failed"
    case "$code" in
        200) ;;
        401|403) auth_help "$(host_of "$url")"; exit 1 ;;
        *) die "download returned HTTP $code for $url" ;;
    esac
    actual="$(sha256sum "$tmp/$FILE_NAME" | cut -d' ' -f1)"
    if [ "$actual" = "$EXPECTED_SHA" ]; then
        echo "MATCH: $url sha256 $actual"
        exit 0
    fi
    echo "MISMATCH: expected $EXPECTED_SHA, got $actual" >&2
    exit 1
fi

if [ "$DO_UPLOAD" = 1 ]; then
    UPLOAD_URL="${UPLOAD_URL:-${RDK_ARTIFACTS_BASE_URL:-}}"
    [ -n "$UPLOAD_URL" ] || die "--upload needs BASE_URL or RDK_ARTIFACTS_BASE_URL"
fi

require_tools sha256sum mktemp date
mkdir -p "$OUT_DIR"
OUT_DIR="$(cd "$OUT_DIR" && pwd)"
TARBALL="$OUT_DIR/$FILE_NAME"
INFO="$OUT_DIR/gecko_sdk-$VERSION.info"
mapfile -t STAGE_DIRS < <(stage_dirs "$VERSION")
[ "${#STAGE_DIRS[@]}" -gt 0 ] || die "GECKO_SDK_STAGE_DIRS not found or empty in silabs-gecko-sdk_$VERSION.bb"
STAGE_HASH="$(printf '%s\n' "${STAGE_DIRS[@]}" | sort | sha256sum | cut -d' ' -f1)"
if [ "$REUSE" = 1 ] && [ -f "$TARBALL" ] && [ -f "$TARBALL.sha256" ] \
    && (cd "$OUT_DIR" && sha256sum -c --status "$FILE_NAME.sha256") \
    && grep -q "^stage_dirs_sha256: $STAGE_HASH\$" "$INFO" 2>/dev/null; then
    echo "Reusing existing $TARBALL"
    SHA="$(cut -d' ' -f1 "$TARBALL.sha256")"
else
    [ "$REUSE" = 0 ] || echo "No valid existing tarball to reuse; building a new one"
    require_tools git git-lfs tar gzip
    [ "$DO_UPLOAD" = 0 ] || require_tools curl
    if gzip --version 2>&1 | grep -qi pigz; then
        die "gzip is pigz; GNU gzip is required for reproducible output"
    fi
    tar --version 2>&1 | grep -q 'GNU tar' || die "GNU tar is required"

    WORK="$(mktemp -d "$OUT_DIR/work.XXXXXX")"
    trap 'rm -rf "$WORK"' EXIT

    echo "Cloning $SDK_REPO_URL at $TAG (${#STAGE_DIRS[@]} directories)"
    git clone --depth 1 --sparse --branch "$TAG" "$SDK_REPO_URL" "$WORK/sdk"
    (
        cd "$WORK/sdk"
        GIT_LFS_SKIP_SMUDGE=1 git sparse-checkout set --cone "${STAGE_DIRS[@]}"
        lfs_include="$(IFS=,; echo "${STAGE_DIRS[*]}")"
        git lfs pull --include="$lfs_include"
    )

    cd "$WORK/sdk"
    # A materialized LFS file no longer starts with the pointer header. Only files inside the
    # sparse checkout exist.
    pointers=0
    while IFS= read -r f; do
        [ -f "$f" ] || continue
        if head -c 40 "$f" | grep -q '^version https://git-lfs'; then
            echo "LFS pointer not materialized: $f" >&2
            pointers=$((pointers + 1))
        fi
    done < <(git lfs ls-files --name-only)
    [ "$pointers" -eq 0 ] || die "$pointers LFS pointer file(s) remain"

    actual_ver="$(sed -n 's/^sdk_version:[[:space:]]*"\([^"]*\)".*/\1/p' gecko_sdk.slcs | head -n 1)"
    [ "$actual_ver" = "$VERSION" ] || die "gecko_sdk.slcs sdk_version '$actual_ver' does not match $VERSION"

    COMMIT="$(git rev-parse HEAD)"
    COMMIT_EPOCH="$(git log -1 --format=%ct)"
    COMMIT_DATE="$(git log -1 --format=%cI)"

    echo "Creating $TARBALL"
    tar --sort=name --mtime="@$COMMIT_EPOCH" --owner=0 --group=0 --numeric-owner --format=gnu \
        --exclude=.git --transform "s,^\.\(/\|\$\),$TOP_DIR/," -C "$WORK/sdk" -cf - . | gzip -n -9 > "$TARBALL"

    SHA="$(sha256sum "$TARBALL" | cut -d' ' -f1)"
    (cd "$OUT_DIR" && sha256sum "$FILE_NAME" > "$FILE_NAME.sha256")
    {
        echo "source_url: $SDK_REPO_URL"
        echo "tag: $TAG"
        echo "commit: $COMMIT"
        echo "commit_date: $COMMIT_DATE"
        echo "git_lfs: $(git lfs version)"
        echo "tar: $(tar --version | head -n 1)"
        echo "gzip: $(gzip --version 2>&1 | head -n 1)"
        echo "sha256: $SHA"
        echo "stage_dirs_sha256: $STAGE_HASH"
        echo "stage_dirs: ${STAGE_DIRS[*]}"
        echo "created: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    } > "$INFO"
fi
cd "$OUT_DIR"

if [ "$DO_UPLOAD" = 1 ]; then
    warn_netrc_perms
    url="${UPLOAD_URL%/}/$ARTIFACT_PATH/$FILE_NAME"
    host="$(host_of "$url")"
    code="$(http_code -I -L "$url")" || die "HEAD request failed"
    case "$code" in
        200) [ "$FORCE" = 1 ] || die "$url already exists; use --force to overwrite" ;;
        404) ;;
        401|403) auth_help "$host"; exit 1 ;;
        *) die "HEAD returned HTTP $code for $url" ;;
    esac
    echo "Uploading $(du -h "$TARBALL" | cut -f1) to $url"
    resp="$(mktemp "$OUT_DIR/upload-response.XXXXXX")"
    trap 'rm -f "$resp"' EXIT
    code="$(do_curl -sS -o "$resp" -w '%{http_code}' -T "$TARBALL" -H "X-Checksum-Sha256: $SHA" "$url")" || die "upload failed"
    case "$code" in
        200|201) echo "Uploaded." ;;
        401|403) auth_help "$host"; exit 1 ;;
        *)
            echo "server response (first 20 lines):" >&2
            head -n 20 "$resp" >&2
            if [ "$code" = 502 ]; then
                echo "hint: a 502 on a large upload is often the proxy hiding an early rejection by Artifactory" >&2
                echo "      (for example 403, no deploy permission). Try a tiny upload to see the real error:" >&2
                echo "      echo test > t.txt; curl --netrc-optional -T t.txt ${UPLOAD_URL%/}/$ARTIFACT_PATH/_upload-test/t.txt" >&2
            fi
            die "upload returned HTTP $code" ;;
    esac
fi

echo
echo "Paste into the recipe or bbappend:"
echo "GECKO_SDK_SHA256 = \"$SHA\""
