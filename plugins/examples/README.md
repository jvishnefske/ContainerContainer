# Example user plugins

## simple-index.groovy

A worked example of a plugin-defined package type. It registers the type
`simpleidx`, a local Generic repository flavour that only holds `.tgz`
packages and keeps a generated index of them.

- **Upload:** a PUT of any file whose name does not end in `.tgz` is rejected
  with 400 and nothing is stored. The one exception is `index.json` at the
  repository root, because the plugin's own index writes go through the same
  upload check; a hand-deployed index is overwritten by the next index run.
- **Index:** after a `.tgz` is stored, the plugin walks the repository and
  rewrites `index.json` at the repository root. The document has one field,
  `packages`, listing every `.tgz` path in the repository in sorted order.
  This runs asynchronously, so the index appears shortly after the upload
  returns.
- **Download:** a GET of `index.json` is served from the stored index file.
  Every other request is declined by the plugin and handled as plain Generic.
  Normal read permissions apply, so anonymous users cannot read the index
  unless anonymous access is enabled and permitted for the repository.
- **Execution `simpleidxReindex`:** rebuilds the index of one repository on
  demand. It answers only POST, takes the repository key as the `repo`
  parameter, and only administrators may run it.

If the plugin is removed, repositories of type `simpleidx` keep working as
plain Generic repositories and their stored packages and index stay intact;
the index is simply no longer updated.

## Installing

1. Copy `simple-index.groovy` into the plugins directory of the Artifactory
   home, `$JFROG_HOME/artifactory/var/etc/artifactory/plugins` (in the
   official Docker image, `/var/opt/jfrog/artifactory/etc/artifactory/plugins`).
2. Load it, either by restarting Artifactory or by calling the plugin reload
   REST endpoint (`POST /artifactory/api/plugins/reload`) as an administrator.
   The plugin listing endpoint (`GET /artifactory/api/plugins`) then shows the
   plugin as loaded with its package type and execution.
3. Create a local repository of package type Generic whose package type
   configuration sets `pluginPackageType` to `simpleidx`. In OSS the public
   repository REST API is a Pro feature, so use the YAML configuration API
   (`PATCH /artifactory/api/system/configuration`) with a `localRepositories`
   entry of `type: generic` and `pluginPackageType: simpleidx`. Naming a type
   that no loaded plugin registers is rejected with 400.

The live integration test in `docker/it/run-it.sh` performs exactly these
steps against the overlay image and exercises every behaviour listed above.
