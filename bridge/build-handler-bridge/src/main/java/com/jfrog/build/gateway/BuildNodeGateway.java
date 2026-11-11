package com.jfrog.build.gateway;

import com.jfrog.build.acl.model.BuildNode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.artifactory.storage.db.fs.dao.NodesDao;
import org.artifactory.storage.db.fs.entity.Node;

import java.sql.SQLException;
import java.util.List;

import static org.artifactory.exception.factory.ExceptionFactory.internalServerException;

@RequiredArgsConstructor
public class BuildNodeGateway {

    private final NodesDao nodesDao;

    @NonNull
    public List<BuildNode> getNodesBySha1(@NonNull List<String> checksums) {
        try {
            return nodesDao.getFileNodesBySha1(checksums).stream()
                    .map(this::buildNode)
                    .toList();
        } catch (SQLException e) {
            throw internalServerException("Failed to retrieve nodes from the DB", e);
        }
    }

    @NonNull
    private BuildNode buildNode(@NonNull Node node) {
        return BuildNode.builder()
                .repo(node.getRepo())
                .path(node.getPath())
                .name(node.getName())
                .nodeId(node.getNodeId())
                .depth(node.getDepth())
                .created(node.getCreated())
                .modified(node.getModified())
                .updated(node.getUpdated())
                .file(node.isFile())
                .createdBy(node.getCreatedBy())
                .modifiedBy(node.getModifiedBy())
                .length(node.getLength())
                .sha1Actual(node.getSha1Actual())
                .sha1Original(node.getSha1Original())
                .sha2(node.getSha2())
                .md5Actual(node.getMd5Actual())
                .md5Original(node.getMd5Original())
                .build();
    }
}
