package org.artifactory.ui.rest.service.utils.validation;

import org.artifactory.common.ConstantValues;
import org.artifactory.rest.common.service.ArtifactoryRestRequest;
import org.artifactory.rest.common.service.RestResponse;
import org.artifactory.rest.common.service.RestService;
import org.springframework.stereotype.Component;

/**
 * @author Hussein Alshqerat
 */
@Component
public class InRangeValidatorService implements RestService<Void> {

    @Override
    public void execute(ArtifactoryRestRequest<Void> request, RestResponse response) {
        long defaultValue = 0;
        String key = null;
        try {
            key = request.getQueryParamByKey("key");
            defaultValue = ConstantValues.valueOf(key).getLong();
            long value = Long.parseLong(request.getQueryParamByKey("value"));
            if (value > defaultValue || value <= 0) {
                response.error("The value must fall within the range of 1 to " + defaultValue + ".");
            } else {
                response.info("Value validated.");
            }
        } catch (NumberFormatException e) {
            response.error("Value must be a numerical value in the rage of 1 - " + defaultValue + ".");
        } catch (IllegalArgumentException e) {
            response.error("The key: " + key + " dose not exist.");
        }
    }
}
