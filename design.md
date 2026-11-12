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
      plugin) by `#getChildrenDelegatesToTheRepositoryService`. `security` exposes only
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
- [x] R5. After an artifact is stored in a plugin-typed repository, the type's
      `index` handler runs asynchronously, so it can maintain metadata files
      such as an index.
      Evidence: `PluginPackageTypeIndexInterceptorTest#indexRunsOnceAndNotForItsOwnWrites`
      and `#dispatchIsAsyncAfterCommit`. Live: `docker/it/run-it.sh`
      `R5.R7.index-json-lists-a-1.0.tgz`.
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
      `plugins/examples/simple-index.groovy` defines type `simpleidx` (accepts only
      `.tgz`, maintains `index.json`, serves it) and the admin-only POST execution
      `simpleidxReindex`. Evidence: `docker/it/run-it.sh` (2026-11-11, image
      digest under B2): `G5.plugins-lists-simpleidxReindex`,
      `G4.execute-wrong-method-405`, `R4.upload-tgz-201`,
      `R4.upload-txt-rejected-400`, `R5.R7.index-json-lists-a-1.0.tgz`,
      `R7.reindex-execution`; unit: `org.artifactory.addon.plugin.groovy.ExamplePluginTest`
      loads the shipped file.

## Out of scope for the first iteration

- Remote and virtual repositories of plugin types (the `HttpRepoFactory` hook
  exists and can come later).
- UI listing of plugin types. The Angular frontend is not in the OSS sources.
- Realms, jobs, staging and promotion DSL blocks.
- WASM plugins.
