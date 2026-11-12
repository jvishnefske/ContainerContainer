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
      cached. Evidence: `RealmGrantPolicyTest` (all 6),
      `PluginRealmAuthenticationProviderTest#bearerJwtCreatesAJustInTimeUserInThePluginRealmWithOnlyAllowedGroups`,
      `#aRealmUserHasItsStoredGroupsReplacedOnTheNextLogin`,
      `#anExistingUserGetsTheRealmGroupsForTheRequestOnly`,
      `#neverAdminAndRejectionsAreFinalWithAGenericMessage`,
      `#anAuthenticationIsRevokedWhenThePluginIsReloadedOrRemoved`,
      `ExpiringAuthenticationTest` (both); live
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

## Out of scope for the first iteration

- Remote and virtual repositories of plugin types (the `HttpRepoFactory` hook
  exists and can come later).
- UI listing of plugin types. The Angular frontend is not in the OSS sources.
- Jobs, staging and promotion DSL blocks (realms: see Plugin realms).
- Opt-in compilation of WASM modules to JVM bytecode (W4).
