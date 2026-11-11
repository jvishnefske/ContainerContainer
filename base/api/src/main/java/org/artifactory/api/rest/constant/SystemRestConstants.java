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

package org.artifactory.api.rest.constant;

/**
 * @author yoavl
 */
public interface SystemRestConstants {
    String PATH_ROOT = "system";
    String PATH_IMPORT = "import";
    String PATH_EXPORT = "export";
    String PATH_CONFIGURATION = "configuration";
    String PATH_SECURITY = "security";
    String PATH_CERTIFICATES = "certificates";
    String PATH_STORAGE = "storage";
    String PATH_STORAGE_COMPRESS = "compress";
    String PATH_REPOSITORIES = "repositories";
    String PATH_STATUS = "status";
    String PATH_VERSION = "version";
    String PATH_VERSION_SERVICE = PATH_VERSION + "/service";
    String PATH_VERSION_PRODUCT = PATH_VERSION + "/product";
    String PATH_PING = "ping";
    String PATH_METRICS = PATH_ROOT + "/metrics";
    String PATH_ENCRYPT = "encrypt";
    String PATH_DECRYPT = "decrypt";
    String PATH_GC = "gc";
    // Artifactory license
    String PATH_LICENSE = "license";
    String PATH_NEW_LICENSES = "licenses";
    String PATH_REPLICATIONS = "replications";
    String PATH_GATEWAY = PATH_ROOT + "/gateway";
    String PATH_OPENID = PATH_GATEWAY + "/openid";
    String OPENID_LOGIN_RESOURCE = "login";
    String OPENID_LOGOUT_RESOURCE = "logout";
    String OPENID_TOKEN_RESOURCE = "token";
    String OPENID_USERINFO_RESOURCE = "userinfo";
    String OPENID_LOGIN_PATH = PATH_OPENID + "/" + OPENID_LOGIN_RESOURCE;
    String OPENID_LOGOUT_PATH = PATH_OPENID + "/" + OPENID_LOGOUT_RESOURCE;
    String OPENID_TOKEN_PATH = PATH_OPENID + "/" + OPENID_TOKEN_RESOURCE;
    String OPENID_USERINFO_PATH = PATH_OPENID + "/" + OPENID_USERINFO_RESOURCE;
    String PATH_VERIFY_CONNECTION = "verifyconnection";
    String MT_VERSION_RESULT = RestConstants.MT_ARTIFACTORY_APP + PATH_ROOT + ".Version+json";
    String MT_IMPORT_SETTINGS = RestConstants.MT_ARTIFACTORY_APP + PATH_ROOT + ".ImportSettings+json";
    String MT_EXPORT_SETTINGS = RestConstants.MT_ARTIFACTORY_APP + PATH_ROOT + ".ExportSettings+json";
    String PATH_SEARCH = "search";
    String PATH_USAGE = "usage";
    String PATH_SEND_MAIL = "send_mail";
    String PATH_METADATA_SERVER = "metadata_server";
    String JFROG_RELEASE = "release";
    String PATH_BLOB = "blob/info";
    String PATH_TRUSTED_KEYS = PATH_SECURITY + "/keys/trusted";
    String PATH_KEY_PAIRS = PATH_SECURITY + "/keypair";
    String PATH_KEY_PAIRS_PER_REPO = "public/repositories/";
    String BUNDLE_TRANSACTION = "bundle/transaction";
    String BUNDLE_OPEN_TRANSACTION_V2 = "bundle/transaction/open";
    String BUNDLE_TRANSACTION_CLOSE = "bundle/transaction/close";
    String BUNDLE_TRANSACTION_CLOSE_ASYNC = "bundle/transaction/async/close";
    String BUNDLE_CHECK_CLOSE_STATUS = "bundle/transaction/async/close/status";
    String PATH_CHECKUP = "checkup";
    String SERVICE = "service";
    String SERVICE_INFO = PATH_ROOT + "/" + SERVICE;
    String SERVERS_STATUS = PATH_ROOT + "/nodes";
    String PATH_ACCESS_PROXY = "/api/access/";
    String REGISTRY = "registry";
    String PATH_SIGNED_URLS = "signed/url";
    String PATH_PARAM_SIGNATURE = "sig";
    String PATH_SUPPORT_BUNDLE = "support/bundle";
    String PATH_SUPPORT_BUNDLES = "support/bundles";
    String PATH_LOGS = PATH_ROOT + "/logs";
    String PATH_CONFIG = "config";
    String PATH_DATA = "data";
    String PATH_STORE = "store";
    String PATH_OBSERVABILITY = "v1/system/logs";
    String PATH_LOGSHIPPING = PATH_OBSERVABILITY + "/generic";
    String PATH_USAGE_LOGS = PATH_OBSERVABILITY + "/usage";
    String PATH_BILLING_LOGS = PATH_OBSERVABILITY + "/billing";
    String PATH_SERVICE_ID = "service_id";
    String PATH_CURATION = "curation";
    String PATH_CURATION_SETTINGS = "settings";
    String PATH_CURATION_REPOS = "repositories";
    String PATH_CURATION_STATUS = "status";
    String PATH_CURATION_REPO_TYPES = "types";
    String PATH_CURATION_INVALIDATE_PACKAGE_STATUS_CACHE = "invalidatePackageStatusCache";
    String PATH_CURATION_AUDIT = "audit";
    String PATH_CURATION_INTERFACE_SETTINGS = "interfaceSettings";
    String PATH_QRL = "v1/" + PATH_ROOT + "/query_rate_limiter";
    String PATH_RRL = "v1/" + PATH_ROOT + "/request_rate_limiter";
    String PATH_RRL_V2 = "v2/" + PATH_ROOT + "/request_rate_limiter";
    String PATH_READINESS = "/artifactory/api/v1/" + PATH_ROOT + "/readiness";
    String PATH_PRESTOP = "preStop";
    String PATH_FULL_SYNC_REPO = "fullsync/repo";
    String PATH_DRY_FULL_SYNC_REPO = "dryfullsync/repo";
    String PATH_DEBUG = PATH_ROOT + "/debug";
    String THREAD_DUMP = "thread_dump";
    String PATH_FULL_SYNC_SYSTEM_REPO = "fullsync/systemRepo";
    String PATH_LOCKS = "locks";
    String PATH_LOCKS_LOCAL = PATH_LOCKS + "/local";

}
