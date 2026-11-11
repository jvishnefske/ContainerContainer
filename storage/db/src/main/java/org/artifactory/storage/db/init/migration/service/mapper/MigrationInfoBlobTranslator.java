package org.artifactory.storage.db.init.migration.service.mapper;

import org.artifactory.storage.db.init.migration.model.MigrationInfoBlob;
import org.jfrog.common.JsonUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Named;

/**
 * @author Uriah Levy
 */
@Mapper
@Named("MigrationInfoBlobTranslator")
public class MigrationInfoBlobTranslator {

    @Named("InfoBlobModelToByteArray")
    byte[] infoBlobModelToByteArray(MigrationInfoBlob blob) {
        if (blob != null) {
            return JsonUtils.getInstance().valueToByteArray(blob);
        }
        return new byte[0];
    }
}
