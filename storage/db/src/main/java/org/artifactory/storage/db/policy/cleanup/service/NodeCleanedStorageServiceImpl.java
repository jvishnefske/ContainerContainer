package org.artifactory.storage.db.policy.cleanup.service;

import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.storage.StorageException;
import org.artifactory.storage.db.policy.cleanup.CleanedNode;
import org.artifactory.storage.db.policy.cleanup.CleanedPackage;
import org.artifactory.storage.db.policy.cleanup.DeleteArtifactStatus;
import org.artifactory.storage.db.policy.cleanup.dao.NodeCleanedDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author vovam
 */
@Slf4j
@Service
public class NodeCleanedStorageServiceImpl implements NodeCleanedStorageService {
    private final NodeCleanedDao nodeCleanedDao;

    @Autowired
    public NodeCleanedStorageServiceImpl(NodeCleanedDao nodeCleanedDao) {
        this.nodeCleanedDao = nodeCleanedDao;
    }

    @Override
    public void insertCleanedNode(CleanedNode cleanedNode) {
        try {
            validateCleanedNode(cleanedNode);
            nodeCleanedDao.insertCleanedNode(cleanedNode);
        } catch (SQLException e) {
            throw new StorageException("Can't insert cleaned node. Policy key " + cleanedNode.policyKey()
                    + ", policy run id " + cleanedNode.policyRunId(), e);
        }
    }


    @Override
    public void updateCleanedNodeBySha(String policyKey, int policyRunId, int status, List<String> sha1List) {
        try {
            nodeCleanedDao.updateCleanedNodeBySha(policyKey, policyRunId, status, sha1List);
        } catch (SQLException e) {
            throw new StorageException("Can't update cleaned node. Policy key " + policyKey
                    + ", policy run id " + policyRunId, e);
        }
    }

    @Override
    public long estimateCleanedBinLengthByPolicyKeyAndRunId(String policyKey, int policyRunId) {
        try {
            return nodeCleanedDao.estimateActualCleanedBinLength(policyKey, policyRunId);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to calculate estimate cleaned size. Policy key " + policyKey + ", policy run id " +
                            policyRunId + " with an error: " + e.getMessage(), e);
        }
    }

    @Override
    public long estimateProcessedBinLengthByPolicyKeyAndRunId(String policyKey, int policyRunId,
            DeleteArtifactStatus processedStatus) {
        try {
            return nodeCleanedDao.estimateActualProcessedBinLength(policyKey, policyRunId, processedStatus);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to calculate processed bin length. Policy key " + policyKey + ", policy run id " +
                            policyRunId + " with an error " + e.getMessage(), e);
        }
    }

    @Override
    public long estimateProcessedBinLengthByPolicyKeyAndRunIdByStatus(String policyKey, int policyRunId,
            DeleteArtifactStatus processedStatus) {
        try {
            return nodeCleanedDao.estimateActualProcessedBinLengthByStatus(policyKey, policyRunId, processedStatus);
        } catch (SQLException e) {
            throw new StorageException(
                    "Can't estimate " + processedStatus + " size. Policy key " + policyKey +
                            ", run id " + policyRunId, e);
        }
    }

    @Override
    public long calculateCleanupRunTotalBinSize(String policyKey, long policyRunId) {
        try {
            return nodeCleanedDao.calculateCleanupRunTotalBinSize(policyKey, policyRunId);
        } catch (SQLException e) {
            throw new StorageException(String.format(
                    "Failed to calculate total size of processed nodes. Policy key: '%s', policy run id: '%d' with an" +
                            " error: '%s'", policyKey, policyRunId, e.getMessage()), e);
        }
    }

    @Override
    public long calculateVirtualBinarySize(String policyKey, long policyRunId,
            DeleteArtifactStatus processedStatus) {
        try {
            return nodeCleanedDao.calculateVirtualBinarySize(policyKey, policyRunId, processedStatus);
        } catch (SQLException e) {
            throw new StorageException(String.format(
                    "Failed to calculate virtual size of processed nodes. Policy key: '%s', id: '%d' with " +
                            "an error: '%s'", policyKey, policyRunId, e.getMessage()), e);
        }
    }

    @Override
    public void cleanNodesCleanedRecordsByPolicyKeyAndRunId(String policyKey, int policyRunId) {
        try {
            nodeCleanedDao.deleteByPolicyKeyAndRun(policyKey, policyRunId);
        } catch (SQLException e) {
            throw new StorageException(
                    "Can't clean records. Policy key " + policyKey + ", policy run id " + policyRunId, e);
        }
    }

    @Nonnull
    @Override
    public List<CleanedPackage> getNodesCleanedRecordsByPolicyKeyAndRunId(@Nonnull String policyKey, int policyRunId) {
        try {
            return nodeCleanedDao.getDeletedNodesRecords(policyKey, policyRunId);
        } catch (SQLException e) {
            throw new StorageException(
                    "Can't get deleted packages. Policy key " + policyKey + ", policy run id " + policyRunId, e);
        }
    }

    @Nonnull
    @Override
    public List<CleanedNode> getCleanedNodesByRunIdAndPolicyKey(@Nonnull String policyKey, int policyRunId) {
        try {
            return nodeCleanedDao.getCleanedNodesByRun(policyKey, policyRunId);
        } catch (SQLException e) {
            throw new StorageException(
                    "Can't get processed retention nodes. Policy key " + policyKey + ", policy run id "
                            + policyRunId, e);
        }
    }

    @Override
    public List<CleanedNode> getCleanedNodeByStatus(List<Integer> statuses, long limit, long offset) {
        try {
            return nodeCleanedDao.getCleanedNodesByStatus(statuses, limit, offset);
        } catch (SQLException e) {
            throw new StorageException("Can't get processed retention nodes by status " + statuses, e);
        }
    }

    @Nonnull
    @Override
    public Map<String, CleanedNode> getLeadMetadataMap(@Nonnull String policyKey, int policyRunId) {
        try {
            List<CleanedNode> leadNodes = nodeCleanedDao.getLeadMetadata(policyKey, policyRunId);
            Map<String, CleanedNode> result = new HashMap<>();
            
            for (CleanedNode node : leadNodes) {
                String key = CleanedPackage.toPackageKey(node);
                CleanedNode existing = result.get(key);
                
                if (existing == null || (isArchiveManifest(existing) && !isArchiveManifest(node))) {
                    result.put(key, node);
                }
            }
            
            return result;
        } catch (SQLException e) {
            throw new StorageException(
                    "Can't get lead metadata map. Policy key " + policyKey + ", policy run id " + policyRunId, e);
        }
    }


    @Override
    public String getFirstErrorMessageForRun(@Nonnull String policyKey, int policyRunId) {
        try {
            return nodeCleanedDao.getFirstErrorMessageForRun(policyKey, policyRunId);
        } catch (SQLException e) {
            throw new StorageException(
                    "Failed to fetch error message for policy key " + policyKey + ", run id " + policyRunId, e);
        }
    }

    private boolean isArchiveManifest(CleanedNode node) {
        if (node.nodeName() == null || !node.nodeName().endsWith(".json")) {
            return false;
        }
        
        String expectedSuffix = node.packageName().replace("/", "-") + "-" + 
                               node.packageVersion() + ".json";
        
        return node.nodeName().endsWith(expectedSuffix) && node.nodeName().contains("-");
    }

    private void validateCleanedNode(CleanedNode cleanedNode) {
        if (StringUtils.isEmpty(cleanedNode.policyKey())) {
            throw new IllegalArgumentException("Policy key can't be empty");
        }
        if (StringUtils.isEmpty(cleanedNode.repo())) {
            throw new IllegalArgumentException("Repo can't be empty");
        }
        if (cleanedNode.nodePath() == null) {
            throw new IllegalArgumentException("Node path can't be null");
        }
        if (StringUtils.isEmpty(cleanedNode.nodeName())) {
            throw new IllegalArgumentException("Node name can't be empty");
        }
        if (StringUtils.isEmpty(cleanedNode.sha1())) {
            throw new IllegalArgumentException("SHA can't be empty");
        }
        if (cleanedNode.status() == null) {
            throw new IllegalArgumentException("Status can't be null");
        }
    }
}