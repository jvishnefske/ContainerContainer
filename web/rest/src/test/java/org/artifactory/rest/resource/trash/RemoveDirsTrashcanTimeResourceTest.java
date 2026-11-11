package org.artifactory.rest.resource.trash;

import org.apache.http.HttpStatus;
import org.artifactory.repo.service.trash.rdtt.RemoveDirsTrashcanTimeRequestModel;
import org.artifactory.repo.service.trash.rdtt.RemoveDirsTrashcanTimeServiceImpl;
import org.jfrog.storage.common.prune.PruneResponseModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.ws.rs.core.Response;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

class RemoveDirsTrashcanTimeResourceTest {
    @ParameterizedTest
    @MethodSource("requestModels")
    void startRemoveDirsTrashcanTimeService(RemoveDirsTrashcanTimeRequestModel requestModel) {
        RemoveDirsTrashcanTimeServiceImpl removeDirsTrashcanTimeService = mock(RemoveDirsTrashcanTimeServiceImpl.class);
        doNothing().when(removeDirsTrashcanTimeService).execute(anyInt(), anyLong(), anyInt());
        RemoveDirsTrashcanTimeResource x = spy(new RemoveDirsTrashcanTimeResource(removeDirsTrashcanTimeService));

        try (Response response = x.startRemoveDirsTrashcanTimeService(requestModel)) {
            assertEquals(HttpStatus.SC_ACCEPTED, response.getStatus());
            assertEquals("Remove Directories Trashcan Time task has been submitted",
                    ((PruneResponseModel) response.getEntity()).info());
        }
    }

    private static Stream<Arguments> requestModels() {
        RemoveDirsTrashcanTimeRequestModel arg0 = new RemoveDirsTrashcanTimeRequestModel();
        arg0.setBatchSize(10);
        arg0.setPauseBetweenBatchesMs(20);
        arg0.setBatchesCount(5);
        return Stream.of(
                Arguments.of(arg0),
                Arguments.of(RemoveDirsTrashcanTimeRequestModel.EMPTY),
                null
        );
    }
}