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
- `packages.war`: the complete `artifactory.war` from the same build, the input of the minimal image.
- `packages.oci`: the minimal from-scratch OCI image, `lib.mkImage` without plugins (see "Minimal OCI image"
  below), as `containercontainer-<version>.oci.tar` with zstd-compressed layers, plus `layers.tsv` with each
  layer's uncompressed and compressed size. `packages.oci-example` is the same image with the public example
  plugins (the OCI registry and echo-realm), which `docker/it/run-min-it.sh` tests.
- `packages.release-oci`: the image tar, `containercontainer-<version>.oci.json` (its sha256, size in bytes,
  manifest digest and layer sizes) and a `SHA256SUMS` line for the tar.
- `lib.mkImage { pkgs, plugins ? [ ], extraConfig ? { }, tag ? version }`:
  builds that image for `pkgs`' system, with the given plugin files built in.
- `packages.release` (the default package): `containercontainer-<version>-jars.tar.gz` and `SHA256SUMS`. The
  tarball holds the jars and manifest, `LICENSE`, `plugins/wasm-abi/abi-v1.schema.json` and a `NOTICE` naming the
  source revision and the AGPL source offer (this repository at the release tag). It is packed deterministically:
  entries sorted, mtime set to the commit time, owner and group 0, normalised modes, gzip without a timestamp.
- `lib.buildWasmPlugin { pkgs, src, cargoLock, pname, ... }`: builds a Rust crate for `wasm32-wasip1` and outputs
  `<pname>.wasm` (and `<pname>.wasm.yaml` when `manifest` is given). Its check phase validates the module and fails
  the build if it imports anything outside `wasi_snapshot_preview1`. `nix/tests/wasm-hello` is the crate it is
  tested with.
- `checks`: the jar set against the stock listing, deterministic packing of the release, the `wasm-hello` module
  answering ABI v1 envelopes under wasmtime with schema-valid replies, the imports check rejecting a module, the
  structure of the minimal image with and without plugins (including that it carries no Access configuration and
  no default administrator credential, and that an image with `ACCESS_URL` set is rejected), its Java runtime's
  modules against `jdeps`, `mkImage`'s plugin rules, and the Helm chart (`helm lint` and renders, see below).
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
`nix flake check`, builds `.#release` and `.#release-oci`, rebuilds the jar tarball and the image to confirm
identical bytes, verifies the sums and publishes a GitHub Release with the tarball, the image archive, its `.oci.json`
and one `SHA256SUMS` covering all three. Running that workflow by hand does everything except publishing. The `nix`
workflow runs the flake checks and builds the jars, the release and the image assets on every push and pull
request.

A consumer (for example a private deployment repository) pins two things. It takes this flake as an input at the
release tag, for `lib.buildWasmPlugin`, `lib.mkImage` and the version, and it fetches the release asset with
`fetchurl` by its URL (`https://github.com/jvishnefske/ContainerContainer/releases/download/v<version>/containercontainer-<version>-jars.tar.gz`)
and the sha256 published in `SHA256SUMS`, so it never rebuilds the jars itself. Alternatively it can use
`packages.jars` from the flake input directly and let Nix build or substitute them. The image a consumer runs is
either the stock image with the jars overlaid (as `docker/build-overlay.sh` does) and its plugins added to the
plugins directory, or the minimal image below, built with `lib.mkImage` and its plugins.

### Minimal OCI image

`packages.oci` (and the release asset `containercontainer-<version>.oci.tar`) is built from scratch by the flake,
with no base image, and contains only: the fork's Artifactory webapp cut from `packages.war`, Apache Tomcat 11 from
nixpkgs with the Apache Derby and PostgreSQL JDBC drivers from Maven Central, an OpenJDK 25 runtime made with
`jlink` from nixpkgs' headless JDK (only the modules the webapp needs), a POSIX shell launcher on busybox, CA
certificates and a few base files (`/etc/passwd`, `/etc/group`). It runs as the non-root user `artifactory`
(uid and gid 1030, as in the stock image) and keeps all state in the volume `/var/opt/jfrog/artifactory`; Tomcat
serves port 8081 directly (Artifactory at `/artifactory`, the Docker `/v2/` path rewritten to it). The archive is
about the size of the old 6.x distribution zip; the layer table is in `layers.tsv` and in the release's `.oci.json`.

What it leaves out, compared with the stock image: the web UI (its static assets are stripped from the webapp, and
the UI also needs JFrog's closed frontend service), JFrog Access, the router, and the frontend, event, metadata,
observability, topology, jfconfig and jfbus services, along with every other file of JFrog's stock image. It also
leaves out a few jars the server does not load unless configured for them (the Oracle JDBC driver, the AWS RDS
helpers, the Kubernetes client), annotation processors the WAR packages by accident, and native libraries for
other platforms. JFrog Access is closed source and not redistributable; the image does not need it, because the
fork runs access embedded in the webapp (see "Embedded access and the first administrator" below). The image carries
no Access address, join key or default credential, and needs no other service.

To run it, load the archive (`docker load -i containercontainer-<version>.oci.tar`, or `podman load -i ...`; with
skopeo it is `oci-archive:containercontainer-<version>.oci.tar`), then start it with a volume, for example
`docker run -d -p 127.0.0.1:8081:8081 -v rt-var:/var/opt/jfrog/artifactory containercontainer:<version>`, and claim
it as described below. On first start the launcher writes `master.key` (from `JF_SHARED_SECURITY_MASTERKEY` when
set, otherwise random) and a `system.yaml` with the router disabled and the embedded Derby database allowed; edit
that file (or provide your own before the first start) to use PostgreSQL. The launcher passes the environment to
Artifactory unchanged, so `INITIAL_ADMIN_TOKEN_FILE` and `INITIAL_ADMIN_PASSWORD_FILE` work as described below.
`JAVA_OPTIONS` adds JVM options after the defaults (`-Xms512m -Xmx2g`), and is also how Artifactory system
properties such as `-Dartifactory.security.externalAdminGroups=ops` are set. The layers are zstd-compressed (media
type `application/vnd.oci.image.layer.v1.tar+zstd`), which needs Docker 23 or later, podman 4 or later, or a
containerd 1.5 or later based runtime; older clients cannot unpack them.

To build an image with plugins, call `lib.mkImage` from the flake input with the plugin files: Groovy plugins,
WASM modules with their `.wasm.yaml` manifests, or directories such as `lib.buildWasmPlugin` outputs, whose
top-level files are taken. For example, a deployment flake can set `packages.x86_64-linux.image` to
`containercontainer.lib.mkImage { pkgs = nixpkgs.legacyPackages.x86_64-linux; plugins = [ ./my-plugin.groovy
myWasmPlugin ]; }`. The plugins are installed read-only in the image's last layer and copied into
`var/etc/artifactory/plugins` on every start, so a volume mounted over the var directory still gets the image's
versions; plugins added to that directory by other means are left alone. A `.wasm` without its manifest, a file of
another kind and duplicate names fail the build. `extraConfig` is merged into the OCI image configuration (its
`Env` list is appended).

Layers are ordered so that what changes least comes first: base (busybox, glibc, CA certificates), Java runtime,
Tomcat, third-party jars, the fork's jars, configuration and launcher, plugins, and a small last layer with
`/etc/passwd`, links and the var directory. The image is built with nixpkgs' `dockerTools.streamLayeredImage` and
converted by `nix/image/to-oci.py` into an OCI layout whose layers are compressed with `zstd -19`; the archive also
carries a `docker save` style `manifest.json`, so `docker load` works with or without the containerd image store.
The creation time is the commit time and every step is deterministic, so a rebuild gives the same bytes.

## Embedded access and the first administrator

Users, groups, permission targets and access tokens are kept by an access implementation embedded in Artifactory
(`artifactory.access.embedded.enabled`, on by default), in tables of Artifactory's own database. Only Apache Derby
and PostgreSQL are supported in this mode, and only a single node. Access tokens are signed with a key that is
stored encrypted with `var/etc/security/master.key`, so that file must persist with the var directory: a server
that finds its signing key but not the master key that encrypted it refuses to start. Users and tokens of a stock
JFrog Access installation are not migrated.

There is no default administrator password. On first start the server writes a one-time setup token to
`var/etc/security/initial-admin.token` (mode 0600; in the images that is
`/var/opt/jfrog/artifactory/etc/security/initial-admin.token`), logs only that path, and answers every request with
503 except ping (`/artifactory/api/system/ping`), the readiness and liveness probes (`/artifactory/api/v1/system/readiness`
and `.../liveness`) and the claim endpoint. Whoever can read the token claims the server by sending
`POST /artifactory/api/setup/initial-admin` with the JSON body `{"token": "...", "password": "..."}`: the password
becomes the `admin` password (it must pass the password policy: at least 12 characters and not a common password),
the token file is deleted, and the token never works again. Every refusal (wrong or expired token, weak password,
already claimed) is the same 400, and repeated attempts are rate limited with 429. The token lives 24 hours by
default (`artifactory.security.initialAdmin.tokenTtlHours`); a restart before the claim keeps a valid token and
replaces an expired one (or one whose file was removed). For
example, with docker, read the token with `docker exec <container> cat
/var/opt/jfrog/artifactory/etc/security/initial-admin.token` and send the claim with curl to the published port.

Two environment variables change the start:

- `INITIAL_ADMIN_TOKEN_FILE` names a file holding the setup token to use instead of a generated one (at least 22
  characters, at most 1 KiB), for deployments that create the token up front, such as a Kubernetes Secret.
- `INITIAL_ADMIN_PASSWORD_FILE` names a file holding a temporary administrator password. The server then starts
  already claimed with that password, marked must-change: until the administrator changes it, every request of that
  account other than the password change is refused with 403, and it cannot create tokens or API keys. Use it only
  where the claim flow cannot be run, and remove the file afterwards.

`artifactory.security.externalAdminGroups` (an Artifactory system property, empty by default) lists groups of an
external realm, such as LDAP or a plugin realm, whose members are made administrators; it applies only once the
server is claimed. Embedded access seeds only the `admin` and `anonymous` users: unlike stock Access it creates no
`readers` group and no `Anything` permission target, so grant read access explicitly.

Setting `-Dartifactory.access.embedded.enabled=false` restores the stock behaviour of talking to an external JFrog
Access server (with its join key and router). Artifactory logs a warning on start, and none of the above applies in
that mode: there is no setup token, the server is not closed until claimed, and the administrator credential is
whatever that Access server has. It exists for running on the stock image, not for the minimal image, which has no
Access server.

## Helm chart

`deploy/helm/containercontainer` deploys the image as a single-replica StatefulSet with a Service on port 8081 and
a persistent volume claim for `/var/opt/jfrog/artifactory` (database, filestore, logs and `master.key`). The pod runs
as the image's non-root user (1030:1030, with `fsGroup` 1030 for the volume, no privilege escalation, all
capabilities dropped). The startup and liveness probes use ping and the readiness probe uses
`/api/v1/system/readiness`; all answer before the claim, so a fresh server becomes ready while still closed.

`values.yaml` holds no password, token or key. Secrets you create yourself are referenced by name:
`initialAdmin.tokenSecret` is mounted read-only at `/run/secrets/initial-admin/token` with `INITIAL_ADMIN_TOKEN_FILE`
pointing at it; `initialAdmin.temporaryPasswordSecret` is mounted read-only at
`/run/secrets/initial-admin-password/password` for `INITIAL_ADMIN_PASSWORD_FILE` (temporary, must be changed at
first login); `masterKeySecret` supplies `JF_SHARED_SECURITY_MASTERKEY` for the first start. Without a token Secret,
read the generated token with `kubectl exec` into the pod, as the chart's notes print. `security.externalAdminGroups`
is passed as `-Dartifactory.security.externalAdminGroups` in `JAVA_OPTIONS`, and `javaOptions` appends further JVM
options. For example: `helm install rt deploy/helm/containercontainer --set image.repository=<registry>/containercontainer
--set initialAdmin.tokenSecret.name=rt-setup-token`, then claim through `kubectl port-forward`. The chart refuses
`replicas` other than 1. The flake check `helm-chart` lints it and checks its renders.

## Integration tests

The scripts in `docker/it` start throwaway containers bound to 127.0.0.1 and claim each fresh server themselves
(`docker/it/initial-admin.sh`): they read the setup token from the container, claim with a random password kept only
in the script's shell and private work directory, and use that as the administrator. No administrator credential is
passed in. `run-min-it.sh` tests the minimal image alone, including the closed server before the claim, the claim
itself and persistence across a restart; `run-it.sh`, `run-oci-it.sh` and `run-wasm-it.sh` test the overlay image.
`run-it.sh` sets the stock phase's admin password through stock Access's `var/etc/access/bootstrap.creds`. With
embedded access the fork does not register with the stock image's router, so on the overlay image it is reached on
Tomcat's port 8081 directly, not the router's 8082.

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