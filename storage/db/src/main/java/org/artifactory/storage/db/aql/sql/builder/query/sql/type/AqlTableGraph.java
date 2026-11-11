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

package org.artifactory.storage.db.aql.sql.builder.query.sql.type;

import com.google.common.collect.Maps;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.aql.model.AqlTableFieldsEnum;
import org.artifactory.storage.db.aql.sql.builder.links.TableLink;
import org.artifactory.storage.db.aql.sql.builder.links.TableLinkRelation;
import org.artifactory.storage.db.aql.sql.builder.query.sql.SqlTable;
import org.artifactory.storage.db.aql.sql.model.SqlTableEnum;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.artifactory.aql.model.AqlTableFieldsEnum.build_id;
import static org.artifactory.aql.model.AqlTableFieldsEnum.bundle_id;
import static org.artifactory.aql.model.AqlTableFieldsEnum.id;
import static org.artifactory.aql.model.AqlTableFieldsEnum.module_id;
import static org.artifactory.aql.model.AqlTableFieldsEnum.node_id;
import static org.artifactory.aql.model.AqlTableFieldsEnum.sha1;
import static org.artifactory.aql.model.AqlTableFieldsEnum.sha1_actual;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.artifact_bundles;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.build_artifacts;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.build_dependencies;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.build_modules;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.build_promotions;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.build_props;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.bundle_files;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.module_props;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.node_meta_infos;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.node_props;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.nodes;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.stats;
import static org.artifactory.storage.db.aql.sql.model.SqlTableEnum.stats_remote;

/**
 * The class contains the relations between the tables.
 * It is being used to find the shortest path between to tables to minimize the number of joins.
 *
 * @author Gidi Shabat
 */
public class AqlTableGraph {
    public static final Map<SqlTableEnum, TableLink> tablesLinksMap;

    static {
        //Create Tables and links
        TableLink nodesTable = new TableLink(nodes);
        TableLink statisticsTable = new TableLink(stats);
        TableLink statisticsRemoteTable = new TableLink(stats_remote);
        TableLink nodesProps = new TableLink(node_props);
        TableLink nodesMetaInfos = new TableLink(node_meta_infos);
        TableLink builds = new TableLink(SqlTableEnum.builds);
        TableLink buildsProps = new TableLink(build_props);
        TableLink buildsModules = new TableLink(build_modules);
        TableLink buildsPromotions = new TableLink(build_promotions);
        TableLink buildsArtifacts = new TableLink(build_artifacts);
        TableLink buildsDependencies = new TableLink(build_dependencies);
        TableLink buildModuleProperties = new TableLink(module_props);
        TableLink releaseBundles = new TableLink(artifact_bundles);
        TableLink releaseBundleFiles = new TableLink(bundle_files);
        nodesTable.addLink(node_id, nodesProps, node_id);
        nodesTable.addLink(sha1_actual, buildsArtifacts, sha1);
        nodesTable.addLink(sha1_actual, buildsDependencies, sha1);
        nodesTable.addLink(node_id, statisticsTable, node_id);
        nodesTable.addLink(node_id, statisticsRemoteTable, node_id);
        nodesTable.addLink(node_id, nodesMetaInfos, node_id);
        statisticsTable.addLink(node_id, statisticsRemoteTable, node_id);
        buildsModules.addLink(module_id, buildsArtifacts, module_id);
        buildsModules.addLink(module_id, buildsDependencies, module_id);
        buildsModules.addLink(module_id, buildModuleProperties, module_id);
        buildsModules.addLink(build_id, builds, build_id);
        builds.addLink(build_id, buildsProps, build_id);
        nodesProps.addLink(node_id, nodesProps, node_id);
        buildsProps.addLink(build_id, buildsProps, build_id);
        buildModuleProperties.addLink(module_id, buildModuleProperties, module_id);
        builds.addLink(build_id, buildsPromotions, build_id);
        buildsPromotions.addLink(build_id, builds, build_id);
        releaseBundles.addLink(id, releaseBundleFiles, bundle_id);
        releaseBundleFiles.addLink(bundle_id, releaseBundles, id);
        releaseBundleFiles.addLink(node_id, nodesTable, node_id);
        nodesTable.addLink(node_id, releaseBundleFiles, node_id);


        //Fill the tables map
        Map<SqlTableEnum, TableLink> map = Maps.newHashMap();
        map.put(stats, statisticsTable);
        map.put(stats_remote, statisticsRemoteTable);
        map.put(nodes, nodesTable);
        map.put(node_props, nodesProps);
        map.put(node_meta_infos, nodesMetaInfos);
        map.put(build_dependencies, buildsDependencies);
        map.put(build_artifacts, buildsArtifacts);
        map.put(build_modules, buildsModules);
        map.put(module_props, buildModuleProperties);
        map.put(SqlTableEnum.builds, builds);
        map.put(build_props, buildsProps);
        map.put(build_promotions, buildsPromotions);
        map.put(artifact_bundles, releaseBundles);
        map.put(bundle_files, releaseBundleFiles);
        tablesLinksMap = Collections.unmodifiableMap(map);
    }

    public static AqlTableFieldsEnum getFromFieldToProperties(SqlTableEnum table) {
        TableLink tableLink = tablesLinksMap.get(table);
        List<TableLinkRelation> relations = tableLink.getRelations();
        for (TableLinkRelation relation : relations) {
            if (relation.getToTable().getTable().getTable() == module_props ||
                    relation.getToTable().getTable().getTable() == node_props ||
                    relation.getToTable().getTable().getTable() == build_props) {
                return relation.getFromField();
            }
        }
        return null;
    }
    public static SqlTable getLinkTable(SqlTableEnum fromTable, AqlDomainEnum toDomain) {
        TableLink tableLink = tablesLinksMap.get(fromTable);
        List<TableLinkRelation> relations = tableLink.getRelations();
        for (TableLinkRelation relation : relations) {
            if (relation.getToTable().getTable().getTable() == domainToTable(toDomain)) {
                return relation.getToTable().getTable();
            }
        }
        return null;
    }

    public static SqlTableEnum domainToTable(AqlDomainEnum domainEnum) {
        return switch (domainEnum) {
            case items -> SqlTableEnum.nodes;
            case properties -> SqlTableEnum.node_props;
            case itemInfos -> SqlTableEnum.node_meta_infos;
            case statistics -> SqlTableEnum.stats;
            case builds -> SqlTableEnum.builds;
            case buildProperties -> SqlTableEnum.build_props;
            case artifacts -> SqlTableEnum.build_artifacts;
            case dependencies -> SqlTableEnum.build_dependencies;
            case modules -> SqlTableEnum.build_modules;
            case moduleProperties -> SqlTableEnum.module_props;
            case buildPromotions -> SqlTableEnum.build_promotions;
            case releaseBundles -> SqlTableEnum.artifact_bundles;
            case releaseBundleFiles -> SqlTableEnum.bundle_files;
        };
    }

}
