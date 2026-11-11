package org.artifactory.ui.rest.model.admin.configuration.licenses;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.artifactory.rest.common.model.RestModel;
import org.jfrog.common.JsonUtils;

/**
 * @author Nadav Yogev
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LicenseChangeModel implements RestModel {

    private String licenseType;
    private String info;

    public static LicenseChangeModel created(String licenseType) {
        return new LicenseChangeModel(licenseType, "Successfully created " + licenseType + " license");
    }

    public static LicenseChangeModel updated(String licenseType) {
        return new LicenseChangeModel(licenseType, "Successfully updated " + licenseType + " license");
    }

    @Override
    public String toString() {
        return JsonUtils.getInstance().valueToString(this);
    }
}
