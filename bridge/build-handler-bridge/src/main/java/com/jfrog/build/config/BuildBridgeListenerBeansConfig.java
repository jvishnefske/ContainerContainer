package com.jfrog.build.config;

import com.jfrog.build.acl.bridge.BuildBridgeFromArtifactory;
import com.jfrog.build.listener.BuildEventListener;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.artifactory.repo.service.InternalRepositoryService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@AllArgsConstructor
public class BuildBridgeListenerBeansConfig {

    private final InternalRepositoryService repositoryService;

    @Bean
    public BuildEventListener buildEventListener(
            @NonNull BuildBridgeFromArtifactory buildBridgeFromArtifactory) {
        return new BuildEventListener(
                buildBridgeFromArtifactory,
                repositoryService
        );
    }
}
