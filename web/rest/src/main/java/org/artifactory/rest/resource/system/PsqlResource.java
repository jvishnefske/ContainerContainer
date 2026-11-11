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

package org.artifactory.rest.resource.system;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.artifactory.activity.ActivityDetails;
import org.artifactory.api.context.ContextHelper;
import org.artifactory.api.rest.constant.ArtifactRestConstants;
import org.artifactory.api.rest.constant.HaRestConstants;
import org.artifactory.api.rest.constant.RestApiGroup;
import org.artifactory.api.security.AuthorizationService;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.config.db.ArtifactoryDbProperties;
import org.artifactory.storage.db.util.ArtifactoryJdbcHelper;
import org.artifactory.storage.db.version.converter.SafeIndexConcurrentCreator;
import org.artifactory.storage.fs.service.PropertiesService;
import org.artifactory.util.AolUtils;
import org.jfrog.converter.DbConverter;
import org.jfrog.converter.DbVersion;
import org.jfrog.converter.model.ChangeLogContext;
import org.jfrog.converter.model.ConverterApplyStatus;
import org.jfrog.converter.model.ConverterExecutionType;
import org.jfrog.storage.DbType;
import org.springframework.stereotype.Component;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.jfrog.converter.model.ConverterApplyStatus.COMPLETED;
import static org.jfrog.converter.model.ConverterApplyStatus.SKIPPED;
import static org.jfrog.converter.model.ConverterExecutionType.SEQ_SYNC;
import static org.jfrog.converter.util.PreDefinedConditions.dbSpecific;
import static org.jfrog.storage.DbType.POSTGRESQL;

/**
 * @author Shay Bagants
 */
@RolesAllowed({AuthorizationService.ROLE_ADMIN, HaRestConstants.ROLE_HA})
@Slf4j
@Component
@NoArgsConstructor
@AllArgsConstructor
@ActivityDetails(group = RestApiGroup.MANAGEMENT_API)
public class PsqlResource {
    private PropertiesService propertiesService;

    @GET
    @Path("properties/listInvalidProperties")
    @Produces("application/json")
    public Response listInvalidProperties() {
        Map<String, List<String>> allLongProps = getAllLongProps();
        PostgresqlPropTrimAndIndexStatusModel responseModel = new PostgresqlPropTrimAndIndexStatusModel();
        responseModel.setProperties(allLongProps);
        return Response.ok().entity(responseModel).build();
    }

    @POST
    @Path("properties/fix")
    @Produces("application/json")
    public Response trimValuesAndFixIndex(@QueryParam(ArtifactRestConstants.PARAM_DRY_RUN) int dryRun) {
        if (!isPostgresqlDb()) {
            PostgresqlPropTrimAndIndexStatusModel responseModel = new PostgresqlPropTrimAndIndexStatusModel(null,
                    "Resource is available for PostgreSQL database only", null);
            responseModel.setError("Resource is available for PostgreSQL database only");
            return Response.status(BAD_REQUEST.getStatusCode()).entity(responseModel).build();
        }
        Map<String, List<String>> longProps = getAllLongProps();
        Map<String, List<String>> expectedTrimmedProps = Maps.newHashMap();
        // populate the expectedTrimmedProps map like the properties were trimmedtrimmed
        simulateTrimPropValues(longProps, expectedTrimmedProps);
        return handleAndBuildResponse(dryRun, expectedTrimmedProps);
    }

    /**
     * Handle psql property values fix request. on dryrun, simulate how it should be, otherwise, trimming property
     * values to the max allowed value length and fixing the node_props indexes. Both applies for Postgresql only.
     */
    @VisibleForTesting
    Response handleAndBuildResponse(@QueryParam(ArtifactRestConstants.PARAM_DRY_RUN) int dryRun,
            Map<String, List<String>> expectedTrimmedProps) {
        if (dryRun == 0) {
            int trimmedPropValues = propertiesService.trimPropertyValuesToAllowedLength();
            if (AolUtils.isAol()) {
                PostgresqlPropTrimAndIndexStatusModel responseModel = new PostgresqlPropTrimAndIndexStatusModel();
                responseModel.setProperties(expectedTrimmedProps);
                responseModel.setIndexFixed(false);
                responseModel.setError("The properties have been trimmed," +
                        " but the index has not been fixed due to the action being in a SaaS environment");
                return Response.status(OK.getStatusCode()).entity(responseModel).build();
            }

            boolean indexFixed = fixIndex();
            PostgresqlPropTrimAndIndexStatusModel responseModel = new PostgresqlPropTrimAndIndexStatusModel();
            responseModel.setIndexFixed(indexFixed);
            if (!indexFixed) {
                String errorMsg = "Failed to fix index on 'node_props' table. " + (isPostgresqlDb() ?
                        "See log file for further details" : "This resource is intended for PostgreSQL only");
                responseModel.setError(errorMsg);
            }
            if (trimmedPropValues > 0) {
                responseModel.setProperties(expectedTrimmedProps);
            } else {
                log.info("No properties should be trimmed");
            }
            return Response.status(indexFixed ? OK.getStatusCode() : INTERNAL_SERVER_ERROR.getStatusCode())
                    .entity(responseModel).build();

        }

        PostgresqlPropTrimAndIndexStatusModel responseModel = new PostgresqlPropTrimAndIndexStatusModel();
        responseModel.setProperties(expectedTrimmedProps);
        responseModel.setIndexFixed(false);
        return Response.ok().entity(responseModel).build();
    }

    @VisibleForTesting
    boolean fixIndex() {
        if (!isPostgresqlDb()) {
            return false;
        }

        try {
            List<ConverterApplyStatus> converterApplyStatus = executeConverter();
            return converterApplyStatus.stream().allMatch(val -> isCompleted(val) || isSkipped(val));
        } catch (Exception e) {
            log.error("Failed to perform conversion. ", e);
            return false;
        }
    }

    @VisibleForTesting
    boolean isCompleted(ConverterApplyStatus status) {
        return status.equals(COMPLETED);
    }

    @VisibleForTesting
    boolean isSkipped(ConverterApplyStatus status) {
        return status.equals(SKIPPED);
    }

    private boolean isPostgresqlDb() {
        return getDbType() == DbType.POSTGRESQL;
    }

    private DbType getDbType() {
        return ContextHelper.get().beanForType(ArtifactoryDbProperties.class).getDbType();
    }

    private ArtifactoryJdbcHelper jdbcHelper() {
        return ContextHelper.get().beanForType(ArtifactoryJdbcHelper.class);
    }

    private String trimPropIfNeeded(String val) {
        int maxAllowedPropSize = getMaxAllowedPropSize();
        return val.length() > maxAllowedPropSize ? val.substring(0, getMaxAllowedPropSize()) : val;
    }

    private Map<String, List<String>> getAllLongProps() {
        return propertiesService.getAllPropsLongerThan(getMaxAllowedPropSize());
    }

    private void simulateTrimPropValues(Map<String, List<String>> longProps,
            Map<String, List<String>> expectedTrimmedProps) {
        longProps.forEach((key, valuesList) -> {
            List<String> trimmedValued = valuesList.stream()
                    .map(this::trimPropIfNeeded)
                    .collect(Collectors.toList());
            expectedTrimmedProps.put(key, trimmedValued);
        });
    }

    private int getMaxAllowedPropSize() {
        return ConstantValues.dbPostgresPropertyValueMaxSize.getInt();
    }

    @VisibleForTesting
    List<ConverterApplyStatus> executeConverter() throws SQLException {
        final List<SafeIndexConcurrentCreator> converters = createConverters();
        final List<ConverterApplyStatus> statuses = new ArrayList<>();
        for (SafeIndexConcurrentCreator dbVersion : converters) {
            try (ChangeLogContext context = new ChangeLogContext(jdbcHelper().getDataSource().getConnection(),
                    getDbType(),
                    new InnerDbVersion())) {
                statuses.add(dbVersion.convert(context).getStatus());
            }
        }
        return statuses;
    }

    private List<SafeIndexConcurrentCreator> createConverters() throws SQLException {
        return List.of(new SafeIndexConcurrentCreator("node_props_prop_value_key_idx", "node_props",
                        List.of("prop_value varchar_pattern_ops", "prop_key varchar_pattern_ops"),
                        dbSpecific(POSTGRESQL)),
                new SafeIndexConcurrentCreator("node_props_prop_key_value_idx", "node_props",
                        List.of("prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"),
                        dbSpecific(POSTGRESQL)),
                new SafeIndexConcurrentCreator("node_props_node_prop_value_idx", "node_props",
                        List.of("node_id", "prop_key varchar_pattern_ops", "prop_value varchar_pattern_ops"),
                        dbSpecific(POSTGRESQL)));
    }

    private class InnerDbVersion implements DbVersion {

        @Override
        public String name() {
            return "manualTrigger";
        }

        @Override
        public String version() {
            return "manulTrigger";
        }

        @Override
        public String description() {
            return "manualTrigger DB converter";
        }

        @Override
        public ConverterExecutionType type() {
            return SEQ_SYNC;
        }

        @Override
        public DbConverter converter() {
            return null;
        }

        @Override
        public String systemVersion() {
            return null;
        }
    }
}
