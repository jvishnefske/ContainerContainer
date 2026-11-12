# ContainerContainer

[![ci](https://github.com/jvishnefske/ContainerContainer/actions/workflows/ci.yml/badge.svg)](https://github.com/jvishnefske/ContainerContainer/actions/workflows/ci.yml)
[![integration](https://github.com/jvishnefske/ContainerContainer/actions/workflows/integration.yml/badge.svg)](https://github.com/jvishnefske/ContainerContainer/actions/workflows/integration.yml)

A fork of JFrog Artifactory OSS 7.161.26, built from JFrog's published AGPL-3.0 sources (the first commit is the
unmodified release). It is not affiliated with or endorsed by JFrog. The fork adds:

- A Groovy user-plugin engine (`download`, `storage`, `executions` and `realms` blocks) in place of the no-op OSS stub.
- Plugin-defined repository package types, riding on Generic repositories, with download, upload, index and full
  HTTP `api` handlers whose permissions are enforced in Java before plugin code runs.
- An OCI / Docker registry written as a plugin (`plugins/oci/`), passing the OCI distribution-spec v1.1.0
  conformance suite.
- Plugin realms (`realms {}`): authentication plugins for Basic and Bearer credentials, with admin grants, group
  assignment and just-in-time users constrained in Java.

Requirements and the tests that demonstrate each one are in `design.md`. Every commit is built and unit-tested by
the `ci` workflow; the `integration` workflow builds an overlay image (the stock
`releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26` image with the fork's jars swapped in) and runs the live
scripts in `docker/it` against throwaway containers. The fork is distributed under the GNU Affero General Public
License v3; see `LICENSE`.

## Binary release and Nix

The repository is a Nix flake (`flake.nix`, inputs locked in `flake.lock`: nixpkgs `nixos-26.05`, which provides
OpenJDK 25 as `jdk25` and Maven 3.9, and `oxalica/rust-overlay` for a Rust toolchain with the `wasm32-wasip1`
target). Its outputs, for `x86_64-linux` and `aarch64-linux`:

- `packages.jars`: the jars the overlay installs into the stock image's `WEB-INF/lib` (the 18
  `artifactory-*-7.161.15.jar` it replaces and the runtime dependencies it adds, today `groovy-4.0.32.jar`), chosen
  by the same rules as `docker/build-overlay.sh` against the stock listing committed as `nix/stock-lib.txt`, plus
  `manifest.json` (each jar's name, role and sha256, and the source git revision) and the replace, add, stock and
  expected listings.
- `packages.war`: the complete `artifactory.war` from the same build, the input for a future from-scratch image.
- `packages.release` (the default package): `containercontainer-<version>-jars.tar.gz` and `SHA256SUMS`. The
  tarball holds the jars and manifest, `LICENSE`, `plugins/wasm-abi/abi-v1.schema.json` and a `NOTICE` naming the
  source revision and the AGPL source offer (this repository at the release tag). It is packed deterministically:
  entries sorted, mtime set to the commit time, owner and group 0, normalised modes, gzip without a timestamp.
- `lib.buildWasmPlugin { pkgs, src, cargoLock, pname, ... }`: builds a Rust crate for `wasm32-wasip1` and outputs
  `<pname>.wasm` (and `<pname>.wasm.yaml` when `manifest` is given). Its check phase validates the module and fails
  the build if it imports anything outside `wasi_snapshot_preview1`. `nix/tests/wasm-hello` is the crate it is
  tested with.
- `checks`: the jar set against the stock listing, deterministic packing of the release, the `wasm-hello` module
  answering ABI v1 envelopes under wasmtime with schema-valid replies, and the imports check rejecting a module.
- `devShells.default`: JDK 25, Maven, Rust with `wasm32-wasip1`, wasm-tools, wasmtime and jq.

The jars are built with nixpkgs' `maven.buildMavenPackage` in two steps. A fixed-output derivation runs the Maven
build once with network access, resolving only from Maven Central and `jfrog-jars` (`.github/maven/settings.xml`),
and keeps the local repository it filled; its hash is the single line in `nix/mvn-hash.txt`. The real build then
runs offline (`mvn -o`, tests skipped) against a copy of that repository, so nothing outside the fixed-output step
touches the network. Whenever a `pom.xml` change adds, removes or bumps a dependency or plugin, run
`nix/update-mvn-hash.sh` and commit the new `nix/mvn-hash.txt`. Until then the offline build fails on the missing
artifact: Nix reuses a fixed-output derivation by its hash, so a stale hash never re-downloads anything. Every
plugin must have a fixed version for the hash to stay stable: a versionless plugin makes Maven pick the newest
release from repository metadata, which changes the downloaded set whenever upstream publishes. The root and
`build-handler` POMs pin the two plugins the JFrog parent POMs leave versionless for that reason.

The release version has one source, `nix/version.txt`, of the form `<upstream release>-cc.<n>` (for example
`7.161.26-cc.1`); `n` counts the fork's releases of that upstream release and is bumped for each one. Pushing tag
`v<version>` runs `.github/workflows/release.yml`, which refuses a tag that differs from `nix/version.txt`, runs
`nix flake check`, builds `.#release`, rebuilds it to confirm identical bytes, verifies `SHA256SUMS` and publishes a
GitHub Release with the tarball and the sums. Running that workflow by hand does everything except publishing. The
`nix` workflow runs the flake checks and builds the jars and the release on every push and pull request.

A consumer (for example a private deployment repository) pins two things. It takes this flake as an input at the
release tag, for `lib.buildWasmPlugin` and for the version, and it fetches the release asset with `fetchurl` by its
URL (`https://github.com/jvishnefske/ContainerContainer/releases/download/v<version>/containercontainer-<version>-jars.tar.gz`)
and the sha256 published in `SHA256SUMS`, so it never rebuilds the jars itself. Alternatively it can use
`packages.jars` from the flake input directly and let Nix build or substitute them. The image a consumer runs is
its own: today that means overlaying the jars on the stock image (as `docker/build-overlay.sh` does) and adding its
Groovy plugins and `buildWasmPlugin` outputs to the plugins directory. A minimal from-scratch OCI image built by the
flake from `packages.war`, with a `plugins` argument, is planned and will be attached to releases as an additional
asset.

The upstream README follows.

---

# JFrog Artifactory OSS

**JFrog Artifactory OSS** JFrog’s Artifactory open source project was created to speed up development cycles using binary repositories. It’s the world’s most advanced repository manager, creating a single place for teams to manage all their binary artifacts efficiently.

---

## 🛠️ Getting Started

### Prerequisites
- **Java** 25
- **Maven** 3.6+

---

## 🔧 Build the Project

```bash
mvn clean install
```

---

## ✅ Running Unit Tests

### Run Tests for Specific Modules

```bash
mvn test -pl base/api,base/base-rest
```

### Additional Options

- **Single Module**
  ```bash
  mvn test -pl base/api
  ```

- **Specific Test Class**
  ```bash
  mvn test -pl base/api -Dtest=YourTestClassName
  ```

---

## 🧪 Running PIT Mutation Tests

PIT mutation testing checks how effective your tests are by mutating code and checking if tests detect the changes.

### Basic Mutation Test Command

```bash
mvn test-compile org.pitest:pitest-maven:mutationCoverage -Dthreads=4 -pl base/api,base/base-rest
```

### Advanced Usage

- **More parallelism**
  ```bash
  mvn test-compile org.pitest:pitest-maven:mutationCoverage -Dthreads=8 -pl base/api
  ```

- **Target specific packages**
  ```bash
  mvn test-compile org.pitest:pitest-maven:mutationCoverage -DtargetClasses=org.artifactory.* -pl base/api
  ```

---

## 📊 View PIT Reports

After running mutation tests, open the report in your browser:

```text
target/pit-reports/index.html
```

---