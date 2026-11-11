package org.artifactory.repo.service.trash.rdtt;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemoveDirsTrashcanTimeRequestModelTest {
    @ParameterizedTest
    @MethodSource("requestModels")
    void ctor(RemoveDirsTrashcanTimeRequestModel requestModel, int numberOfViolations) {
        try(ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            Set<ConstraintViolation<RemoveDirsTrashcanTimeRequestModel>> violations = validator.validate(requestModel);
            assertEquals(numberOfViolations, violations.size());
        }
    }

    private static Stream<Arguments> requestModels() {
        return Stream.of(
                Arguments.of(RemoveDirsTrashcanTimeRequestModel.EMPTY, 0),
                Arguments.of(withParams(10, 20L, -1), 0),
                Arguments.of(withParams(10, -20L, 5), 1),
                Arguments.of(withParams(-10, 20L, -2), 1),
                Arguments.of(withParams(-10, -20L, 8), 2)
        );
    }

    @ParameterizedTest
    @MethodSource("missingParams")
    void missingParams(RemoveDirsTrashcanTimeRequestModel requestModel,
            int expBatchSize, long expPause, int expBatchesCount) {
        assertEquals(expBatchSize, requestModel.getBatchSize());
        assertEquals(expPause, requestModel.getPauseBetweenBatchesMs());
        assertEquals(expBatchesCount, requestModel.getBatchesCount());
    }

    private static Stream<Arguments> missingParams() {
        return Stream.of(
                Arguments.of(RemoveDirsTrashcanTimeRequestModel.EMPTY,
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getBatchSize(),
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getPauseBetweenBatchesMs(),
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getBatchesCount()),
                Arguments.of(withParams(null, null, null),
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getBatchSize(),
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getPauseBetweenBatchesMs(),
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getBatchesCount()),

                Arguments.of(withParams(null, null, 1),
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getBatchSize(),
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getPauseBetweenBatchesMs(),
                        1),

                Arguments.of(withParams(null, 1L, null),
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getBatchSize(),
                        1L,
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getBatchesCount()),

                Arguments.of(withParams(1, null, null),
                        1,
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getPauseBetweenBatchesMs(),
                        RemoveDirsTrashcanTimeRequestModel.EMPTY.getBatchesCount())
        );
    }

    private static RemoveDirsTrashcanTimeRequestModel withParams(Integer batchSize,
            Long pauseBetweenBatchesMs, Integer batchesCount) {
        RemoveDirsTrashcanTimeRequestModel res = new RemoveDirsTrashcanTimeRequestModel();
        if(batchSize != null)
            res.setBatchSize(batchSize);
        if(pauseBetweenBatchesMs != null)
            res.setPauseBetweenBatchesMs(pauseBetweenBatchesMs);
        if(batchesCount != null)
            res.setBatchesCount(batchesCount);
        return res;
    }
}