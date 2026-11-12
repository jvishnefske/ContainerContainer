# OCI registry plugin

`oci-registry.groovy` turns a local Generic repository of the Artifactory OSS fork into an OCI Distribution
Spec v1.1 registry. It is written entirely against the plugin HTTP API (`api {}` handlers of a plugin package
type), so it needs no Java code of its own. The package type it registers is `oci-registry`; the built-in
`docker` and `oci` names belong to Artifactory's own (Pro) package types and cannot be used by a plugin.

## What it supports

- Pulling blobs with `GET` and `HEAD`, including a single byte range (`Range: bytes=a-b`, `bytes=a-` and
  `bytes=-n`), with `Docker-Content-Digest`, an exact `Content-Length` and `Content-Type:
  application/octet-stream`.
- Pushing blobs in every way the spec defines: a monolithic `POST ...?digest=`, `POST` followed by one `PUT
  ...?digest=`, and chunked uploads with `PATCH` (with or without `Content-Range`) and a final `PUT`, which may
  carry the last chunk. Out-of-order chunks are refused with 416, upload status is reported by `GET` on the
  upload URL, and `DELETE` cancels an upload.
- Mounting a blob with `POST ...?mount=<digest>&from=<name>` when `<name>` is in the same Artifactory
  repository. Any other `from`, including another repository and a missing `from`, gets a normal upload
  session (202), which the spec allows.
- Manifests by tag and by digest (`PUT`, `GET`, `HEAD`, `DELETE`) for the OCI image manifest, the OCI image
  index, the Docker v2 schema 2 manifest and the Docker manifest list. A manifest is served with the media
  type it was pushed with. On push, every blob an image manifest references, and every manifest an index
  references, must already be in the same name of the same repository; otherwise the push is refused with
  `MANIFEST_BLOB_UNKNOWN`. Manifests are capped at 4 MiB (413 above).
- `GET tags/list` in lexical order with `n` and `last` pagination and a `Link` header for the next page.
- Deleting manifests by digest (which also removes the tags that point at it) and by tag, and deleting blobs;
  all answer 202, and 404 afterwards.
- The OCI 1.1 referrers API, `GET referrers/<digest>`, with the `artifactType` filter. Pushing a manifest with
  a `subject` answers with the `OCI-Subject` header.

The official conformance suite (distribution-spec v1.1.0, pull, push, content discovery and content
management) passes against it; `docker/it/run-oci-it.sh` runs it together with docker and podman clients.

## Storage layout

Everything is stored inside the repository the request addresses, under the image name:

- `<name>/_blobs/sha256/<hex>` holds a blob.
- `<name>/_manifests/sha256/<hex>` holds a manifest exactly as pushed, and `<hex>.mediatype` next to it the
  media type it was pushed with.
- `<name>/_tags/<tag>` is a small text file naming the digest the tag points at.
- `<name>/_referrers/sha256/<subject hex>/<hex>` describes a manifest whose subject is that digest.
- `<name>/_uploads/<uuid>/` holds an upload in progress: the user who opened it and one file per chunk,
  named by the chunk's start offset.

Folder names that start with an underscore cannot be part of an image name, so they never collide with one.
A blob or manifest counts as present only when the SHA-256 Artifactory computed for the stored file equals
the digest in its path, so a file put there by other means is ignored. Direct HTTP deploys into the
repository are refused; content enters only through the registry API, where every upload is checked against
its digest before it is copied to its digest path.

## Security model

Artifactory authenticates the caller (HTTP Basic) and checks the method's permission on the repository
before the plugin runs: read for `GET` and `HEAD`, deploy for `POST`, `PUT` and `PATCH`, delete for `DELETE`.
Permissions are therefore per repository, not per image name. Writes additionally get Artifactory's usual
per-path checks, so replacing an existing tag (re-tagging) needs delete permission, as it does for
Artifactory's own Docker repositories. The plugin validates every name, tag, digest and upload id against the
spec's grammars before it builds a path, and never touches another repository, which is why a mount from
another repository is answered with an upload session instead.

## Limits

- No garbage collection. Deleting a manifest does not delete the blobs it referenced, and abandoned upload
  sessions stay under `_uploads/` until someone deletes them. Deleted files go to Artifactory's trash can as
  usual.
- A user without delete permission can push, but the plugin cannot then clean up the finished upload folder,
  which stays behind under `_uploads/`.
- Local repositories only: no remote (proxy) or virtual repositories of this type.
- Basic authentication only; there is no token service, so clients that insist on bearer tokens are not
  supported. The catalog endpoint (`/v2/_catalog`) is not provided.
- Upload bodies must not be sent as `application/x-www-form-urlencoded` (curl's default for `--data`):
  Artifactory's servlet filters parse such a body as form parameters before the plugin runs, so the plugin
  sees it empty and refuses the upload with `DIGEST_INVALID`. Registry clients send
  `application/octet-stream`.
- Listings are bounded: 10000 tags per name, 1000 referrers per subject and 10000 chunks per upload.

## Creating a repository

Create a local Generic repository whose package type configuration sets `pluginPackageType` to
`oci-registry`. In OSS the public repository REST API is a Pro feature, so use the YAML configuration API as
an administrator:

`PATCH /artifactory/api/system/configuration` with `Content-Type: application/yaml` and a body that declares
the repository under `localRepositories` with `type: generic` and `pluginPackageType: oci-registry`.

Install the plugin by copying `oci-registry.groovy` into `$JFROG_HOME/artifactory/var/etc/artifactory/plugins`
and either restarting Artifactory or calling `POST /artifactory/api/plugins/reload`.

## Configuring clients

Registry clients use the registry root, `/v2/`, on the Artifactory platform port (8082 by default). The
repository key is the first segment of every image name (Artifactory's repository-path-prefix method), so an
image `app` in repository `oci-local` on `registry.example.com:8082` is
`registry.example.com:8082/oci-local/app:1.0`.

- docker: `docker login registry.example.com:8082` with Artifactory credentials, then `docker push` and
  `docker pull` with references like the one above. Without TLS, the host must be listed as an insecure
  registry in the docker daemon configuration; `127.0.0.0/8` is treated as insecure by default.
- podman: `podman login registry.example.com:8082`, then `podman push` and `podman pull`; add
  `--tls-verify=false` when the registry is served over plain HTTP. `podman manifest push --all` pushes
  multi-platform images as an OCI index, or as a Docker manifest list with `--format v2s2`.
- Other tools (crane, oras, regctl, skopeo) work the same way with Basic credentials.

Without the registry root, the same API is also reachable at
`<artifactory>/api/docker/<repoKey>/v2/<name>/...`; there the image name does not include the repository key.
