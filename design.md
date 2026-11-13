# Artifactory OSS fork: Groovy plugins and plugin-defined repository types

Base: JFrog Artifactory OSS 7.161.26 published sources (AGPL-3.0), sha256
`b867422f3137c542b402c38a2ca2584396ef431b691ca70f038ff5cc3768802c`, committed
pristine as the first git commit. Deployment target: the stock
`releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26` image with the
fork's `artifactory-*.jar` files overlaid into its `WEB-INF/lib`.

## Background

OSS ships only `PluginsAddonDefaultImpl`, a no-op stub. The Groovy plugin
engine, its DSL and the `papi` service implementations (`Repositories`,
`Searches`, `Security`) live in the closed Pro addon. `RepoType` is a closed
enum referenced in roughly 400 files, so it is not replaced. A plugin package
type is a `Generic` repository whose `packageTypeConfig` map carries the
reserved key `pluginPackageType`. `Generic` is already the fallback for unknown
types everywhere, and `packageTypeConfig` already persists, diffs and
replicates.

## Requirements

Each item is checked only when a named test demonstrates it.

### Build and deployment

- [x] B1. The fork builds the WAR from source with JDK 25 and Maven, resolving
      dependencies only from Maven Central and JFrog's public `jfrog-jars`
      repository.
      Shown by a full reactor `mvn install` in `maven:3.9-eclipse-temurin-25`
      with only those two repositories configured (all 34 modules build).
- [x] B2. The fork's jars, overlaid on the stock 7.161.26 image, start
      Artifactory with existing data and no schema migration.
      Evidence: `docker/it/run-it.sh` on 2026-11-11 against
      `releases-docker.jfrog.io/jfrog/artifactory-oss@sha256:d103c0464d33645acdcbfbb6fc61310d7faf14c9893975cbee562487eaeac1d1`
      (overlay built by `docker/build-overlay.sh`: 18 `artifactory-*-7.161.15.jar`
      replaced, `groovy-4.0.32.jar` added, the version the WAR resolves through
      the Spring Boot BOM). The stock image writes a repository and a file, then
      the overlay starts on the same volume: `B2.stock-data-written`,
      `B2.fork-starts-on-existing-data`, `B2.stock-data-readable`,
      `B2.no-schema-migration` (the only conversion logged is PRE_INIT
      7.161.15 to 7.161.15, which the stock image also logs). Both images use the
      embedded Derby database, which since 7.161 needs the stock opt-in
      `JF_SHARED_DATABASE_ALLOWNONPOSTGRESQL=true`.
- [x] B3. With no plugins present the fork behaves as stock OSS: the existing
      unit test suite of every changed module passes.
      Full suites on 2026-11-11: backend/core 10435 run 0 failed, web/rest 1205
      run 0 failed, web/rest-ui 3068 run 0 failed (3 skipped, as upstream).

### Nix flake and binary release

The flake (`flake.nix`, `nix/`) builds the binary release that downstream deployments consume; see the README
section "Binary release and Nix".

- [x] N1. `nix build .#jars` builds the fork's jars purely: Maven dependencies come only from a fixed-output
      derivation (hash in `nix/mvn-hash.txt`, recomputed by `nix/update-mvn-hash.sh`) resolving from Maven Central
      and `jfrog-jars`, and the build itself runs offline in the sandbox. The jar set is the one
      `docker/build-overlay.sh` installs, chosen against the committed stock listing `nix/stock-lib.txt`, with a
      manifest of names, roles, sha256s and the source revision.
      Evidence (2026-11-12, x86_64-linux, nixpkgs nixos-26.05 `ac588ef`, `jdk25` 25.0.5, Maven 3.9.12): the
      dependency derivation (484 MB, 4100+ files) fetched twice from scratch with the same hash, after pinning the
      two plugins the parent POMs left versionless (`maven-pmd-plugin`, `develocity-maven-extension`), so it holds
      no `maven-metadata` files; `nix build .#jars` then builds offline in about 3 minutes, and two
      `nix build .#jarsBuild --rebuild` runs reproduced it bit for bit (one earlier rebuild, before the pins, did
      not, and was not kept for diffing). `checks.jars-vs-stock` passes: 18 jars replaced, `groovy-4.0.32.jar`
      added, sums match the manifest. 16 of the 18 jars are entry-for-entry identical (CRC) to a conventional
      `mvn install` WAR from 2026-11-11; core and web-application differ only in classes changed after that build.
- [x] N2. `nix build .#release` produces `containercontainer-<version>-jars.tar.gz` (jars, manifest, `LICENSE`, the
      ABI schema, a `NOTICE` with the source revision and AGPL source offer) and `SHA256SUMS`; the version comes only
      from `nix/version.txt`, and packing is deterministic, so a rebuild yields identical bytes.
      Evidence: `checks.release-deterministic` (packs twice from inputs with different mtimes, compares bytes with
      the package, runs `sha256sum -c SHA256SUMS`, requires owner 0/0 on every entry) passes, and
      `nix build .#release --rebuild` reports identical output.
- [x] N3. The flake builds a minimal from-scratch OCI image (no stock base image) from Nix-built parts, with a
      `plugins` argument, attached to releases as an additional asset.
      As built: `lib.mkImage { pkgs, plugins, extraConfig, tag }` (`nix/image.nix`) and
      `packages.oci` (no plugins): `dockerTools.streamLayeredImage` without `fromImage`, layered by an explicit
      pipeline (base, JRE, Tomcat, third-party jars, fork jars, config and launcher, plugins, customisation), then
      `nix/image/to-oci.py` writes an OCI layout with `zstd -19` layers
      (`application/vnd.oci.image.layer.v1.tar+zstd`) plus a `docker save` style `manifest.json`. Creation time is
      the commit time. `packages.release-oci` holds the archive, its `.oci.json` (sha256, size, manifest digest,
      per-layer sizes) and a `SHA256SUMS` line; `release.yml` builds it, rebuilds `.#oci` for identical bytes and
      publishes both assets under one `SHA256SUMS`. Plugins (Groovy, `.wasm` with `.wasm.yaml`) are installed
      read-only and copied into `var/etc/artifactory/plugins` by the launcher on every start.
      Evidence (2026-11-12, x86_64-linux): `packages.oci` is 128,471,040 bytes (597 MB of uncompressed layers);
      `skopeo inspect --raw oci-archive:` lists eight `tar+zstd` layers, `docker load` (Docker 29, overlay2 storage)
      loads it; `nix build .#oci --rebuild` reproduces it bit for bit (and the webapp, JRE, Tomcat and config
      inputs likewise); `checks.image-structure` and `checks.image-example-structure` (blob digests, diff_ids,
      media types, layer order) and `checks.image-plugin-rules` (a `.wasm` without manifest fails the build) pass;
      `docker/it/run-min-it.sh` on `packages.oci-example` (the OCI registry and echo-realm plugins), the container
      alone with access embedded (EA1): no Access service, sidecar, join key or second container. On 2026-11-13,
      22 passed 0 failed, among them `N3.ping`, `IA1.N3.admin-login-after-claim-default-password-401`,
      `N3.yaml-patch-creates-generic-repository`, `N3.deploy-download-round-trip`, `N3.token-mint-and-bearer-use`,
      `N3.oci-registry-plugin-loaded`, `N3.v2-401-anonymous-200-admin`, `N3.oci-push-pull-round-trip`,
      `N3.echo-realm-wasm-plugin-loaded`, `N3.echo-realm-bearer-login-runs-execution`,
      `N6.N7.runs-as-non-root-without-access-config` and `N6.no-web-ui`; the administrator is the one created by
      the claim (IA4), since the image has no default credential. An earlier run (2026-11-12, 12 passed) used the
      stock image's Access webapp as a throwaway sidecar; embedded access replaced it. Publishing the asset waits
      for N5's first run.
- [x] N4. `lib.buildWasmPlugin` builds a Rust crate for `wasm32-wasip1` offline from its `Cargo.lock` and fails the
      build when the module imports anything outside `wasi_snapshot_preview1`.
      Evidence: `checks.wasm-hello` builds `nix/tests/wasm-hello` (imports: five `wasi_snapshot_preview1`
      functions), runs it under wasmtime for an execution, a realm and a malformed envelope, and validates the
      three replies against the `Reply` definition of `abi-v1.schema.json`; `checks.wasm-imports-check-rejects`
      shows the same module failing the build when only `env` imports are allowed.
- [ ] N5. Pushing tag `v<version>` runs `.github/workflows/release.yml`: tag and `nix/version.txt` must agree, all
      flake checks pass, the release and the minimal image are rebuilt bit for bit and their sums verified, and a
      GitHub Release carries the tarball, the image archive (`.oci.tar`) with its `.oci.json`, and one `SHA256SUMS`
      covering them; a manual run is a dry run that publishes nothing. Only `GITHUB_TOKEN` is used, with
      `contents: write` in the publishing job alone.
      Not yet checked: the workflow has not run (no tag pushed). Its steps were run locally: `nix flake check`,
      `nix build .#release`, `nix build .#release --rebuild`, `nix build .#release-oci`, `nix build .#oci --rebuild`,
      `sha256sum -c SHA256SUMS`.

- [x] N6. The minimal image contains only an allowlist: the fork's webapp from `packages.war`, Tomcat 11 from nixpkgs
      (`tomcat11` 11.0.24; the stock image has 11.0.25) cut to the stock Tomcat's jar set, Apache Derby 10.17.1.0
      and PostgreSQL JDBC 42.7.13 from Maven Central (hashes equal to the stock image's copies), a jlink'd runtime of
      nixpkgs' headless OpenJDK 25 with the modules of `nix/image/jre-modules.txt`, busybox, CA certificates, the
      launcher and base files. Nothing is taken from JFrog's stock image: no Access, router, frontend, event,
      metadata, observability, topology, jfconfig, jfbus or other JFrog service, no `jfrog-tomcat-lib.jar`, and no
      web UI. It runs as uid and gid 1030 with the var directory as a volume. Left out of the WAR's jar set, each
      checked by the live test: the web UI assets in `artifactory-web-application` (152 MB of 5208 entries), the
      Oracle JDBC driver, the AWS RDS helpers, the Kubernetes client (`io.kubernetes:client-java`, `-api`,
      `-proto`; JFrog's `org.jfrog.worker:client-java` is needed and kept), Lombok and the MapStruct processor, and
      native libraries for other platforms inside zstd-jni, JNA, grpc-netty-shaded and byte-buddy-agent; unsigned
      jars are re-stored uncompressed for the zstd layer compression.
      Evidence: `checks.image-structure` and `checks.image-example-structure` (`nix/image/check-image.py`) require
      the exact jar set (the WAR's kept jars, Tomcat's, the drivers and the JRE's `jrt-fs.jar`; 457 jars), ELF
      files only under the closure of the JRE and busybox (339 files), no path component naming a JFrog service, no
      `.war`, `jfrog-tomcat-lib` or `access-server` file, the expected files, user `1030:1030` and the var volume
      owned by it; `checks.jre-modules` runs jdeps over all 457 jars and fails on a needed module the runtime lacks
      (excluded on purpose: `jdk.jdi`, only javassist's HotSwapper, and `jdk.incubator.vector`, Lucene's opt-in
      vector code); the runtime is 101 MB, has no reference to the JDK it came from (`disallowedReferences`) and
      none to ALSA. Live: `N6.N7.runs-as-non-root-without-access-config`, `N6.no-web-ui` and every N3 assertion above,
      with WASM running in that runtime (W7).
- [x] N7. The image carries no JFrog Access configuration and no default credential: its config sets no `ACCESS_*`
      variable, the launcher passes no Access URL override or join key, and no file or jar entry the check scans
      holds a default administrator credential. The image passes `INITIAL_ADMIN_TOKEN_FILE` and
      `INITIAL_ADMIN_PASSWORD_FILE` through unchanged (IA2, IA5).
      Evidence (2026-11-13): `checks.image-structure` and `checks.image-example-structure` print "no Access
      configuration ... ok" (`nix/image/check-image.py`, `check_no_access`); `checks.image-no-access-rejects` builds
      an image with `ACCESS_URL` set and requires the structure check to fail naming it; live
      `N6.N7.runs-as-non-root-without-access-config` (no `ACCESS_*` variable in the loaded image's config).
- [x] N8. A Helm chart (`deploy/helm/containercontainer`) deploys the image as one replica (EA6) with a persistent
      var volume, probes on ping and readiness, and no password, token or key in its values: the setup token
      (`INITIAL_ADMIN_TOKEN_FILE`) and the optional temporary administrator password (`INITIAL_ADMIN_PASSWORD_FILE`,
      IA5) come only from operator Secrets mounted read-only, the master key from a Secret reference, and
      `artifactory.security.externalAdminGroups` (IA7) from a value.
      Evidence: `checks.helm-chart` (`nix/checks/helm-chart.nix`): `helm lint --strict`; the default render is a
      single-replica StatefulSet with the var PVC, non-root 1030, both probes and no initial-admin environment or
      Secret; the render with every option has both read-only Secret mounts and the external admin groups;
      `replicas=2` fails to render; `values.yaml` and every render hold no password value or default credential.
      Not exercised on a live cluster.

### Groovy plugin engine

- [x] G1. On startup, every `*.groovy` file in `ArtifactoryHome.getPluginsDir()`
      (`$JFROG_HOME/artifactory/var/etc/artifactory/plugins`) is compiled and
      loaded. A plugin that fails to compile is reported in plugin status and
      does not stop startup or other plugins.
      Evidence (backend/core: `org.artifactory.addon.plugin.groovy.GroovyPluginEngineTest`):
      `#badScriptsAreReportedAndDoNotBlockOtherPlugins`,
      `#startupLoadsPluginsThroughContextReadiness`,
      `#missingPluginsDirectoryLoadsNothing`. Startup is exercised through
      `GroovyPluginsAddon.onContextReady()`, not a booted server.
- [x] G2. `POST /api/plugins/reload` reloads all plugins without a restart and
      replaces previously registered hooks and package types.
      Evidence: backend/core: `org.artifactory.addon.plugin.groovy.GroovyPluginEngineTest#reloadReplacesHooksAndPackageTypes`,
      `#pluginThatStopsCompilingLosesItsPackageTypes`,
      `#packageTypeMovedBetweenPluginsInOneReloadIsAccepted`; web/rest: `org.artifactory.rest.resource.plugin.GroovyPluginsResourceTest#reloadLoadsPluginsAndReportsEachFile`.
- [x] G3. The DSL supports `download { beforeDownloadRequest, altResponse,
      afterDownload }`, `storage { beforeCreate, afterCreate, beforeDelete,
      afterDelete }` and `executions { name { params -> } }`, bound to the
      existing `PluginAction` interfaces in `backend/core/.../addon/plugin`.
      Evidence (backend/core: `org.artifactory.addon.plugin.groovy.PluginDslBindingTest`):
      `#downloadBeforeDownloadRequestSetsModifiedPathOnDownloadCtx`,
      `#downloadAltResponseSetsStatusAndMessageOnResponseCtx`,
      `#downloadAfterDownloadReceivesTheResponse`,
      `#storageHooksFireForCreateAndDelete`,
      `#executionsBlockDeclaresANamedExecution`,
      `#hooksBindToTheCatalogActionTypes`. The `upload { beforeUploadRequest }`
      block and the other `PluginAction` hooks listed in `HookCatalog` are
      also bound.
- [x] G4. `POST /api/plugins/execute/{name}` runs a named execution, admin-only
      by default, and returns its status and message.
      Evidence: web/rest: `org.artifactory.rest.resource.plugin.GroovyPluginsResourceTest#executeRunsTheNamedExecutionAndReturnsItsStatusAndMessage`,
      `#executeIsAdminOnlyUnlessTheExecutionAllowsTheCaller`; backend/core: `org.artifactory.addon.plugin.groovy.PluginExecutionTest`
      (`#nonAdminIsRefusedByDefault`, `#declaredUserMayRun`,
      `#declaredGroupMemberMayRun`, `#cancelledExecutionReturnsTheCancelStatus`).
- [x] G5. `GET /api/plugins` lists loaded plugins and their hooks.
      Evidence: web/rest: `org.artifactory.rest.resource.plugin.GroovyPluginsResourceTest#listingShowsExecutionsAndPluginsWithHooks`;
      backend/core: `org.artifactory.addon.plugin.groovy.PluginListingTest#listsPluginsHooksExecutionsAndFailures`.
- [x] G6. Plugin scripts see `repositories`, `log` and `security` bindings.
      `repositories` supports at least `getFileInfo`, `getContent`, `deploy`,
      `delete`, `exists`, `getProperties` and `setProperty`.
      Evidence: backend/core: `org.artifactory.addon.plugin.groovy.PluginBindingsTest#scriptUsesEveryRepositoriesMethodAndSecurityAndLog`,
      `#bindingsMapHoldsTheThreeVariables`; `getChildren` (used by the example
      plugin) by `#getChildrenDelegatesToTheRepositoryService`. Besides what P4 adds, `security` exposes
      `currentUsername`, `admin` and `anonymous`.
- [x] G7. An exception thrown inside a hook is logged with the plugin name and
      does not fail the request, except when the hook explicitly sets an error
      status (for example `CancelException` in `beforeCreate`).
      Evidence: backend/core: `org.artifactory.addon.plugin.groovy.GroovyPluginEngineTest#hookExceptionIsReportedWithPluginNameAndDoesNotFailTheCall`,
      `#cancelExceptionInBeforeCreateFailsTheOperation`;
      `PackageTypeDslTest#downloadHandlerFailureIsReportedAndDeclines`.

### Plugin-defined repository types

- [x] R1. A plugin declares `packageType('name') { displayName, layout,
      download {}, upload {}, index {} }`. Names are unique, lowercase
      `[a-z0-9-]+`, and must not collide with a `RepoType` value.
      Registry: invalid names, built-in collisions, duplicates within a
      batch and names owned by another plugin are rejected all or nothing
      (`PluginPackageTypeRegistryImplTest#invalidNameRejectsWholeBatch`,
      `#builtInNameCollisionRejected`, `#duplicateNameWithinBatchRejected`,
      `#nameOwnedByAnotherPluginRejected`). DSL: the block builds a
      `PluginPackageType` with its handlers and registry rejections surface in
      plugin status (`org.artifactory.addon.plugin.groovy.PackageTypeDslTest`,
      `#invalidTypeNameSurfacesInPluginStatus`).
- [x] R2. Creating a local Generic repository whose package type configuration
      sets `pluginPackageType` to `name` succeeds only when `name` is
      registered. Otherwise it returns 400 with a message naming the unknown type.
      In OSS the public repository API (`PUT /api/repositories/{key}`) is a Pro
      feature (`RestAddonDefaultImpl` throws `MissingRestAddonException`, 400),
      so the OSS create path is the YAML configuration API,
      `PATCH /api/system/configuration`, whose `localRepositories` entries of
      `type: generic` accept `pluginPackageType`
      (`RepoConfigMigrationPatchUtilsTest#testBuildLocalPackageTypeConfigGenericCarriesPluginPackageType`).
      Evidence: `docker/it/run-it.sh` (2026-11-11, image digest under B2):
      `R2.unknown-type-rejected-400-naming-it`, `R2.unknown-type-not-stored`,
      `R2.registered-type-accepted`. The save-time validator and the UI REST layer
      are covered by `PluginPackageTypeRepoConfigValidatorTest#createWithUnknownTypeRejectedWith400NamingType`,
      `#createWithRegisteredTypeAccepted` and
      `PluginPackageTypeRepoValidationTest#createWithUnknownTypeReturns400AndStoresNothing`.
- [x] R3. GET requests to a plugin-typed repository go through the type's
      `download` handler, which can serve stored content, a generated
      response (for example an index document) or a status code. When the
      handler declines, normal Generic handling applies.
      Evidence: `DownloadServiceImplPluginPackageTypeTest#handledMessageIsServedAsBody`,
      `#handledStreamIsServedAsBody`, `#handledErrorStatusIsSentAsError`,
      `#handledHeadSendsHeadResponse`, `#declinedFallsThroughToGeneric`,
      `#handlerExceptionFallsThroughToGeneric` and
      `#handledStreamWithoutSizeIsStreamedWithoutContentLength`. Live, with read
      permissions enforced before the handler: `docker/it/run-it.sh`
      `R5.R7.index-json-lists-a-1.0.tgz` (served by the handler) and
      `R3.anonymous-index-denied`.
- [x] R4. PUT requests to a plugin-typed repository go through the type's
      `upload` handler, which can reject the upload or rewrite the target path
      before storage.
      Evidence: `UploadServiceImplPluginPackageTypeTest#acceptLeavesRequestUnchanged`,
      `#rejectSendsStatusAndStoresNothing`, `#rewriteInSameRepoTranslatesTargetPath`
      and `#rewriteToOtherRepoRejectedWith400`. Live: `docker/it/run-it.sh`
      `R4.upload-tgz-201`, `R4.upload-txt-rejected-400`, `R4.rejected-file-not-stored`.
- [x] R5. Whenever the files of a local plugin-typed repository change, the
      type's `index` handler runs asynchronously after the change commits, so
      it can maintain metadata files such as an index. The events covered are
      a file deployed into the repository, copied into it or moved into it
      (the handler gets the destination path), and a file moved out of it or
      deleted from it, including each file of a deleted folder (the handler
      gets the removed path). A move within the repository reports both
      paths. Folder events and trash-can items are not reported, and the
      handler's own writes, moves and deletes in the repository do not
      trigger it again.
      Evidence: `PluginPackageTypeIndexInterceptorTest#indexRunsOnceAndNotForItsOwnWrites`,
      `#dispatchIsAsyncAfterCommit`, `#copyIntoPluginRepoIndexesDestination`,
      `#moveIntoPluginRepoIndexesDestination`,
      `#moveOutOfPluginRepoIndexesSourceWithRemovedPath`,
      `#moveWithinPluginRepoReportsRemovedAndNewPath`,
      `#deleteFromPluginRepoIndexesRemovedPath`,
      `#deleteThatIsPartOfAMoveIsLeftToTheMove`,
      `#folderCopyMoveDeleteAreIgnored`,
      `#handlersOwnMovesAndDeletesDoNotRetrigger` and `#affectedPathsDecision`. Live:
      `docker/it/run-it.sh` `R5.R7.index-json-lists-a-1.0.tgz`.
- [x] R6. If the plugin is removed, a repository of its type keeps working as
      plain Generic and its stored artifacts stay intact.
      Evidence: `DownloadServiceImplPluginPackageTypeTest#unregisteredTypeFallsThroughToGeneric`,
      `UploadServiceImplPluginPackageTypeTest#unregisteredTypeStoresAsGeneric`,
      `PluginPackageTypeIndexInterceptorTest#typeRemovedBeforeAsyncRunSkipsHandler`,
      `PluginPackageTypeResolverTest#unregisteredTypeServesAsGenericAndIsReportedOnce`,
      `PluginPackageTypeRepoConfigValidatorTest#importOfRepoWithUnknownTypeDoesNotFail`
      and `#updateKeepingTypeOfRemovedPluginAccepted`. Live: `docker/it/run-it.sh`
      `R6.reload-after-removal`, `R6.execution-gone`,
      `R6.stored-artifact-still-served`, `R6.plain-generic-upload-after-removal`.
- [x] R7. A worked example plugin ships under `plugins/examples/` and defines a
      simple package type with an index, covered by an integration test.
      `plugins/examples/simple-index.groovy` defines type `simpleidx` (accepts `.tgz`
      uploads and its own `index.json`, maintains and serves that index) and the
      admin-only POST execution
      `simpleidxReindex`. Evidence: `docker/it/run-it.sh` (2026-11-11, image
      digest under B2): `G5.plugins-lists-simpleidxReindex`,
      `G4.execute-wrong-method-405`, `R4.upload-tgz-201`,
      `R4.upload-txt-rejected-400`, `R5.R7.index-json-lists-a-1.0.tgz`,
      `R7.reindex-execution`; unit: `org.artifactory.addon.plugin.groovy.ExamplePluginTest`
      loads the shipped file.
- [x] R8. Editing a plugin-typed repository keeps its plugin package type
      unless the update itself sets or clears it. The UI repository editor
      has no field for the key and rebuilds `packageTypeConfig` from its
      model, so single-repository updates in `RepositoryConfigServiceImpl`
      carry the stored type over when the update does not mention the key; a
      blank or null value clears it. Bulk upserts and imports store the
      configuration as given.
      Evidence: `PluginPackageTypesTest#updateWithoutTheKeyKeepsStoredType`,
      `#updateSettingTheKeyWins`, `#updateWithBlankOrNullValueClearsTheKey`,
      `RepositoryConfigServiceImplTest#upsertWithoutPluginPackageTypeKeepsStoredType`,
      `#upsertChangingPluginPackageTypeIsHonoured`,
      `#upsertClearingPluginPackageTypeRemovesIt` and
      `FederationRoundTripTest#uiEditOmitsPluginPackageTypeAndBackendMergeKeepsIt`.

### Plugin HTTP API

A plugin package type can serve a complete HTTP API, so that a registry such
as OCI Distribution can be written purely as a plugin. The contract lives in
`backend/core/.../addon/plugin/pkgtype/` (`PluginApiHandler`,
`PluginApiRequest`, `PluginApiResponse`, `PluginApiMount`), the gate and
service in `pkgtype/impl/` (`PluginApiGate`, `PluginApiService`,
`PluginApiAuthenticationAdapter`), and the REST shell in
`web/rest/.../rest/resource/pkgapi/` (`DockerPluginApiResource`,
`PkgPluginApiResource`, `PluginApiEndpoint`).

- [x] A1. A type declares an optional `api` handler that takes an immutable
      request value and returns an immutable response value. The request has
      the upper-case method, repository key, the path after the key, decoded
      query parameters in order, headers keyed by lower-case name, the live
      body stream (empty when there is none) and its declared length (-1 when
      unknown), the caller's name, admin and anonymous flags, and a base URL.
      The response has a status of 100 to 599, headers and a body that is
      none, bytes or a stream with an optional size. Maps are unmodifiable
      copies, header names and values cannot carry line breaks, and the
      existing `PluginPackageType` constructor and the other handler types are
      unchanged.
      Evidence: `PluginApiContractTest#requestNormalisesMethodPathAndHeaderNamesAndCopiesMaps`,
      `#requestRejectsInvalidValues`, `#queryStringIsParsedInOrderAndDecoded`,
      `#readBodyIsBounded`, `#responseEnforcesStatusRangeAndImmutableHeaders`,
      `#responseBodies`, `#packageTypeApiIsOptionalAndTheOldConstructorStillWorks`.
- [x] A2. GET, HEAD, PUT, POST, PATCH and DELETE on
      `/api/docker/{repoKey}/{path}` and `/api/pkg/{repoKey}/{path}` reach the
      handler of the repository's type with the request as sent. The query
      string is parsed from the raw string, so no servlet parameter parsing can
      consume a body.
      Evidence (web/rest): `PluginApiResourceTest#everyMethodReachesTheHandlerWithItsRequest`,
      `#pkgPrefixServesTheSameApi`, `#resourcesDeclareEveryMethodOnTheirPaths`.
- [x] A3. Registry clients reach the API through `/v2/`. With a plugin type
      that has an API handler and no reverse proxy configured,
      `DockerInternalRewrite` uses the repository-path-prefix method, so
      `<host>/v2/<repoKey>/<rest>` is forwarded to
      `/api/docker/<repoKey>/v2/<rest>` for any host name (the stock default
      is the subdomain method, which maps dotted hosts to their first label
      and leaves `localhost` unrewritten). The ping `GET /v2/`, and `/v2`
      without the slash, answers 200 `{}` with
      `Docker-Distribution-API-Version: registry/2.0` to an authenticated
      caller, and 401 with `WWW-Authenticate: Basic realm="Artifactory Realm"`
      to an anonymous one unless anonymous access is enabled; without such a
      type, and for other single-segment paths the rewrite sends there (such
      as `/v2/_catalog`), it answers 404. Basic credentials authenticate
      requests under `/api/docker/` once such a type exists: stock
      Artifactory leaves them to a package-handler adapter that OSS lacks, so
      without `PluginApiAuthenticationAdapter` every registry client would
      stay anonymous.
      Evidence: web/application: `DockerInternalRewritePluginApiTest#pingWithAndWithoutTrailingSlashLandsOnTheDockerPing`,
      `#withoutReverseProxyConfigurationAPluginApiSwitchesToRepoPathPrefix`,
      `#stockSubdomainDefaultWouldMisrouteDottedHostsAndSkipLocalhost`;
      web/rest: `PluginApiResourceTest#pingAnswersRegistryClients`; backend/core:
      `PluginApiGateTest#pingDecision`,
      `PluginApiServiceTest#pingNeedsARegisteredApiTypeAndChallengesAnonymousUnlessAnonymousAccessIsOn`,
      `PluginApiAuthenticationAdapterTest#acceptanceRule`,
      `#onlyTheDockerEndpointUnderTheContextPathIsHandled`,
      `#withoutAnApiTypeOrOutsideTheEndpointNothingIsHandledAndSecurityIsNotConsulted`,
      `#dockerRequestWithoutBasicHeaderIsNotHandled`,
      `ArtifactoryPackageBeansConfigTest#testPackageAuthenticationAdapter`.
- [x] A4. The request's base URL lets a handler build absolute `Location`
      URLs as the client sees them. A request forwarded from
      `/v2/<repoKey>/...` gets the platform URL and `urlFor` maps
      `v2/<rest>` to `<platform>/v2/<repoKey>/<rest>`; a direct call gets the
      Artifactory URL and maps to the `/api/docker/` or `/api/pkg/` path. Both
      URLs come from Artifactory's own resolution (`X-JFrog-Override-Base-Url`,
      the configured base URL, trusted `X-Forwarded-Host` and
      `X-Forwarded-Proto`, then the request's `Host`).
      Evidence: `PluginApiContractTest#mountIsDetectedFromTheForwardedUri`,
      `#baseUrlAndUrlForGiveTheUrlTheClientUses`; web/rest:
      `PluginApiResourceTest#forwardedV2RequestGetsThePlatformBaseUrl`.
- [x] A5. Permissions are enforced in Java before any plugin code runs, by a
      pure decision function. GET and HEAD need read, PUT, POST and PATCH
      deploy, DELETE delete, each checked on the repository root as the
      caller. A repository that is not local, or whose registered type has no
      API handler, answers 404 without a permission check; an anonymous caller
      without the permission gets 401 with the Basic challenge, an
      authenticated one 403; a blacked-out repository answers 404 after the
      permission check. Any other method is refused with 405.
      Evidence: `PluginApiGateTest` (all tests),
      `PluginApiServiceTest#permissionsAreCheckedOnTheRepositoryRootAsTheCaller`,
      `#anonymousCallerWithoutPermissionIsChallenged`,
      `#remoteMissingAndNonApiRepositoriesAre404WithoutPermissionChecks`,
      `#blackedOutRepositoryIsRefused`; web/rest:
      `PluginApiResourceTest#repositoryWithoutApiTypeIs404AndRunsNoPluginCode`,
      `#anonymousWithoutPermissionGets401WithTheBasicChallengeAndAuthenticatedGets403`,
      `#blackedOutRepositoryIsRefused`.
- [x] A6. Bodies stream in both directions. The handler gets the servlet
      input stream itself, unread. A stream response is copied in 64 KiB
      pieces, sent with `Content-Length` when its size is known, and closed
      after copying, unread for HEAD, and in any case at the end of the
      request. HEAD sends headers only, and a handler-set `Content-Length` on
      a response without a body is kept so HEAD can advertise a size.
      Evidence (web/rest): `PluginApiResourceTest#largeBodiesAreStreamedInBothDirections`
      (64 MiB generated response, never held in memory),
      `#headSendsHeadersOnlyAndClosesTheStream`,
      `#emptyBodyGetsZeroLengthAndBytesGetTheirLength`; backend/core:
      `PackageTypeApiDslTest#streamBodiesAreHandedThroughUnread`.
- [x] A7. The API fails closed. A handler that throws or returns nothing is
      answered with a generic 500 that carries no failure details, and the
      failure is logged with the type and plugin name. In the DSL, a closure
      that throws or returns something that is not a valid response is
      reported in plugin status and answered with the same 500, and a stream
      carried by a rejected value is closed. `CancelException` answers with
      its status (500 when it is not 4xx or 5xx).
      Evidence: `PluginApiServiceTest#handlerExceptionAndNullBecomeAGeneric500`;
      web/rest: `PluginApiResourceTest#handlerFailureIsAGeneric500`; backend/core:
      `PackageTypeApiDslTest#failuresAndInvalidReturnsFailClosedWith500AndAreReported`,
      `#invalidReturnClosesTheStreamItCarried`, `#cancelExceptionAnswersWithItsStatus`.
- [x] A8. The DSL accepts `api` inside `packageType('name') { }`; the closure
      gets the request and returns a `PluginApiResponse` or a map of
      `status`, `headers` and `body`, where the body is absent, bytes, a
      string sent as UTF-8, a stream with an optional size, or a content
      handle from `repositories`. `PluginApiRequest` and `PluginApiResponse`
      are default imports of plugin scripts.
      Evidence: `PackageTypeApiDslTest#recordReturnIsUsedAsIs`,
      `#mapReturnIsConverted`,
      `#resourceStreamHandleBodyIsStreamedWithItsSizeAndClosedWithTheStream`,
      `#typeWithoutApiBlockHasNoApi`, `#conversionRejectsWhatIsNotAResponse`.
- [x] A9. The `repositories` binding offers what a registry needs with bounded
      memory: `findFile` (size, SHA-256, SHA-1, MD5, modification time, empty
      for a missing path or a folder), `openContent` (a stream body of the
      file's size that releases its handle when closed), `deploy` (hands the
      caller's stream to the normal upload path, which streams it into the
      binary store), and `move` and `copy` within one repository (another
      repository is refused with 400). The binding runs as the requesting
      user, never as the system: writes keep their usual per-path permission
      checks, and reads check the user's read permission on every path,
      because permission targets can be scoped to paths inside a repository
      while the shell's pre-check (A5) covers only the repository. A path the
      user may not read looks absent (`findFile`, `openContent`, `exists`,
      left out of `getChildren`) or is refused (`getContent`,
      `getStringContent`, `getFileInfo`, `getProperties`), so a plugin cannot
      serve, inspect or re-stream content its caller could not download.
      Evidence: `PluginRepositoriesApiTest#unreadablePathsLookAbsentAndNeverReachTheContentService`,
      `#childrenTheCallerMayNotReadAreLeftOut`,
      `OciRegistryPluginTest#blobsAndManifestsUnderAnUnreadableNameAreUnknown`,
      `#mountFromAnUnreadableNameInTheSameRepositoryFallsBackToAnUploadSession`,
      `PluginRepositoriesApiTest#findFileReportsSizeAndStoredSha256`,
      `#findFileIsEmptyForMissingPathsAndFolders`,
      `#openContentStreamsWithTheFileSizeAndReleasesTheHandle`,
      `#deployHandsTheCallersStreamToTheUploadPath`,
      `#moveAndCopyStayWithinTheRepository`.
- [x] A10. With no plugin type that has an API handler, Artifactory behaves as
      before: `/v2` stays unrewritten, the rewrite method is the configured or
      stock one, `/api/docker/` ignores Basic credentials as stock OSS does,
      and the existing suites pass.
      Evidence: `DockerInternalRewriteTest` (upstream, unchanged),
      `DockerInternalRewritePluginApiTest#withoutReverseProxyConfigurationAPluginApiSwitchesToRepoPathPrefix`,
      `PluginApiAuthenticationAdapterTest#withoutAnApiTypeOrOutsideTheEndpointNothingIsHandledAndSecurityIsNotConsulted`.
      Full suites on 2026-11-11 with this change: backend/core 10520 run 0
      failed (9 skipped), web/rest 1220 run 0 failed, web/rest-ui 3069 run 0
      failed (3 skipped), web/application 537 run 0 failed.

### OCI registry plugin

Phase 2 writes an OCI Distribution Spec v1.1 registry as a Groovy plugin on
the API above: `plugins/oci/oci-registry.groovy`, package type
`oci-registry` (the built-in `docker` and `oci` names are taken), described
in `plugins/oci/README.md`. It needed no Java change. Unit evidence is
`org.artifactory.addon.plugin.groovy.OciRegistryPluginTest` (backend/core),
which loads the shipped file into the plugin engine with the real
`repositories` binding over an in-memory repository
(`InMemoryRepositoryService`) and asserts after every test that no other
repository was touched and no content handle was left open. Live evidence is
`docker/it/run-oci-it.sh`, run on 2026-11-12 (UTC) against the overlay of
`releases-docker.jfrog.io/jfrog/artifactory-oss@sha256:d103c0464d33645acdcbfbb6fc61310d7faf14c9893975cbee562487eaeac1d1`
with docker 29.1.3 (classic image store) and podman 4.9.3 as clients: 63
assertions passed, 0 failed.

- [x] O1. `docker push` and `docker pull` of a single-platform image against
      `<host>/v2/<repoKey>/<name>` work after `docker login` with Artifactory
      credentials; the same with `podman`.
      Evidence: `run-oci-it.sh` `O1.docker-login`, `O1.docker-push`,
      `O1.docker-pull-by-tag-same-image-id`,
      `O1.docker-pull-by-digest-same-image-id`, `O1.podman-login`,
      `O1.podman-push`, `O1.podman-pull-by-tag-same-image-id`,
      `O1.podman-pull-by-digest-same-image-id`.
- [x] O2. Monolithic upload: `POST .../blobs/uploads/?digest=` in one request,
      and `POST` followed by a single `PUT ...?digest=`; the stored blob's
      SHA-256 is verified against the digest and a mismatch is refused.
      Evidence: `run-oci-it.sh` `O2.monolithic-post-201`,
      `O2.blob-get-exact-content`, `O2.blob-head-size`, `O2.blob-range-206`,
      `O2.post-then-put-201`, `O2.digest-mismatch-400-DIGEST_INVALID`,
      `O2.mismatched-content-not-stored`,
      `O2.large-64MiB-blob-streamed-round-trip`; unit
      `OciRegistryPluginTest#monolithicPostWithDigestStoresTheVerifiedBlobAndServesItWithExactHeaders`,
      `#postThenSinglePutWithDigestCompletesTheUpload`,
      `#digestMismatchDeletesTheUploadAndAnswersDigestInvalid`,
      `#invalidAndUnsupportedDigestsAreRefusedBeforeAnyStorageAccess`,
      `#rangeRequestsServeExactSlices`,
      `#contentAtADigestPathThatDoesNotMatchItsDigestIsNeitherServedNorTrusted`,
      `#concurrentPushOfTheSameBlobResolvesToTheVerifiedBlob`. An upload body
      sent as `application/x-www-form-urlencoded` is consumed by servlet
      parameter parsing in Artifactory's filters before the plugin runs, so it
      is refused as a digest mismatch (fails closed; registry clients send
      `application/octet-stream`).
- [x] O3. Chunked upload: `POST`, one or more `PATCH` with `Content-Range`,
      final `PUT ...?digest=`, with `Location`, `Range`, `Docker-Upload-UUID`
      and `OCI-Chunk-Min-Length` headers; out-of-order chunks are refused with
      416; upload status by `GET` on the upload URL.
      Evidence: `run-oci-it.sh` `O3.post-202-session-headers`,
      `O3.out-of-order-chunk-416`, `O3.first-chunk-202-range`,
      `O3.upload-status-204`, `O3.second-chunk-202-range`,
      `O3.final-put-201`, `O3.assembled-blob-matches`,
      `O3.finished-session-gone-404`; unit
      `OciRegistryPluginTest#chunkedUploadChecksOrderReportsStatusAndAssemblesTheBlob`,
      `#streamedPatchOfUnknownLengthAndFinalChunkInPutAreAppended`,
      `#chunkWhoseLengthDisagreesWithItsRangeIsRefused`,
      `#uploadSessionsAreStrictUuidsOwnedByTheirCreator`.
- [x] O4. Cross-repository blob mount `POST .../blobs/uploads/?mount=&from=`
      (optional: falling back to 202 and a normal upload is conformant).
      Mounts are served only from names in the same Artifactory repository;
      any other `from` gets an upload session, because following it would
      bypass the other repository's read permission.
      Evidence: `run-oci-it.sh` `O4.mount-same-repository-201`,
      `O4.mounted-blob-served`,
      `isolation.cross-repository-mount-falls-back-to-202`; unit
      `OciRegistryPluginTest#mountFromANameInTheSameRepositoryCreatesTheBlob`,
      `#mountFromAnotherRepositoryNeverReadsItAndFallsBackToAnUploadSession`.
- [x] O5. Manifests by tag and by digest: `PUT`, `GET` and `HEAD`, with
      `Docker-Content-Digest`, the stored media type as `Content-Type`, and
      both OCI and Docker schema 2 manifests.
      Evidence: `run-oci-it.sh` `O5.get-by-tag-media-type-and-digest`,
      `O5.head-by-digest-exact-length`, `O5.put-by-tag-201`,
      `O5.podman-docker-schema2-push-served-as-schema2`,
      `O5.missing-blob-400-MANIFEST_BLOB_UNKNOWN`,
      `O5.oversize-manifest-413`; unit
      `OciRegistryPluginTest#ociManifestByTagAndDigestWithItsMediaTypeAndDigestHeaders`,
      `#manifestWithoutMediaTypeIsServedWithTheContentTypeItWasPushedWith`,
      `#manifestReferencingAMissingBlobOrABlobOfAnotherNameIsRefused`,
      `#invalidManifestsAreRefused`,
      `#oversizeManifestIsRefusedWith413WithoutBufferingIt`,
      `#retaggingNeedsOverwritePermissionAndReportsDenied`.
- [ ] O6. Image index and Docker manifest list (multi-platform images) push
      and pull, including `docker buildx` multi-platform push.
      Shown except for `docker buildx`, which is not installed on the test
      host (and the classic docker image store cannot push a multi-platform
      build without a BuildKit container): podman pushes a two-platform image
      as an OCI index and as a Docker manifest list, both are served with
      their media type and resolvable children, and podman pulls the arm64
      image from the index (`run-oci-it.sh` `O6.podman-multi-platform-push`,
      `O6.oci-list-served-with-its-media-type`,
      `O6.docker-list-served-with-its-media-type`,
      `O6.podman-pull-selects-arm64-from-index`; unit
      `OciRegistryPluginTest#dockerSchema2ManifestAndManifestListAndOciIndex`).
- [x] O7. Tag listing `GET .../tags/list` with `n` and `last` pagination and
      the `Link` header.
      Evidence: `run-oci-it.sh` `O7.first-page-and-link`,
      `O7.next-page-follows-link`, `O7.last-page-without-link`,
      `O7.full-list-sorted`; unit
      `OciRegistryPluginTest#tagListIsSortedAndPaginatedWithALinkHeader`.
- [x] O8. Deletion of manifests by digest, tags, and blobs, answering 202, and
      404 afterwards.
      Evidence: `run-oci-it.sh` `O8.delete-tag-202`, `O8.deleted-tag-404`,
      `O8.delete-manifest-by-digest-202`, `O8.deleted-manifest-404`,
      `O8.delete-blob-202`, `O8.deleted-blob-404`; unit
      `OciRegistryPluginTest#deleteByTagThenByDigestRemovesTagsAndAnswers404Afterwards`,
      `#deleteBlobAnswers202ThenThe404`.
- [x] O9. The OCI distribution-spec conformance suite passes its pull, push,
      content discovery and content management workflows against the
      plugin.
      Evidence: `run-oci-it.sh` `O9.conformance-pull-push-discovery-management`
      runs `ghcr.io/opencontainers/distribution-spec/conformance@sha256:287c9c1cb6511da2789b5c2dc3f0f8285c2fb73e031a634f86dccd52556a6ca5`
      (v1.1.0) with all four workflows enabled,
      `OCI_NAMESPACE=oci-local/conformance`,
      `OCI_CROSSMOUNT_NAMESPACE=oci-local/crossmount` (the first segment is
      the repository key) and `OCI_AUTOMATIC_CROSSMOUNT=false`: 76 of 80
      specs ran, 76 passed, 0 failed. The 4 skipped specs are alternatives
      the suite skips by configuration: "Get tag name from environment" and
      "Populate registry with test tags (no push)" (the suite pushes its own
      data), "Cross-mounting of nonexistent blob should yield session id"
      (runs only when the mount answered 202; it answered 201) and
      "Cross-mounting without from, and automatic content discovery enabled
      should return a 201" (automatic mounting is not implemented; its
      "disabled should return a 202" counterpart passed). The suite's content
      discovery workflow covers the referrers API, also unit tested by
      `OciRegistryPluginTest#referrersListManifestsWithASubjectAndFilterByArtifactType`.
- [x] O10. Permissions hold end to end: a read-only user can pull but not
      push (403), an anonymous client is challenged (401), and a user without
      delete permission cannot delete.
      Evidence: `run-oci-it.sh` `O10.anonymous-ping-challenged-401`,
      `O10.anonymous-pull-challenged-401`, `O10.reader-can-pull`,
      `O10.reader-podman-push-refused`, `O10.reader-push-403`,
      `O10.reader-delete-403`,
      `O10.deployer-can-push-without-delete-permission`,
      `O10.user-without-delete-permission-cannot-delete-403`. Isolation:
      `isolation.path-traversal-does-not-leak`,
      `isolation.reader-has-no-access-to-oci-other`,
      `isolation.direct-deploy-refused`; unit
      `OciRegistryPluginTest#invalidNamesAndPathTraversalAreRefusedBeforeAnyStorageAccess`,
      `#directHttpDeploysIntoTheRepositoryAreRefusedButInternalDeploysPass`.
      Users and permission targets are created through the UI REST API
      (`/artifactory/ui/users`, `/artifactory/ui/permissiontargets`), because
      the public security API is a Pro feature in OSS.

Out of scope for the plugin: garbage collection of unreferenced blobs and of
abandoned upload sessions, remote and virtual repositories of the type,
token authentication (Basic only), `/v2/_catalog`, and automatic mounting
without `from`.

## Plugin realms

Generic authentication support for Groovy plugins, in the spirit of JFrog
Pro's `realms {}` but with explicit, fail-closed results. Nothing here knows
about OIDC.

The contract lives in `backend/core/.../addon/plugin/groovy/`: the DSL
(`RealmsDelegate`, `PluginRealm`), the result values (`RealmResult`, built by
the `realm` binding `RealmResults`), fail-closed invocation (`RealmInvoker`),
the pure grant rules (`RealmGrantPolicy`) and routing rule
(`CredentialRouting`), the shell (`PluginRealmAuthenticator`, `RealmDirectory`,
the Spring bean `PluginRealmAuthenticationProvider`) and the resulting
`PluginRealmAuthentication`. Unit evidence is in backend/core; live evidence is
`docker/it/run-oidc-it.sh` (see the OIDC section), which exercises these
requirements through the OIDC plugin.

- [x] P1. A plugin declares `realms { name(autoCreateUsers: bool, realmPolicy:
      'additive'|'exclusive') { authenticate { username, credentials -> }
      bearer { token -> } userExists { username -> } } }`. `authenticate`
      sees Basic credentials, `bearer` sees the raw token of an
      `Authorization: Bearer` header; either is optional. Each returns an
      explicit result: authenticated as a username (with optional group names
      and an optional just-in-time user flag), not mine (pass to the next
      provider), or rejected. Any other return value, and any exception, counts
      as "not mine" for that realm and is reported in plugin status, so a
      broken plugin cannot let anyone in.
      Results are built with `realm.authenticated(name, groups:, jit:,
      expiresAt:, email:)`, `realm.notMine()` and `realm.rejected(reason)`; an
      unknown option of `authenticated` also counts as not mine. Defaults are
      `exclusive` and `autoCreateUsers: false`; unknown options, an unknown
      policy, a realm without the closures its policy needs and a realm name
      declared by an earlier plugin fail the load. Rejected is final (401):
      later realms and providers are not asked. An additive realm never
      authenticates: after another provider did, its `authenticate` is called
      with the user name and `null` credentials and may only add groups, and
      only to password and realm authentications (never to token
      authentications, whose scope would be lost).
      Evidence: `PluginRealmDslTest` (all 9 tests, among them
      `#realmsBlockDeclaresARealmWithOptionsAndClosures`,
      `#invalidDeclarationsFailTheLoadAndRegisterNoRealm`,
      `#anythingButARealmResultIsNotMineAndIsReported`,
      `#aRealmNameDeclaredByAnEarlierPluginFailsTheLaterOne`),
      `PluginRealmAuthenticationProviderTest#neverAdminAndRejectionsAreFinalWithAGenericMessage`,
      `#unclaimedTokensReturnEmptySoTheStockPathRuns`,
      `#additiveRealmsAddAllowedGroupsToPasswordLoginsOnly`,
      `#additiveAppliesOnlyToScopeFreeAuthenticationTypes`.
- [x] P2. One plugin realm authentication provider sits in the Spring provider
      chain before the internal database provider, so plugin realms can claim
      credentials before they are checked against stored passwords. Access
      tokens issued by Artifactory itself keep authenticating as before:
      bearer tokens whose issuer is this Artifactory's Access are never offered
      to plugin realms.
      As built: `pluginRealmAuthenticationProvider` comes after LDAP and Crowd
      and before Access user/password authentication and the database, so a
      JWT used as a password is not first sent to directories and Access as a
      failed login (account lockout). Ordinary passwords reach realms through
      that chain (internal and anonymous logins are not offered). Stock
      Artifactory treats every value that parses as a JWT as an Access token
      and verifies it before the chain, so tokens reach realms through
      `PasswordDecryptingManager`, which first offers `PluginRealmGateway` a
      Bearer token or Basic password that is a JWT whose `iss` is not a JFrog
      service id (`<type>@<id>[/users/<name>]`); Access tokens, reference
      tokens, API keys and non-JWTs keep their stock path. Evidence:
      `PluginRealmProviderOrderTest#pluginRealmProviderComesBeforeStoredPasswordProviders`,
      `CredentialRoutingTest` (all),
      `PluginRealmAuthenticationProviderTest#accessIssuedTokensAreNeverShownToPlugins`,
      `#foreignJwtsInOtherHeadersAreNotOffered`,
      `#basicWithAJwtPasswordReachesAuthenticateWithAnyUsername`,
      `#inTheChainOrdinaryPasswordsReachAuthenticateAndUnclaimedOnesFallThrough`,
      `PasswordDecryptingManagerTest#pluginRealmClaimIsReturnedBeforeTheStockTokenHandling`,
      `#unclaimedTokensKeepTheStockAccessTokenPath`; live: the exchanged Access
      token keeps working with the realm loaded (`I5.exchanged-token-works`).
- [x] P3. A realm can never grant admin. Groups a realm returns are applied
      only if they already exist, and never include groups that carry admin
      privileges. A just-in-time user is created in a realm named after the
      plugin realm, with no password, and is not admin.
      Enforced in Java by `RealmGrantPolicy`, not trusted to the plugin: a
      result naming an existing (effective) administrator is refused; a
      just-in-time user is `plugin:<realm>`, password disabled, not admin, and
      needs both `jit: true` and `autoCreateUsers: true`; `jit: true` naming an
      existing user of another realm is refused (no account takeover); names
      must be valid, at most 64 characters, not anonymous, and just-in-time
      names lower case. A realm user's stored groups are replaced by the
      granted ones on each login (so minted tokens carry them); other users get
      them for the request only. Successful results are cached only by the
      existing `AccessFilter` header/session cache, which now never reuses a
      `PluginRealmAuthentication` past its `expiresAt` or after its realm was
      reloaded or removed (`ExpiringAuthentication`); failures are never
      cached. Administrator rights for external users come only from the
      explicit Java-side `ExternalAdminMapping` (IA7): after the policy, a
      realm group listed in `artifactory.security.externalAdminGroups`
      (empty by default) makes the user admin for that authentication only,
      never stored, and only once the server is claimed.
      Evidence: `RealmGrantPolicyTest` (all 6),
      `PluginRealmAuthenticationProviderTest#bearerJwtCreatesAJustInTimeUserInThePluginRealmWithOnlyAllowedGroups`,
      `#aRealmUserHasItsStoredGroupsReplacedOnTheNextLogin`,
      `#anExistingUserGetsTheRealmGroupsForTheRequestOnly`,
      `#neverAdminAndRejectionsAreFinalWithAGenericMessage`,
      `#anAuthenticationIsRevokedWhenThePluginIsReloadedOrRemoved`,
      `ExpiringAuthenticationTest` (both), `ExternalAdminMappingTest` (all 3, among them
      `#grantsAdminOnlyWhenClaimedAndAGroupIsConfigured`, `#disabledNeverGrantsAdmin`); live
      `I4.jit-user-has-only-the-allowed-existing-non-admin-group`,
      `I7.without-the-plugin-jwts-are-refused`.
- [x] P4. The `security` binding gains, for realm and execution code only,
      `createAccessToken(expiresInSeconds)` for the current authenticated user
      (minted through Access, scope `applied-permissions/user`, refused for
      anonymous), plus `findUser(name)`.
      As built: `findUser` and `realmAuthentication` (which realm
      authenticated the request, and its expiry) work in realm and execution
      code; `createAccessToken` works in execution code only, since realm code
      runs before anyone is authenticated (the scope is a thread-local
      `PluginCallScope`). Tokens are not refreshable and live at most the
      requested time, one hour (`PluginSecurity.MAX_TOKEN_SECONDS`), an
      optional `notAfterEpochSeconds` and the realm credential's remaining
      lifetime; the answer is a `PluginAccessToken` value, never an exception.
      Evidence: `PluginSecurityRealmBindingTest` (all 6, among them
      `#executionsMintTokensForTheCallerWithTheUserScopeAndACappedLifetime`,
      `#mintingIsRefusedForAnonymousForExpiredRealmCredentialsAndWhenAccessFails`,
      `#otherPluginCodeCannotMintOrLookUpUsers`); live
      `I5.exchanged-token-is-a-user-scoped-access-token-within-the-cap`.
- [x] P5. Plugin executions can allow every authenticated (non-anonymous)
      caller, for example with `users: ['*']`, without opening them to
      anonymous callers.
      Evidence: `PluginSecurityRealmBindingTest#anyAuthenticatedCallerMayRunAStarExecutionButAnonymousMayNot`,
      `OidcFederationPluginTest#theExchangeRefusesCallersTheRealmDidNotAuthenticate`;
      the admin-only default is unchanged (`PluginExecutionTest`).
- [x] P6. With no plugin realm loaded, authentication behaves exactly as stock
      OSS, and the existing suites pass unchanged.
      With no realm the provider returns before reading the credential or any
      user data and the gateway claims nothing. Evidence:
      `PluginRealmAuthenticationProviderTest#withNoRealmLoadedNothingIsClaimedAndNoUserDataIsRead`,
      `PasswordDecryptingManagerTest#unclaimedTokensKeepTheStockAccessTokenPath`;
      live `P6.passwords-keep-working-with-the-realm-loaded`,
      `I7.without-the-plugin-passwords-still-work`; full suites on 2026-11-12 (UTC): backend/core 10611 run 0 failed (9 skipped),
      web/rest 1220 run 0 failed, web/rest-ui 3069 run 0 failed (3 skipped),
      web/application 537 run 0 failed.

## WASM plugin runtime

WebAssembly plugins run beside Groovy plugins on Endive (`run.endive`, Apache-2.0, pinned to 1.2.0), a pure-JVM
runtime. Unlike a Groovy plugin, which has full JVM access, a WASM module can reach only the host functions listed
here, within limits the administrator sets, so modules can come from parties who are not trusted with the JVM. WASM
hooks feed the same Java enforcement as Groovy ones: realm results go through P1-P6, executions through G4 and G7.

- [x] W1. A plugin is `<name>.wasm` plus a manifest `<name>.wasm.yaml` in the plugins directory, loaded at startup and
      by `POST /api/plugins/reload` together with Groovy plugins and reported in plugin status. The manifest declares
      the hooks the module implements (realms with name and policy, executions with users, groups and method), its
      capabilities (allowed HTTPS hosts), its limits (linear memory pages, per-call deadline, maximum request and
      response sizes) and an opaque `config` value handed to the module as JSON. A missing or invalid manifest, a
      module that fails validation or instantiation, or a module whose ABI version differs fails that plugin's load
      with a clear status and registers nothing.
      As built (`org.artifactory.addon.plugin.wasm`): `WasmPluginLoader` is a `PluginLoader` the Groovy engine calls
      on every reload, so WASM plugins share the snapshot, status, listing and reload. Manifest schema
      (`ManifestParser`; unknown keys fail the load): `abi: 1`; `realms: [{name, policy, autoCreateUsers, hooks}]`
      (`policy` must be `exclusive`, since ABI v1 cannot call `realm.authenticate` without a password as additive
      realms need; `hooks` is a non-empty subset of `authenticate`, `bearer`, default both); `executions: [{name,
      users, groups, httpMethod}]`; `capabilities: {httpGet: {hosts: [host[:port]]}}`; `limits` (`PluginLimits`,
      default [bounds]: memoryPages 256 [1-1024], stepTimeoutMs 2000 [10-30000], maxSteps 4 [1-16], maxStdinBytes
      and maxStdoutBytes 1 MiB [1 KiB-16 MiB], maxStateBytes 64 KiB [0-1 MiB], httpTimeoutMs 5000 [100-30000],
      maxHttpBodyBytes 256 KiB [0-4 MiB]); `config`. Plugin names are unique across Groovy and WASM files.
      Evidence (backend/core): `ManifestParserTest` (all 5), `WasmPluginLoaderTest` (all 4, among them
      `#modulesThatBreakTheRulesFailTheLoad`, `#missingOrInvalidManifestsFailTheLoad`),
      `WasmPluginEnforcementTest#wasmPluginsAreListedAndReportedLikeGroovyOnes`,
      `#pluginAndRealmNamesStayUniqueAcrossGroovyAndWasm`,
      `#removingThePluginRevokesItsAuthenticationsAndRefusesItsTokens`; live (`docker/it/run-wasm-it.sh`, 13 passed 0 failed on 2026-11-12)
      `W1.wasm-plugin-loaded-and-listed`, `W1.plugin-removed-and-reloaded`,
      `W1.after-removal-realm-tokens-are-refused-and-passwords-work`.
- [x] W2. ABI version 1 is a step protocol over WASI preview 1 standard streams, so a guest needs no exported
      allocator, no raw pointers and no custom imports (a Rust guest can be `#![forbid(unsafe_code)]`). The module is a
      WASI command. For each step the host instantiates it, writes one JSON envelope to stdin and runs `_start`: `abi`
      (1), `hook` (`realm.authenticate`, `realm.bearer` or `execution`), `request`, the manifest `config`, the plugin's
      `state`, `now` (epoch seconds), and `effects` (results of effects the previous step asked for, empty on the first
      step). The module writes one JSON reply to stdout: either `{"result": ...}` ending the call, or `{"effects":
      [...]}` asking the host to perform effects and run another step; either may carry a replacement `state`. Stderr
      lines go to the plugin log. Steps per call are capped (default 4). The exact shapes are
      `plugins/wasm-abi/abi-v1.schema.json`. Realm results map one to one onto `authenticated` / `notMine` /
      `rejected`; execution results carry status, headers and body.
      As built: `AbiCodec` (strict: one JSON value, no duplicate keys, depth at most 64, at most 8 effects per reply),
      `StepRules` (the pure decisions), `StepLoop` (the loop over a `Runner` and a `Performer`). Steps after the first
      see the latest state a step of the same call returned; it is committed only when the call finishes. Execution
      requests carry the request headers without `authorization`, `cookie` and other credential headers. Response
      headers are validated (tokens, no control characters, no framing, cookie or challenge headers) and sent by
      `PluginsResource` for hosted executions only (`HostedResponseCtx`). Evidence: `AbiCodecTest` (all 5),
      `StepLoopTest#effectsRoundTripIntoTheNextStepAndTheLatestStateIsCommitted`, `#theStepCapEndsTheCall`,
      `EndiveRuntimeTest#everyStepGetsAFreshInstanceAndStdinIsTheEnvelope`; web/rest:
      `HostedExecutionResourceTest#aHostedExecutionSendsItsStatusHeadersAndBody`; live
      `W2.bearer-login-runs-the-execution-as-the-realm-user`.
- [x] W3. Effects are the module's only way to affect anything outside its own memory, and the host performs each one
      only within the manifest's capabilities: `http_get` (HTTPS only, to hosts the manifest allows, no redirects,
      timeout, response-size cap) and, in execution calls only, `mint_access_token` (the same rules as P4). Plugin
      `state` is an opaque JSON value with a size cap, kept per plugin by the host and replaced whole when a reply
      carries a new one, so a module can cache (for example signing keys) across calls without shared mutable memory.
      WASI is offered with no preopened directories, no environment, no arguments, a wall clock, and random from the
      JVM's secure source. Any import outside WASI preview 1 fails the load. Cryptography runs inside the module.
      As built: `EffectPolicy` (pure: an absolute `https` URI whose host and port, 443 unless the entry names one,
      equal an allowlist entry, without user info or fragment, without client-owned request headers; mint only in
      `execution` calls; a denied effect fails the call), `HostEffects` (JDK `HttpClient`, redirects never, no proxy,
      one deadline over connect, headers and body, the exchange cancelled past the body cap; mint through
      `PluginSecurity.createAccessToken(expires_in, not_after)` in the caller's security context), `PluginState`
      (one `AtomicReference` per plugin, reset on reload), `ModuleRules` and `EndiveRuntime` (WASI options).
      Evidence: `EffectPolicyTest` (all 4), `ModuleRulesTest` (all 5),
      `StepLoopTest#aDisallowedEffectFailsTheCallAndNothingIsPerformed`, `#stateIsCommittedOnlyBySuccessfulCalls`,
      `WasmPluginEnforcementTest#anHttpGetEffectRoundTripsThroughTheHost`,
      `#executionsAreAuthorizedLikeGroovyOnesAndMintForTheCaller`,
      `#mintedTokensNeverOutliveTheRealmCredentialOrNotAfter`, `#stateSurvivesAcrossCallsAndCountsOnlyCompletedOnes`;
      live `W3.mint-effect-gives-a-user-scoped-token-within-the-requested-lifetime`, `W3.minted-token-works`,
      `W3.http-get-effect-reaches-the-allowlisted-https-host`.
- [ ] W4. Sandboxing is enforced by the host, not trusted to the module: Endive's interpreter by default (the
      build-time or runtime compiler only when the manifest opts in); linear memory capped by the manifest; each step
      runs on a bounded worker pool with a deadline, after which it is interrupted. A trap, non-zero exit, timeout,
      out-of-memory, oversized or malformed reply, too many steps, or a disallowed effect fails closed: a realm call
      counts as "not mine" and is reported, and an execution answers 500, and no `state` from a failed call is kept.
      Every step uses a fresh instance, so nothing survives in module memory between steps or calls.
      Status: open only for the compiler opt-in, which is not built: the manifest has no such key (an unknown key
      fails the load) and the `compiler` artifact is not a dependency, so every module runs on the interpreter.
      Everything else is shown: `EndiveRuntimeTest#trapNonZeroExitAndOversizedStdoutFail`,
      `#anInfiniteLoopIsInterruptedAtTheDeadlineAndTheWorkerIsFreedAgain`, `#aFullPoolRefusesInsteadOfWaiting`,
      `#memoryCannotGrowPastTheCap`, `StepLoopTest#malformedRepliesAndFailedStepsFailClosedWithoutState`,
      `#oversizedOutputStateAndEnvelopeFail`, `#theStepCapEndsTheCall`,
      `WasmPluginEnforcementTest#trapsAndLoopsFailClosedAsNotMineAndAreReported`,
      `#aFailingExecutionAnswers500AndIsReported`; live
      `W4.looping-and-trapping-modules-fail-closed-and-are-reported`, `W4.plugin-keeps-working-after-a-killed-step`.
      WASM realm results go through the unchanged P1-P6 code (`RealmHook.Hosted` hooks run through `RealmInvoker`
      and `PluginRealmAuthenticator`): `WasmPluginEnforcementTest#aWasmRealmCanNeverGrantAdminOrTakeOverAnotherRealmsUser`,
      `#aBearerTokenCreatesAJustInTimeUserInThePluginRealmWithOnlyExistingNonAdminGroups`; live
      `P3.wasm-realm-cannot-grant-admin-and-rejections-are-final`,
      `P3.jit-user-in-plugin-realm-with-only-existing-non-admin-groups`.
- [x] W5. A public example module (Rust, `wasm32-wasip1`, `#![forbid(unsafe_code)]`) under `plugins/wasm-examples/`
      exercises every hook, both effects and state. The Java tests run it from a committed build; CI rebuilds it from source and checks it matches.
      As built: `plugins/wasm-examples/echo-realm` (serde, serde_json, base64, locked by `Cargo.lock`; toolchain
      1.91.1), built by `build.sh` in `rust:1.91.1` pinned by digest, with remapped source and Cargo paths, no debug
      info, stripped symbols, one codegen unit and LTO. Fixture `backend/core/src/test/resources/wasm/echo-realm.wasm`,
      sha256 `298de0655b108cf8074d73ed88af248e8398abba39c34f02d094a01c0cf56ad5`: three clean builds gave the same
      hash, and the CI job `wasm-example` runs `build.sh --check`, which fails unless the rebuild is bit-identical.
      Evidence: `WasmPluginEnforcementTest#theCommittedModuleMatchesItsRecordedHash` and the tests above that run it.
- [x] W6. With no WASM plugins, behavior and test suites are unchanged, and Groovy plugins work as before.
      Evidence: full suites on 2026-11-12 with the CI excludes (`.github/surefire-excludes.txt`): backend/core
      10670 run 0 failed (9 skipped; 10611 before plus the 59 new WASM tests), web/rest 1222 run 0 failed (1220 plus
      2), web/rest-ui 3067 run 0 failed (3 skipped; the excluded EmptyTrashServiceLongRunTest accounts for the rest),
      web/application 538 run 0 failed; `HostedExecutionResourceTest#aFailedHostedExecutionIs500AndGroovyHeadersStayUnsent`
      (a Groovy execution's `headers` are still not sent); live `W6.passwords-keep-working-with-the-wasm-realm-loaded`.
      The one changed existing test is `RealmGrantPolicyTest`, whose realm fixture wraps its closure in
      `RealmHook.Script` now that realm hooks are `RealmHook` values.
- [x] W7. WASM runs entirely inside the JRE: only Endive's pure-Java execution (the interpreter, and, when a manifest
      opts in, its compilers to JVM bytecode). No JNI, no Panama FFM or other native code, and no native backend
      (Endive's experimental Cranelift-based Redline is excluded). A build check bans `run.endive` native backend
      artifacts and any dependency that ships native libraries for the WASM path, and a test confirms a module
      executes with native access disabled (`--illegal-native-access=deny` and no `--enable-native-access` for the
      WASM classes).
      As built: the enforcer execution `wasm-runtime-is-pure-java` in `backend/core/pom.xml` bans every `run.endive`
      artifact except `wasm`, `runtime`, `wasi`, `log` and `compiler` at 1.2.0, and all of `com.github.jnr` and
      `org.lwjgl`. `mvn dependency:tree` shows the four Endive artifacts used with no transitive dependencies, and the
      rule was seen to fail the build when an Endive artifact outside the list entered it. Evidence:
      `NativeAccessDeniedTest#theExampleModuleRunsWithNativeAccessDenied` (a child JVM started with
      `--illegal-native-access=deny` first sees `System.loadLibrary` refused, then runs the example module).

## Initial administrator bootstrap

Stock Artifactory, through JFrog Access, creates `admin` with the published password `password`. The fork ships no
default credential. A fresh installation starts unclaimed: the administrator account exists but has no password, so no
password can log in until an operator who can read a one-time setup secret sets one. That first claim is the forced
initial password reset. These requirements bind the fork's own security layer that replaces JFrog Access (see Embedded
access).

The bootstrap lives in `backend/core/.../security/access/embedded/` (pure rules in `core/`, the shell
`AdminBootstrapService` and `PasswordHasher` in `bootstrap/`), with `InitialAdminResource` in web/rest and
`InitialSetupFilter` and `MustChangePasswordGate` in web/application. Live evidence is `docker/it/run-min-it.sh`
(2026-11-13, 22 passed 0 failed) on the minimal image, which has no Access service; `docker/it/initial-admin.sh`
claims the server the same way for the other live tests.

- [x] IA1. No default credentials. On first start the built-in administrator (`admin`) is created with no password
      hash and the state `unclaimed`; no password, API key or token authenticates as it. Nothing in the image, the
      Helm values or the documentation contains a default password.
      As built: the schema seeds `admin` (and `anonymous`) with a NULL hash, and recreates `admin` the same way if it
      is deleted; a NULL hash never matches. `SecurityService.DEFAULT_ADMIN_PASSWORD`, `SecurityUtils.isDefaultAdmin`,
      the `access.creds` write and the onboarding probe for the old default password are gone. Evidence:
      `EmbeddedAccessFactoryTest#firstStartSeedsUsersEncryptsTheKeyAndOpensTheSetupToken`,
      `JdbcEmbeddedAccessStoreTest` (inherited from `AbstractEmbeddedAccessStoreTest`)
      `#schemaIsIdempotentAndSeedsPasswordlessAdmin` and `#adminIsReseededWhenAbsent`,
      `EmbeddedAccessAdaptersTest#nullHashNeverAuthenticates`, `PasswordHasherTest#nullHashNeverMatches`,
      `GetArtifactoryInitStatusServiceTest#executeReportsInitialSetupRequiredWithoutAnyPasswordProbe`; while unclaimed
      nothing but ping, the probes and the claim gets past `InitialSetupFilter` (IA3), so no API key or token is ever
      looked at. Image and Helm: N7 and N8. Live: `IA1.IA3.default-and-empty-password-503-before-claim`,
      `IA1.N3.admin-login-after-claim-default-password-401`. Documentation: on 2026-11-13 a search of `README.md`,
      `deploy/`, `nix/` and `docker/` for `admin:password` and `password=password` finds only the checks that forbid
      them and the live test that asserts they fail.
- [ ] IA2. First start generates a one-time setup token from the JVM's secure random source (at least 128 bits,
      URL-safe encoding) and writes it to `$JFROG_HOME/artifactory/var/etc/security/initial-admin.token`, owned by the
      service user with mode 0600. Only the file's path is logged, never the token. An operator may supply the token
      instead through a file named by `INITIAL_ADMIN_TOKEN_FILE` (for example a mounted Kubernetes Secret); then none
      is generated. The token expires after a configurable period (default 24 hours); an expired token is replaced by
      a new one on the next start, and the replacement is logged the same way.
      As built: 32 `SecureRandom` bytes, base64url without padding (43 characters), written as a 0600 temporary file
      and moved into place; only its SHA-256 is stored. The period is `security.initialAdmin.tokenTtlHours`. A
      supplied file must hold 22 to 1024 printable ASCII characters and at most 1 KiB. Evidence:
      `AdminBootstrapServiceTest#firstStartWritesOwnerOnlyTokenFileAndStoresOnlyItsHash`,
      `#restartWithValidTokenKeepsIt`, `#expiredTokenIsReplacedOnNextStart`, `#missingTokenFileIsReplacedOnNextStart`,
      `#suppliedTokenFileIsUsedAndNothingIsGenerated`, `#suppliedTokenReplacesAGeneratedOneAndDeletesItsFile`,
      `#oversizedEmptyOrMissingSuppliedFileIsRefused`, `#suppliedFileIsReadAsUtf8`;
      `BootstrapRulesTest#firstStartGenerates`, `#validGeneratedTokenIsKeptAndExpiredOneRegenerated`,
      `#suppliedTokenIsUsedInsteadOfGenerating`, `#sameSuppliedTokenIsKeptUntilItExpires`,
      `#withdrawnSuppliedTokenIsReplacedByAGeneratedOne`, `#malformedSuppliedTokenOrBadTtlIsRefused`,
      `#tokenFormatBounds`; live `IA2.setup-token-file-written-private` (mode 600, 43 characters, in a container
      running as uid 1030).
      Not yet checked: no test asserts what is logged. The live run's log has one WARN naming the file's path, the
      reason and the expiry, and no token, but that was read by hand, not asserted, and the replacement message was
      not looked at.
- [x] IA3. While unclaimed, every endpoint except `GET /artifactory/api/system/ping`, readiness and the claim endpoint
      answers 503 with a fixed "initial setup required" body. Anonymous access, plugins' executions and realms, and
      the OCI `/v2` API are unavailable until the claim, so an unclaimed server exposes nothing.
      As built: `InitialSetupFilter` sits in `web.xml` between `ArtifactoryProbesFilter` and `AccessFilter`; it lets
      through ping, the readiness and liveness probes and `POST /api/setup/initial-admin` only, and fails closed (503)
      when the bootstrap service is missing in embedded mode. Evidence: `InitialSetupFilterTest` (all, among them
      `#blocksEverythingElseBeforeTheClaim`, which covers `/v2/`, the Docker API and look-alikes of the ping path,
      `#letsPingProbesAndTheClaimThroughBeforeTheClaim`, `#passesEverythingThroughAfterTheClaimAndStopsAsking`,
      `#opensWhenTheClaimHappensWhileRunning`, `#failsClosedWhenTheServiceIsMissing`,
      `#embeddedOrUnknownModeStaysClosedWithoutService`, `#anonymousInterceptorAcceptsOnlyTheClaim`),
      `InitialAdminResourceTest#missingServiceAnswers503`; live `IA3.probes-answer-before-claim`,
      `IA1.IA3.default-and-empty-password-503-before-claim`, `IA3.arbitrary-endpoints-503-before-claim` (REST
      configuration, users, token mint, plugin list, repository deploy and listing, `/v2/`, the web app, a ping path
      with `..` and a path below the claim endpoint).
- [x] IA4. `POST /artifactory/api/setup/initial-admin` with the setup token and a new password claims the server: the
      token is compared in constant time, attempts are rate-limited per client address and in total, and the endpoint
      accepts requests only while unclaimed. The password must pass the policy in IA6. On success the password is
      stored only as a salted hash from a slow key-derivation function (PBKDF2-HMAC-SHA256 with at least 600,000
      iterations, or bcrypt with cost 12 or more; pure Java, no native code), the token is invalidated and its file
      deleted, the state becomes `claimed`, and an audit event records the claim with the client address. A second
      claim, a reused token or an expired token is refused with the same generic error.
      As built: SHA-256 of the token compared with `MessageDigest.isEqual`; 5 attempts per client and 30 in total per
      15 minutes (429), refused attempts never touch the store, and a valid token is never locked out by exhausted
      limits; the hash is `{bcrypt}` at cost 12 from spring-security-crypto (pure Java), and `{pbkdf2}` hashes at
      600,000 iterations are also accepted; hash, must-change flag and state are written in one transaction with a
      compare-and-swap on the stored state; the client address is the socket address (`X-Forwarded-For` is ignored). A
      token supplied through `INITIAL_ADMIN_TOKEN_FILE` is invalidated but its file, the operator's read-only Secret,
      is left in place. Evidence: `BootstrapRulesTest#correctTokenClaims`,
      `#secondClaimReusedTokenExpiredAndWrongTokenAllGetTheSameError`, `#constantTimeCompare`,
      `#claimedStaysClaimedWhateverIsSupplied`; `ClaimRateLimitTest` (all 7);
      `AdminBootstrapServiceTest#claimSetsHashDeletesGeneratedFileAndAudits`,
      `#claimWithSuppliedTokenLeavesTheOperatorsFile`, `#everyFailureIsTheSameGenericRefusal`,
      `#wrongTokenAttemptsAreRateLimitedPerClientAndGlobally`, `#validTokenIsNeverLockedOutByExhaustedLimits`,
      `#refusedAndRateLimitedAttemptsDoNotReadTheStore`, `#conflictingClaimIsRefused`; `InitialAdminResourceTest` (all
      7, among them `#successfulClaimAnswers200`, `#rateLimitedClaimAnswers429`, `#refusedClaimAnswersTheGeneric400`,
      `#forwardedHeaderIsIgnoredAndTheSocketAddressIsPassedOn`);
      `EmbeddedAccessFactoryTest#claimThroughTheStorePortSetsThePasswordAndStateTogether`,
      `#storePortSaveIsCompareAndSwap`; the key-derivation function:
      `PasswordHasherTest#encodesBcryptWithCost12AndVerifies`, `#saltsEachHash`,
      `#acceptsPbkdf2Sha256With600kIterations`. Live: `IA4.IA6.wrong-token-and-weak-password-400` (the same body for
      both), `IA4.claim-with-setup-token-200`, `IA4.token-file-deleted-after-claim`, `IA4.second-claim-400`,
      `IA4.EA7.still-claimed-after-restart`, and in `docker/it/run-it.sh` `IA4.EA17.fork-claimed-on-existing-data`.
- [ ] IA5. Forced password change: an account flag `mustChangePassword` is set on every account whose password was not
      chosen by its holder, namely an administrator password supplied by an operator (for example through a Helm
      Secret, which is then only a temporary password), a password set or reset by another administrator, and a
      password restored from a break-glass procedure. A login with such an account yields only the right to call the
      change-password endpoint; every other request answers 403 "password change required" until the holder sets a new
      password that differs from the temporary one. API keys and tokens cannot be created for such an account.
      As built: the flag is the `ea_users.must_change_password` column, seen by Artifactory as the custom datum
      `must_change_password` and `UserInfo.isMustChangePassword()`. `INITIAL_ADMIN_PASSWORD_FILE` claims the server on
      start with the flag set. `MustChangePasswordGate` (called from `AccessFilter`) allows only the change-password
      and logout endpoints. Evidence:
      `AdminBootstrapServiceTest#operatorSuppliedPasswordClaimsWithMustChangeAndNoTokenFile`,
      `#operatorSuppliedPasswordIsIgnoredOnceClaimed`, `#badOperatorSuppliedPasswordFileKeepsTheServerUnclaimed`;
      `SecurityServiceMustChangePasswordTest` (all 13, among them `#adminCreatingAnotherUserWithAPasswordSetsTheFlag`,
      `#adminUpdatingAnotherUsersPasswordSetsTheFlag`,
      `#adminResetWithoutValidationSetsTheFlagButAnonymousResetDoesNot`,
      `#holderChangingToADifferentPasswordClearsTheFlag`,
      `#holderReusingTheTemporaryPasswordIsRefusedAndKeepsTheFlag`, `#apiKeyIsRefusedForAFlaggedUser`);
      `MustChangePasswordRulesTest` (all 4); `MustChangePasswordGateTest` (all 10);
      `AccessServiceImplTest#createTokenRefusedForUserWhoMustChangePassword`;
      `PluginSecurityMustChangePasswordTest#flaggedCallerIsRefusedWithoutMinting`; `UserMapperMustChangePasswordTest`,
      `AccessUserGroupStoreMustChangePasswordTest`,
      `EmbeddedAccessAdaptersTest#mustChangePasswordDatumMapsToTheColumn`,
      `EmbeddedAccessContractTest#mustChangeFlagAndBulkPasswordExpiryRoundTrip`. In practice `docker/it/run-oci-it.sh`
      creates its users with a password and has each change it before use.
      Not yet checked: no break-glass procedure exists, so "a password restored from a break-glass procedure" has
      nothing to set the flag; and no live test asserts the 403 for a flagged login.
- [ ] IA6. Password policy: at least 12 characters, not equal to or containing the username, not in a bundled list of
      common and breached passwords, and checked without network access. The policy applies to the claim, to every
      password change and to administrator resets.
      As built: `PasswordPolicy` (pure, no network) behind `PasswordPolicyChecker`, implemented by
      `LocalPasswordPolicyChecker`; it is applied when a password is set, not at login, and fails closed when its list
      cannot be loaded. Evidence: `PasswordPolicyTest` (all 8, among them `#tooShort`, `#lengthCountsCodePoints`,
      `#equalToOrContainingUsernameIgnoringCase`, `#commonPasswordIgnoringCase`,
      `#bundledListLoadsAndRejectsObviousPasswords`); `SecurityServicePasswordPolicyTest` (all 5, among them
      `#administratorResetAndHolderChangeAreChecked`, `#checkerFailsClosedWithoutItsList`);
      `SecurityUtilsTest#testValidatePasswordPolicyDelegatesToTheCheckerWithTheUsername`,
      `#testValidatePasswordPolicyHasNoExemptions`, `#testValidatePasswordPolicyFailsClosedWithoutAChecker`;
      `EmbeddedAccessAdaptersTest#passwordPolicyIsApplied`; the claim:
      `AdminBootstrapServiceTest#everyFailureIsTheSameGenericRefusal` and live
      `IA4.IA6.wrong-token-and-weak-password-400`.
      Not yet checked: the bundled list (`embedded-access/common-passwords.txt`) is a self-authored list of 195
      obvious passwords, not a list of breached passwords. A larger list under a compatible licence (for example an
      MIT-licensed SecLists list) has to replace it.
- [ ] IA7. External identity does not bypass the bootstrap: plugin realms (including the WASM OIDC module) never grant
      administrator rights (P3), and mapping an external group to administrator is an explicit Java-side setting that
      takes effect only after the server is claimed. The local administrator remains as the break-glass account when
      the external identity provider is unavailable.
      As built: P3 holds for Groovy and WASM realms, and `ExternalAdminMapping` reads
      `artifactory.security.externalAdminGroups` (empty by default) and grants admin for one authentication only once
      the server is claimed; while unclaimed, `InitialSetupFilter` keeps realms from running at all (IA3).
      Evidence: `ExternalAdminMappingTest` (all 3, among them `#grantsAdminOnlyWhenClaimedAndAGroupIsConfigured`,
      `#disabledNeverGrantsAdmin`), P3's tests, live `P3.wasm-realm-cannot-grant-admin-and-rejections-are-final`.
      Not yet checked: no live test configures an external admin group and logs in through a realm before and after
      the claim, and none shows the local administrator logging in while the identity provider is unreachable.
- [x] IA8. Tested end to end: unit tests for token generation, expiry, constant-time comparison, rate limits, the
      policy and the state machine (unclaimed, claimed, must-change), and a live test that starts a fresh image,
      confirms `admin`/`password` and every other default fail, reads the token from the volume, claims the server,
      logs in, and confirms the token file is gone and a second claim is refused.
      Evidence: the unit tests cited under IA1 to IA6 (`BootstrapRulesTest`, `ClaimRateLimitTest`,
      `AdminBootstrapServiceTest`, `PasswordPolicyTest`, `PasswordHasherTest`, `MustChangePasswordRulesTest`,
      `InitialSetupFilterTest`, `InitialAdminResourceTest`); live `docker/it/run-min-it.sh` on 2026-11-13 against a
      fresh volume: `IA2.setup-token-file-written-private`, `IA3.probes-answer-before-claim`,
      `IA1.IA3.default-and-empty-password-503-before-claim`, `IA3.arbitrary-endpoints-503-before-claim`,
      `IA4.IA6.wrong-token-and-weak-password-400`, `IA4.claim-with-setup-token-200`,
      `IA4.token-file-deleted-after-claim`, `IA4.second-claim-400`,
      `IA1.N3.admin-login-after-claim-default-password-401`, and after a restart
      `IA4.EA7.still-claimed-after-restart`.

## Embedded access

JFrog Access (users, groups, permissions, tokens, platform configuration) is closed source with no redistribution
licence, so the fork does not use it. Artifactory talks to Access only through the
`org.jfrog.access.client.AccessClient` interfaces, which are public; the fork implements the part of them it calls, in
process, in `backend/core/.../security/access/embedded/`. The package is split as the rest of the fork: pure rules and
values in `core/` (`EaOutcome`, `ScopeGrammar`, `Revocability`, `TokenClaimsFactory`, `PasswordPolicy`,
`BootstrapRules`, `ClaimRateLimit`, `LastUpdatedClock`), the JDBC store in `store/`, the `AccessClient` adapters in
`client/`, the initial-admin bootstrap in `bootstrap/`, and `EmbeddedAccessFactory`, which `AccessServiceImpl` calls
in place of `AccessClientBootstrap`. Identity from an external provider comes through plugin realms (P1 to P6).
Embedded access is for fresh installations: it does not read data a stock Access server wrote.

- [x] EA1. Embedded access is the default (`artifactory.access.embedded.enabled`, default `true`): `AccessServiceImpl`
      installs an in-process `EmbeddedAccessClient` with no Access server, network hop, join key, Access admin token,
      router registration or JFrog wire format, stores the embedded certificate as the Access root certificate
      (`keys/root.crt`) so token verification stays local, and refuses the proxy token path to Access.
      Evidence: `AccessServiceImplEmbeddedTest#initInstallsTheEmbeddedClientAndPublishesTheBootstrapService`,
      `#failedCredentialsAreNotRetriedInEmbeddedMode`;
      `EmbeddedAccessFactoryTest#firstStartSeedsUsersEncryptsTheKeyAndOpensTheSetupToken`; live: every
      `docker/it/run-min-it.sh` check runs on the minimal image alone (N3, N7), among them
      `N6.N7.runs-as-non-root-without-access-config`.
- [x] EA2. Every Access client operation the fork calls is served. A committed inventory
      (`backend/core/src/test/resources/embedded-access/access-lite-operations.tsv`, 149 rows, no JFrog payloads)
      classes each as critical (startup or every request), feature, optional (a safe default answer), or unsupported,
      and the real Artifactory services work against the embedded client.
      Evidence: `AccessLiteOperationsCoverageTest#inventoryIsWellFormedAndFreeOfVendorPayloads`,
      `#criticalAndFeatureRowsAreImplementedAndUnsupportedRowsFailClosed`; `EmbeddedAccessContractTest` (all 4:
      `#usersAndGroupsRoundTripThroughArtifactoryServices`, `#mustChangeFlagAndBulkPasswordExpiryRoundTrip`,
      `#aclsFollowPermissionChangesAndAuthorizationSeesThem`, `#platformConfigRoundTrip`, driving the real
      `AccessUserGroupStoreService`, `AclStoreServiceImpl`, `AuthorizationServiceImpl` and
      `PlatformConfigServiceImpl`); `EmbeddedAccessAdaptersTest` (all 17).
- [x] EA3. Fail closed: every Access client surface or method that embedded access does not implement (among them
      certificates, the gRPC users, groups, system, permissions-v2 and proxy clients, imports, topology, proxies,
      vaults, locks and federation, and any method an adapter does not override) throws an `AccessClientException`
      ("not supported by embedded access: ..."); it never answers success. Only `registry()`, `notifications()` and
      `router()` are logged no-ops.
      Evidence: `FailClosedTest` (all 9, among them `#refusingProxyThrowsTheFixedMessage`,
      `#backedProxyDispatchesAndRefusesTheRest`, `#delegateSignatureMistakesFailAtConstruction`,
      `#loggedNoOpSurfaceSucceeds`, `#everyEmbeddedSurfaceBuilds`); the unsupported rows of EA2's inventory.
- [x] EA4. Embedded access runs on Derby or PostgreSQL. Any other database type stops startup with a message naming
      it, before any table is touched; so does a missing master encryption key.
      Evidence: `JdbcEmbeddedAccessStoreTest#unsupportedDatabaseIsRefusedWithoutTouchingIt`, `#supportedTypes`;
      `EmbeddedAccessFactoryTest#unsupportedDatabaseAndMissingEncryptionStopStartup`;
      `AccessServiceImplEmbeddedTest#refusedEmbeddedStartStopsStartupWithTheReason`; Derby: every store test below and
      every live test.
- [ ] EA5. On PostgreSQL the store behaves exactly as on Derby (the same tables from `embedded-access/postgresql.sql`,
      the same contract). `JdbcEmbeddedAccessStorePostgresTest` runs the whole store contract
      (`AbstractEmbeddedAccessStoreTest`) on PostgreSQL, and
      `JdbcEmbeddedAccessStoreTest#bothDdlResourcesDefineTheSameTables` compares the two DDL files.
      Not yet checked: the PostgreSQL test runs only when `EA_PG_URL` names a database, and it has not run; no live
      test uses PostgreSQL.
- [x] EA6. Embedded access is single node: one Artifactory process owns the tables, and the shipped deployment refuses
      more than one replica. High availability is out of scope.
      Evidence: `checks.helm-chart` (`replicas=2` fails to render, N8). Nothing at runtime detects a second node on
      the same database; running one is unsupported.
- [x] EA7. Persistence: users, groups, memberships, permissions, tokens and a key-value table live in the tables
      `ea_users`, `ea_groups`, `ea_user_groups` (deleted with the user or group), `ea_permissions`, `ea_tokens` and
      `ea_kv` (platform and security configuration, licence, bootstrap state, the encrypted signing key and
      `permissions.last_updated`), created idempotently on start (`EmbeddedAccessSchema.ensure`). Each store operation
      is one transaction; `ea_kv` writes are compare-and-swap on a revision with bounded retries. Everything survives
      a restart.
      Evidence: `JdbcEmbeddedAccessStoreTest` on Derby (the 21 tests of `AbstractEmbeddedAccessStoreTest`, among them
      `#userCreateReadFindAndList`, `#deleteUserRemovesMemberships`, `#groupDeleteCascadesToMemberships`,
      `#permissionCrudFindAndMentions`, `#tokenInsertReadListAndRevoke`, `#kvCompareAndSet`,
      `#kvUpdateRetriesAfterAConflict`, `#kvUpdateGivesUpAfterThreeRetriesAndPassesRefusals`,
      `#setPasswordAndReplaceKvWritesBothOrNeither`, `#updateAllUsersWritesOnlyChangedUsersInOneTransaction`, plus
      `#scriptSplitterDropsCommentsAndBlankStatements`); live `IA4.EA7.still-claimed-after-restart`.
- [x] EA8. Changes reach Artifactory's caches without polling Access: every user, group, membership or permission
      change advances `permissions.last_updated` (strictly increasing) in the same transaction, so the existing ACL
      freshness polling sees it, and an in-process bus publishes the affected users and groups after commit, in order,
      on one thread, coalescing into one "everything changed" event when its bounded queue overflows. A revoked token
      publishes its subject, so cached authentications of that user are dropped.
      Evidence: `JdbcEmbeddedAccessStoreTest#everyMutationAdvancesLastUpdatedMonotonically`,
      `#lastUpdatedBumpIsInTheSameTransaction`, `#mutationsPublishAfterCommitWithAffectedPrincipals`;
      `TokenRulesTest#lastUpdatedClockIsStrictlyIncreasing`; `EaChangeBusTest` (all 3, among them
      `#overflowCoalescesIntoOneEverythingChangedEvent`);
      `EmbeddedAccessAdaptersTest#permissionCrudLastUpdatedAndChangeEvents`,
      `#tokenRevocationReachesPermissionConsumersAsTheUser`;
      `EmbeddedAccessContractTest#aclsFollowPermissionChangesAndAuthorizationSeesThem`.
- [x] EA9. Signing key: on first start embedded access generates an RSA-2048 key and a self-signed certificate naming
      the Access service id `jfac@<id>` (the same id as `jfrt@<id>`), stores the key encrypted with the master key in
      `ea_kv`, and reuses it on every later start, so tokens minted before a restart still verify. A key that the
      current master key cannot decrypt stops startup.
      Evidence: `EaKeyMaterialTest#generatedKeysParseAndNameTheService`, `#mismatchedKeyAndCertificateAreRefused`;
      `EmbeddedAccessFactoryTest#firstStartSeedsUsersEncryptsTheKeyAndOpensTheSetupToken` (stored encrypted),
      `#restartKeepsTheSigningKeySoOldTokensVerify`, `#keyEncryptedWithAnotherMasterKeyIsRefused`;
      `TokenRulesTest#serviceIds`; live `EA9.old-token-verifies-after-restart`.
- [x] EA10. Access tokens are JWTs signed with that key (RS256) with the claims Artifactory expects (`sub`, `scp`,
      `aud`, `iss`, `exp`, `iat`, `jti`), and verify both through embedded access and through the stock
      `ClientTokenVerifyContext` against `root.crt`. Expired tokens, tokens signed by another key, foreign issuers,
      reference tokens and scopes outside the grammar are refused.
      Evidence: `EmbeddedTokenRoundTripTest#mintedTokenHasTheClaimsArtifactoryExpectsAndVerifies`,
      `#expiredTokenIsRefused`, `#tokenSignedByAnotherKeyIsRefused`, `#foreignIssuerAndReferenceTokensAreRefused`,
      `#noExpiryAndDefaultExpiry`, `#scopesArtifactoryRequestsAreAccepted`, `#invalidScopeIsRefused`;
      `TokenRulesTest#claimsHaveAccessShape`, `#nonExpiringTokenHasNoExpAndMultiAudienceIsAList`,
      `#claimInputsAreValidated`; `ScopeGrammarTest` (all 9); live `N3.token-mint-and-bearer-use` (a tampered token
      answers 401).
- [x] EA11. Refresh tokens are random, stored only as their SHA-256 hash, and usable once: a refresh revokes the old
      access token, works for an expired token only inside the refresh window, and is refused for a disabled or
      deleted user. Expired token rows are purged after the refresh window.
      Evidence: `EmbeddedTokenRoundTripTest#refreshWorksOnceAndRevokesTheOldToken` ("only the hash is stored"),
      `#refreshOfAnExpiredTokenWorksInsideTheWindowOnly`, `#refreshIsRefusedForADisabledUser`;
      `JdbcEmbeddedAccessStoreTest#refreshTokenIsConsumedOnce`, `#purgeDeletesOnlyTokensExpiredBeforeTheCutOff`;
      `EmbeddedAccessFactoryTest#purgeKeepsRecordsForTheRefreshWindow`.
- [x] EA12. Revocation: a revoked token fails verification at once. Tokens that are refreshable or live longer than
      21,600 seconds are revocable by id or value, by user and by scope, as in Access; shorter non-refreshable tokens
      are not. Deleting a user revokes all of its tokens, the non-revocable ones included.
      Evidence: `EmbeddedTokenRoundTripTest#revokedTokenFailsVerificationAndRevocationIsPublished`,
      `#revokeByValueAndNonRevocableTokens`, `#revokeAllForUserAndScopeAndTokenList`,
      `#deletingAUserRevokesAllOfItsTokensIncludingNonRevocableOnes`;
      `TokenRulesTest#revocabilityWithSubSecondLifetimeAboveThreshold`,
      `ValueTypesTest#tokenInvariantsAndRevocability`.
- [x] EA13. Password authentication: passwords are verified against `{bcrypt}` (cost 12) or `{pbkdf2}` (600,000
      iterations) hashes in pure Java; an unknown user still runs a comparison against a dummy hash, so timing does
      not reveal which users exist; a NULL hash, a disabled or a locked user, and an overlong password never
      authenticate; anonymous logs in only while anonymous access is enabled. The provider order is unchanged, so
      plugin realms still run before stored passwords (P2).
      Evidence: `PasswordHasherTest` (all 6); `EmbeddedAccessAdaptersTest#passwordAuthentication`,
      `#unknownUsersStillRunTheComparison`, `#nullHashNeverAuthenticates`, `#disabledAndLockedUsersNeverAuthenticate`,
      `#tokenAsPasswordAndAuthHeader`; `EmbeddedAccessFactoryTest#passwordVerifierRefusesOverlongPasswords`,
      `#anonymousLogsInOnlyWhileAnonymousAccessIsEnabled`,
      `#passwordExpiryForAllUsersSkipsPasswordlessUsersAndRoundTrips`;
      `PluginRealmProviderOrderTest#pluginRealmProviderComesBeforeStoredPasswordProviders`.
- [ ] EA14. LDAP and Crowd settings are stored in `ea_kv` (encrypted with the master key) and used by Artifactory's
      existing LDAP and Crowd providers; SAML, OAuth and HTTP SSO settings are stored but have no effect.
      Evidence so far: `EmbeddedAccessAdaptersTest#securityConfigDeepMergesWithDefaults`,
      `#authSettingsRoundTripAndEnableRealms` (the settings round trip).
      Not yet checked: no test logs in through an LDAP directory or Crowd with embedded access.
- [ ] EA15. Refused in embedded mode, and documented as such: OAuth user registration (`users().register`, the
      invitation activation of the OAuth flow) fails closed, and fine-grained permission scopes
      (`auth().principalCanDo`) answer `false`, so a token's permission scope never widens what the ACLs allow.
      Evidence so far: registration is an unsupported row of EA2's inventory, so
      `AccessLiteOperationsCoverageTest#criticalAndFeatureRowsAreImplementedAndUnsupportedRowsFailClosed` shows it
      refused. Open: `principalCanDo` is an optional row; no test asserts that it answers `false`, and whether
      features that rely on it (token permission scopes) should work in embedded mode is undecided.
- [ ] EA16. Stock mode is an explicit opt-out: `artifactory.access.embedded.enabled=false` restores the stock external
      Access server (with its join key and router), logs a WARN on start that the initial-admin bootstrap (IA1 to IA8)
      does not apply, and `InitialSetupFilter` lets everything through.
      Evidence so far: `InitialSetupFilterTest#stockAccessModePassesEverythingAsAnExplicitOptOut`.
      Not yet checked: no test asserts the WARN, and stock mode has not been run against an Access server since
      embedded access was added.
- [x] EA17. No migration from stock Access: embedded access starts from its own empty tables. On a volume a stock
      installation wrote, repositories and artifacts stay readable (B2), the stock administrator password does not log
      in, and the server must be claimed (IA4).
      Evidence: `docker/it/run-it.sh` on 2026-11-13: `IA4.EA17.fork-claimed-on-existing-data` (the claimed
      administrator 200, the stock phase's password 401), `B2.stock-data-readable`.
- [ ] EA18. Open decision: default groups and permission targets. Stock Access seeds a `readers` group and the
      `Anything` permission target; embedded access seeds only `admin` and `anonymous`. A realm that assigns `readers`
      therefore grants nothing (P3 applies only existing groups), and OSS has no public API to create groups, so
      `run-min-it.sh` grants its realm user read access through a permission target it creates. Whether to seed the
      stock defaults, seed something narrower, or document the empty start is undecided.
- [ ] EA19. Open: in the stock-image overlay deployment (B2) with embedded access, the stock router's port 8082 does
      not route to the fork; Artifactory's own port 8081 has to be exposed and used instead. The overlay should either
      route 8082 or document 8081; the minimal image (N3) has no router and serves 8081 directly.

Out of scope for embedded access: high availability and more than one node (EA6); MySQL, Oracle and MSSQL; migration
from stock Access data (EA17); mTLS; reference tokens; projects other than `default`; federation; vaults; SAML and
OAuth behaviour (EA14, EA15); the Auth0 password grant; and a separate Access-compatible network service.

## Out of scope for the first iteration

- Remote and virtual repositories of plugin types (the `HttpRepoFactory` hook
  exists and can come later).
- UI listing of plugin types. The Angular frontend is not in the OSS sources.
- Jobs, staging and promotion DSL blocks (realms: see Plugin realms).
- Opt-in compilation of WASM modules to JVM bytecode (W4).
- Embedded access beyond a single node on Derby or PostgreSQL for fresh installations (see the list at the end
  of Embedded access).
