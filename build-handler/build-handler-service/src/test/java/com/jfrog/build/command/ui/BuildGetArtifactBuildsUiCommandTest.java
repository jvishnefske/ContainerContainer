package com.jfrog.build.command.ui;

import com.jfrog.build.acl.model.BuildArtifactChecksums;
import com.jfrog.build.acl.model.ProducedBy;
import com.jfrog.build.acl.model.UsedBy;
import com.jfrog.build.model.api.ArtifactBuildsModel;
import com.jfrog.build.service.BuildArtifactService;
import com.jfrog.build.service.BuildReadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuildGetArtifactBuildsUiCommandTest {

    private static final String REPO_KEY = "libs-releases-local";
    private static final String PATH = "org/example/lib/1.0/lib-1.0.jar";
    private static final String SHA1 = "09ce045cb586ff4c2b5a2427b811823214daa379";
    private static final String MD5 = "c78996a9838d3ce72bb380291645cee6";

    private BuildArtifactService artifactService;
    private BuildReadService readService;
    private BuildGetArtifactBuildsUiCommand command;

    @BeforeEach
    void setUp() {
        artifactService = mock(BuildArtifactService.class);
        readService = mock(BuildReadService.class);
        command = new BuildGetArtifactBuildsUiCommand(artifactService, readService);
    }

    @Test
    void getArtifactBuilds_shouldReturnEmpty_whenNoChecksumsResolved() {
        when(artifactService.getArtifactChecksums(REPO_KEY, PATH)).thenReturn(Optional.empty());

        ArtifactBuildsModel model = command.getArtifactBuilds(REPO_KEY, PATH);

        assertTrue(model.getUsedBy().isEmpty());
        assertTrue(model.getProducedBy().isEmpty());
        verify(readService, never()).retrieveDependantBuilds(SHA1, MD5);
        verify(readService, never()).retrieveProducedBuilds(SHA1, MD5);
    }

    @Test
    void getArtifactBuilds_shouldReturnUsedByAndProducedBy_whenChecksumsResolved() {
        when(artifactService.getArtifactChecksums(REPO_KEY, PATH))
                .thenReturn(Optional.of(new BuildArtifactChecksums(SHA1, MD5)));
        UsedBy usedBy = new UsedBy();
        usedBy.setName("my-build");
        usedBy.setNumber("15");
        ProducedBy producedBy = new ProducedBy();
        producedBy.setName("producer-build");
        producedBy.setNumber("3");
        when(readService.retrieveDependantBuilds(SHA1, MD5)).thenReturn(Stream.of(usedBy));
        when(readService.retrieveProducedBuilds(SHA1, MD5)).thenReturn(Stream.of(producedBy));

        ArtifactBuildsModel model = command.getArtifactBuilds(REPO_KEY, PATH);

        assertEquals(1, model.getUsedBy().size());
        assertEquals("my-build", model.getUsedBy().getFirst().getName());
        assertEquals("15", model.getUsedBy().getFirst().getNumber());
        assertEquals(1, model.getProducedBy().size());
        assertEquals("producer-build", model.getProducedBy().getFirst().getName());
    }
}
