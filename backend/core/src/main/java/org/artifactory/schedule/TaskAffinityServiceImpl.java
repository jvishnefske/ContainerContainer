package org.artifactory.schedule;

import org.apache.commons.collections4.CollectionUtils;
import org.artifactory.storage.db.servers.model.ArtifactoryServer;
import org.artifactory.storage.db.servers.service.ArtifactoryServersCommonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Omri Ziv
 */
@Service
public class TaskAffinityServiceImpl implements TaskAffinityService {

    private ArtifactoryServersCommonService serversService;

    private SecureRandom rand = new SecureRandom();

    @Autowired
    public TaskAffinityServiceImpl(ArtifactoryServersCommonService serversService) {
        this.serversService = serversService;
    }

    @Override
    public List<String> getAffinityForTask(JobName jobName) {
        List<ArtifactoryServer> servers = serversService.getAliveAffinityNodes();
        if (CollectionUtils.isEmpty(servers)) {
            return Collections.emptyList();
        }
        Map<String, List<String>> affinityMapping = servers.stream()
                .collect(Collectors.toMap(ArtifactoryServer::getServerId, ArtifactoryServer::getAffinityTasksForNode));

        List<String> affinityNodesOfTask = affinityMapping.entrySet().stream()
                .filter(entry -> entry.getValue().contains(jobName.asText()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(affinityNodesOfTask) && !JobName.ANY.equals(jobName)) {
            return getAffinityForTask(JobName.ANY);
        }
        return affinityNodesOfTask;
    }

    @Nonnull
    @Override
    public Optional<ArtifactoryServer> pickAffinityMemberByNodeNames(List<String> taskAffinityNodes) {
        if (CollectionUtils.isEmpty(taskAffinityNodes)) {
            return Optional.empty();
        }
        List<ArtifactoryServer> aliveMembers = serversService.filterAliveMembersByNodeNames(taskAffinityNodes);
        if (CollectionUtils.isEmpty(aliveMembers)) {
            return Optional.empty();
        }
        int index = rand.nextInt(aliveMembers.size());
        return Optional.of(aliveMembers.get(index));
    }

}