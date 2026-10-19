# silabs-gecko-sdk

Pristine, unpatched Silicon Labs Gecko SDK (GSDK) as a source-only Yocto recipe. It does not compile anything and produces no packages: it stages a subset of the SDK into the sysroot so that consumers such as `barton-zigbee-efr32` (ZigbeeCore and zigbeed) can build against it.

## Why an Artifactory tarball

The public [gecko_sdk](https://github.com/SiliconLabs/gecko_sdk) repository stores its prebuilt libraries in Git LFS, and its `.lfsconfig` points LFS at a Silicon Labs server that our build infrastructure cannot use. A plain `git archive` would export LFS pointer files rather than the libraries. `make-gecko-sdk-tarball.sh` builds a tarball with the LFS content materialized, and Comcast layers fetch that tarball from Artifactory instead.

The tarball is not the whole SDK. It contains only the directories listed in `GECKO_SDK_STAGE_DIRS` in the recipe (the same list the recipe stages) plus the files at the SDK root, about 210 MB unpacked and under 50 MB compressed, against 3.9 GB for the full tree. The script clones with a sparse checkout and downloads only the matching LFS objects, so building it takes about a minute. If `GECKO_SDK_STAGE_DIRS` changes, rebuild and re-upload the tarball and update `GECKO_SDK_SHA256`; `do_install` fails if a listed directory is missing from the tarball.

Without any configuration the recipe clones the tag from GitHub with `lfs=1`. That works only where the LFS server is reachable and `git-lfs` is installed on the build host.

## One version per recipe

The recipe version, the SDK tag and the artifact all use the same version number, and nothing else may carry a second copy of it:

- recipe: `silabs-gecko-sdk_<ver>.bb`
- tag: `v<ver>`
- artifact: `silabs-zigbee/silabs/gsdk/<ver>/gecko_sdk-<ver>.tar.gz` (repository `silabs-zigbee`, next to the existing `silabs/zigbee/stack.source` artifacts) (top-level directory `gecko_sdk-<ver>/`)

`do_configure` compares `sdk_version` in `gecko_sdk.slcs` with `${PV}` and fails the build on a mismatch.

## Creating, uploading and verifying a tarball

Requirements: `git`, `git-lfs`, GNU `tar`, GNU `gzip` (not pigz), `sha256sum`, and `curl` for upload and verify.

```bash
# Build gecko_sdk-4.4.6.tar.gz, .sha256 and .info under ./out (downloads a few hundred MB)
./make-gecko-sdk-tarball.sh 4.4.6

# Upload a tarball that is already built, without cloning or building again
./make-gecko-sdk-tarball.sh 4.4.6 --reuse --upload https://artifacts.example.com/artifactory

# Build and upload to https://artifacts.example.com/artifactory/silabs-zigbee/silabs/gsdk/4.4.6/gecko_sdk-4.4.6.tar.gz
./make-gecko-sdk-tarball.sh 4.4.6 --upload https://artifacts.example.com/artifactory

# Base URL from the environment instead
RDK_ARTIFACTS_BASE_URL=https://artifacts.example.com/artifactory ./make-gecko-sdk-tarball.sh 4.4.6 --upload

# Download the artifact again and compare with GECKO_SDK_SHA256 from the recipe
./make-gecko-sdk-tarball.sh 4.4.6 --verify https://artifacts.example.com/artifactory
# or against an explicit checksum
./make-gecko-sdk-tarball.sh 4.4.6 --verify https://artifacts.example.com/artifactory --sha256 <sha256>
```

The tarball is reproducible: files are sorted, timestamps are the tag commit time, owners are root, and gzip runs with `-n`. Two independent builds of the same tag and list give byte-identical files. `--reuse` only reuses a tarball whose checksum file and recorded `GECKO_SDK_STAGE_DIRS` match the current recipe. The script prints a line `GECKO_SDK_SHA256 = "<sha>"` at the end. Uploading refuses to overwrite an existing artifact unless `--force` is given.

### Credentials

curl reads `~/.netrc`. Add an entry for the Artifactory host and restrict the file:

```text
machine artifacts.example.com login <user> password <token>
```

```bash
chmod 600 ~/.netrc
```

Alternatively export `ARTIFACTORY_USER` and `ARTIFACTORY_TOKEN`; they override `~/.netrc`. Credentials are never placed on the command line or printed.

## Opting in from a Comcast layer

In a bbappend (or distro configuration) in the Comcast layer:

```text
GECKO_SDK_ARTIFACT_PATH = "silabs-zigbee/silabs/gsdk"
GECKO_SDK_SHA256 = "<sha256 printed by make-gecko-sdk-tarball.sh>"
```

`RDK_ARTIFACTS_BASE_URL` provides the server (for example `https://artifacts.example.com/artifactory`). The source then becomes `${RDK_ARTIFACTS_BASE_URL}/${GECKO_SDK_ARTIFACT_PATH}/${PV}/gecko_sdk-${PV}.tar.gz`, and the GitHub clone is not used. Parsing fails if `RDK_ARTIFACTS_BASE_URL` or `GECKO_SDK_SHA256` is empty while `GECKO_SDK_ARTIFACT_PATH` is set.

## Bumping the version

1. Copy `silabs-gecko-sdk_<old>.bb` to `silabs-gecko-sdk_<new>.bb`.
2. Update `SRCREV_gecko` to the commit of tag `v<new>` (`git ls-remote https://github.com/SiliconLabs/gecko_sdk.git refs/tags/v<new>`) and the `LIC_FILES_CHKSUM` if `License.txt` changed.
3. Run `./make-gecko-sdk-tarball.sh <new> --upload`.
4. Set `GECKO_SDK_SHA256` to the printed value.
5. Re-check `GECKO_SDK_STAGE_DIRS` against the consumers' makefiles (the derivation is described in the recipe).

## What is staged

Everything lands in `${datadir}/silabs-gecko-sdk/` (the sysroot path `recipe-sysroot/usr/share/silabs-gecko-sdk/`):

- the subtrees listed in `GECKO_SDK_STAGE_DIRS`, with their layout relative to the SDK root preserved
- `gecko_sdk.slcs`, `License.txt`
- `gecko_sdk.version`, containing exactly the SDK version and a newline

Consumers can point `BASE_SDK_PATH` at that directory.

## Patches

This recipe never patches the SDK. Patches live in the consuming application repository (`BartonZigbeeEFR32/gecko_sdk_patches`) and are applied by that build to its own copy.
