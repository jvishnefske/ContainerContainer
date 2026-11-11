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

- [ ] B1. The fork builds the WAR from source with JDK 25 and Maven, resolving
      dependencies only from Maven Central and JFrog's public `jfrog-jars`
      repository.
- [ ] B2. The fork's jars, overlaid on the stock 7.161.26 image, start
      Artifactory with existing data and no schema migration.
- [ ] B3. With no plugins present the fork behaves as stock OSS: the existing
      unit test suite of every changed module passes.

### Groovy plugin engine

- [ ] G1. On startup, every `*.groovy` file in `ArtifactoryHome.getPluginsDir()`
      (`$JFROG_HOME/artifactory/var/etc/artifactory/plugins`) is compiled and
      loaded. A plugin that fails to compile is reported in plugin status and
      does not stop startup or other plugins.
- [ ] G2. `POST /api/plugins/reload` reloads all plugins without a restart and
      replaces previously registered hooks and package types.
- [ ] G3. The DSL supports `download { beforeDownloadRequest, altResponse,
      afterDownload }`, `storage { beforeCreate, afterCreate, beforeDelete,
      afterDelete }` and `executions { name { params -> } }`, bound to the
      existing `PluginAction` interfaces in `backend/core/.../addon/plugin`.
- [ ] G4. `POST /api/plugins/execute/{name}` runs a named execution, admin-only
      by default, and returns its status and message.
- [ ] G5. `GET /api/plugins` lists loaded plugins and their hooks.
- [ ] G6. Plugin scripts see `repositories`, `log` and `security` bindings.
      `repositories` supports at least `getFileInfo`, `getContent`, `deploy`,
      `delete`, `exists`, `getProperties` and `setProperty`.
- [ ] G7. An exception thrown inside a hook is logged with the plugin name and
      does not fail the request, except when the hook explicitly sets an error
      status (for example `CancelException` in `beforeCreate`).

### Plugin-defined repository types

- [ ] R1. A plugin declares `packageType('name') { displayName, layout,
      download {}, upload {}, index {} }`. Names are unique, lowercase
      `[a-z0-9-]+`, and must not collide with a `RepoType` value.
- [ ] R2. Creating a local repository through the REST repository API with
      `"packageType": "generic"` and `"packageTypeConfig":
      {"pluginPackageType": "name"}` succeeds only when `name` is registered.
      Otherwise it returns 400 with a message naming the unknown type.
- [ ] R3. GET requests to a plugin-typed repository go through the type's
      `download` handler, which can serve stored content, a generated
      response (for example an index document) or a status code. When the
      handler declines, normal Generic handling applies.
- [ ] R4. PUT requests to a plugin-typed repository go through the type's
      `upload` handler, which can reject the upload or rewrite the target path
      before storage.
- [ ] R5. After an artifact is stored in a plugin-typed repository, the type's
      `index` handler runs asynchronously, so it can maintain metadata files
      such as an index.
- [ ] R6. If the plugin is removed, a repository of its type keeps working as
      plain Generic and its stored artifacts stay intact.
- [ ] R7. A worked example plugin ships under `plugins/examples/` and defines a
      simple package type with an index, covered by an integration test.

## Out of scope for the first iteration

- Remote and virtual repositories of plugin types (the `HttpRepoFactory` hook
  exists and can come later).
- UI listing of plugin types. The Angular frontend is not in the OSS sources.
- Realms, jobs, staging and promotion DSL blocks.
- WASM plugins.
