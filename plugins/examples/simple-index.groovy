/*
 * Example user plugin for the Artifactory OSS fork: a plugin-defined package type with an index.
 *
 * Package type 'simpleidx' (a local Generic repository whose packageTypeConfig carries
 * pluginPackageType=simpleidx):
 *   upload   - only files ending in .tgz (and the index the plugin writes itself) are accepted; anything
 *              else is rejected with 400.
 *   index    - after a .tgz is stored, index.json at the repository root is rewritten to list
 *              every .tgz path in the repository.
 *   download - GET index.json is served from the stored index file; every other request is
 *              declined and handled as plain Generic.
 *
 * Execution 'simpleidxReindex' (POST, administrators only) rebuilds the index of one repository:
 *   POST /artifactory/api/plugins/execute/simpleidxReindex?params=repo=<repoKey>
 *
 * Copyright (C) 2026 the fork authors. AGPL-3.0-or-later.
 */

import groovy.transform.Field
import org.artifactory.fs.ItemInfo

import java.nio.charset.StandardCharsets

/** Path of the generated index, relative to the repository root. */
@Field final String INDEX_PATH = 'index.json'
/** Bounds on the repository walk, so a rebuild always ends in bounded time. */
@Field final int MAX_DEPTH = 32
@Field final int MAX_ENTRIES = 100000

/** @return the paths of all .tgz files in repository {@code repoKey}, sorted */
List<String> tgzPaths(String repoKey) {
    List<String> found = []
    List<List> pending = [[RepoPathFactory.create(repoKey, ''), 0]]
    while (!pending.isEmpty() && found.size() < MAX_ENTRIES) {
        List next = pending.remove(pending.size() - 1)
        RepoPath folder = next[0]
        int depth = next[1]
        for (ItemInfo child : repositories.getChildren(folder)) {
            if (child.isFolder()) {
                if (depth < MAX_DEPTH) {
                    pending << [child.repoPath, depth + 1]
                }
            } else if (child.repoPath.path.endsWith('.tgz')) {
                found << child.repoPath.path
            }
        }
    }
    found.sort()
}

/** @return {@code value} as a JSON string literal */
String jsonString(String value) {
    StringBuilder out = new StringBuilder('"')
    for (char c : value.toCharArray()) {
        if (c == '"' as char || c == '\\' as char) {
            out.append('\\').append(c)
        } else if (c < (' ' as char)) {
            out.append(String.format('\\u%04x', (int) c))
        } else {
            out.append(c)
        }
    }
    out.append('"').toString()
}

/** @return the index document listing {@code paths} */
String indexDocument(List<String> paths) {
    '{"packages":[' + paths.collect { jsonString(it) }.join(',') + ']}\n'
}

/** Rewrites index.json of {@code repoKey}; returns the number of packages listed. */
int rebuildIndex(String repoKey) {
    List<String> paths = tgzPaths(repoKey)
    byte[] body = indexDocument(paths).getBytes(StandardCharsets.UTF_8)
    def status = repositories.deploy(RepoPathFactory.create(repoKey, INDEX_PATH), new ByteArrayInputStream(body))
    if (status.error) {
        throw new IllegalStateException("could not write ${repoKey}/${INDEX_PATH}: ${status.statusMsg}")
    }
    log.info('simpleidx: {}/{} lists {} package(s)', repoKey, INDEX_PATH, paths.size())
    paths.size()
}

packageType('simpleidx') {
    displayName 'Simple index'

    // The plugin's own index writes (repositories.deploy) pass through this handler as well, so the index path
    // is accepted too; an index.json deployed by hand is overwritten by the next index run.
    upload { request, repoPath ->
        (repoPath.path.endsWith('.tgz') || repoPath.path == INDEX_PATH) ? null :
                UploadDecision.reject(400, "simpleidx accepts only .tgz files, not '${repoPath.path}'".toString())
    }

    index { repoPath ->
        if (repoPath.path.endsWith('.tgz')) {
            rebuildIndex(repoPath.repoKey)
        }
    }

    download { request, repoPath, response ->
        if (repoPath.path != INDEX_PATH || !repositories.exists(repoPath)) {
            return false
        }
        def content = repositories.getContent(repoPath)
        status = 200
        size = content.size
        response.inputStream = content.inputStream
        true
    }
}

executions {
    simpleidxReindex(httpMethod: 'POST',
            description: 'Rebuild index.json of a simpleidx repository (params: repo)') { params ->
        def repo = params?.get('repo')
        String repoKey = repo instanceof Collection ? (repo ? repo.first() : null) : repo
        if (!repoKey) {
            status = 400
            message = 'missing parameter: repo'
            return
        }
        int count = rebuildIndex(repoKey)
        status = 200
        message = "${repoKey}/${INDEX_PATH} lists ${count} package(s)".toString()
    }
}
