/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.ui.rest.model.admin.security.oauth;

import org.artifactory.addon.oauth.OAuthProvidersTypeEnum;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Gidi Shabat
 */
@SuppressWarnings("squid:S00115") //UI Model
public enum OAuthUIProvidersTypeEnum {
    //Second set of values is for ui placeholders

    github("GitHub", OAuthProvidersTypeEnum.github,
            new String[] {Constants.API_URL, Constants.AUTH_URL, Constants.TOKEN_URL, "basicUrl"},
            new String[] {"https://api.github.com/user", "https://github.com/login/oauth/authorize",
                    "https://github" + ".com/login/oauth/access_token", "https://github.com/"},
            new String[] {"<base_url>/api/v3/user", "<base_url>/login/oauth/authorize",
                    "<base_url>/login/oauth/access_token", null}),

    google("Google", OAuthProvidersTypeEnum.google,
            new String[] {Constants.API_URL, Constants.AUTH_URL, Constants.TOKEN_URL, "domain"},
            new String[] {"https://www.googleapis.com/oauth2/v1/userinfo",
                    "https://accounts.google.com/o/oauth2/auth", "https://www.googleapis.com/oauth2/v3/token", null},
            new String[] {null, null, null, null}),

    cloudfoundry("Cloud Foundry", OAuthProvidersTypeEnum.cloudfoundry,
            new String[] {Constants.API_URL, Constants.AUTH_URL, Constants.TOKEN_URL},
            new String[] {null, null, null},
            new String[] {"<base_url>/userinfo", "<base_url>/oauth/authorize", "<base_url>/oauth/token"}),

    openId("OpenID", OAuthProvidersTypeEnum.openId,
            new String[] {Constants.API_URL, Constants.AUTH_URL, Constants.TOKEN_URL},
            new String[] {null, null, null},
            new String[] {null, null, null});

    private static final Set<String> PROVIDER_TYPE_NAMES = Arrays.stream(values())
            .map(type -> type.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());

    private String signature;
    private OAuthProvidersTypeEnum providerType;
    private String[] mandatoryFields;
    private String[] fieldsValues;
    private String[] fieldHolders;

    OAuthUIProvidersTypeEnum(String signature, OAuthProvidersTypeEnum providerType, String[] mandatoryFields,
            String[] fieldsValues, String[] fieldHolders) {
        this.providerType = providerType;
        this.signature = signature;
        this.mandatoryFields = mandatoryFields;
        this.fieldsValues = fieldsValues;
        this.fieldHolders = fieldHolders;
    }

    public OAuthProviderInfo getProviderInfo() {
        OAuthProviderInfo oAuthProviderInfo = new OAuthProviderInfo();
        oAuthProviderInfo.setFieldHolders(fieldHolders);
        oAuthProviderInfo.setFieldsValues(fieldsValues);
        oAuthProviderInfo.setDisplayName(signature);
        oAuthProviderInfo.setType(name());
        oAuthProviderInfo.setMandatoryFields(mandatoryFields);
        return oAuthProviderInfo;
    }

    public OAuthProvidersTypeEnum getProviderType() {
        return providerType;
    }

    public String getSignature() {
        return signature;
    }

    public String[] getMandatoryFields() {
        return mandatoryFields;
    }

    public String[] getFieldsValues() {
        return fieldsValues;
    }

    public String[] getFieldHolders() {
        return fieldHolders;
    }

    public static boolean isSupportedProviderType(String providerType) {
        return providerType != null && PROVIDER_TYPE_NAMES.contains(providerType.toLowerCase(Locale.ROOT));
    }

    public static OAuthUIProvidersTypeEnum fromProviderType(OAuthProvidersTypeEnum providerType) {
        for (OAuthUIProvidersTypeEnum oAuthUIProvidersTypeEnum : values()) {
            if (providerType == oAuthUIProvidersTypeEnum.getProviderType()) {
                return oAuthUIProvidersTypeEnum;
            }
        }
        return null;
    }

    private static class Constants {
        public static final String AUTH_URL = "authUrl";
        public static final String API_URL = "apiUrl";
        public static final String TOKEN_URL = "tokenUrl";
    }
}
