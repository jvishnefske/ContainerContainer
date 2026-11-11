package com.jfrog.build.dao.util;

public class BuildQueries {

    private BuildQueries() {
    }

    public static final String MODULE_DEPENDENCY_DIFF_QUERY = "select * from (\n" +
            // get module dependencies which are in build b and not in a - removed from last build
            "         SELECT *\n" +
            "         from (\n" +
            "                  SELECT build_dependencies.dependency_name_id,\n" +
            "                         build_dependencies.dependency_type,\n" +
            "                         build_dependencies.sha1 sh,\n" +
            "                         build_dependencies.dependency_scopes,\n" +
            "                         'Removed'               status\n" +
            "                  FROM build_dependencies\n" +
            "                           left join build_modules on build_modules.module_id = build_dependencies" +
            ".module_id\n" +
            "                           left join builds on build_modules.build_id = builds.build_id\n" +
            "                  where build_dependencies.dependency_name_id not in\n" +
            "                        (SELECT distinct build_dependencies.dependency_name_id\n" +
            "                         FROM build_dependencies\n" +
            "                                  left join build_modules on build_modules.module_id = " +
            "build_dependencies.module_id\n" +
            "                                  left join builds on build_modules.build_id = builds.build_id\n" +
            "                         where builds.build_number = ?\n" +
            "                           and builds.build_date = ?\n" +
            "                           and builds.repo = ?\n" +
            "                           and build_modules.module_name_id = ?)\n" +
            "                    and builds.build_number = ?\n" +
            "                    and builds.build_date = ?\n" +
            "                    and builds.repo = ?\n" +
            "                    and build_modules.module_name_id = ?" +
            "\n" +
            // get module dependencies which are in build a and not in b - added to last build
            "                  union\n" +
            "                  SELECT build_dependencies.dependency_name_id,\n" +
            "                         build_dependencies.dependency_type,\n" +
            "                         build_dependencies.sha1 sh,\n" +
            "                         build_dependencies.dependency_scopes,\n" +
            "                         'New'                   status\n" +
            "                  FROM build_dependencies\n" +
            "                           left join build_modules on build_modules.module_id = build_dependencies" +
            ".module_id\n" +
            "                           left join builds on build_modules.build_id = builds.build_id\n" +
            "                  where build_dependencies.dependency_name_id not in\n" +
            "                        (SELECT distinct build_dependencies.dependency_name_id\n" +
            "                         FROM build_dependencies\n" +
            "                                  left join build_modules on build_modules.module_id = " +
            "build_dependencies.module_id\n" +
            "                                  left join builds on build_modules.build_id = builds.build_id\n" +
            "                         where builds.build_number = ?\n" +
            "                           and builds.build_date = ?\n" +
            "                           and builds.repo = ?\n" +
            "                           and build_modules.module_name_id = ?)\n" +
            "                    and builds.build_number = ?\n" +
            "                    and builds.build_date = ?\n" +
            "                    and builds.repo = ?\n" +
            "                    and build_modules.module_name_id = ?) x" +
            "\n" +
            // get module dependencies which has the same sha1 on both builds - no changes
            "         union\n" +
            "         SELECT o.c, o.dependency_type, o.sh, o.dependency_scopes, o.status\n" +
            "         from (\n" +
            "                  SELECT *\n" +
            "                  from (\n" +
            "                           SELECT build_dependencies.dependency_name_id c,\n" +
            "                                  build_dependencies.dependency_type,\n" +
            "                                  build_dependencies.sha1               sh,\n" +
            "                                  build_dependencies.dependency_scopes,\n" +
            "                                  'Unchanged'                           status1\n" +
            "                           FROM build_dependencies\n" +
            "                                    left join build_modules on build_modules.module_id = " +
            "build_dependencies.module_id\n" +
            "                                    left join builds on build_modules.build_id = builds.build_id\n" +
            "                           where builds.build_number = ?\n" +
            "                             and builds.build_date = ?\n" +
            "                             and builds.repo = ?\n" +
            "                             and build_modules.module_name_id = ?) x\n" +
            "                           inner join (\n" +
            "                      SELECT *\n" +
            "                      from (\n" +
            "                               SELECT build_dependencies.dependency_name_id b,\n" +
            "                                      build_dependencies.dependency_type    type_x,\n" +
            "                                      build_dependencies.sha1               sh2,\n" +
            "                                      build_dependencies.dependency_scopes  scope,\n" +
            "                                      'Unchanged'                           status\n" +
            "                               FROM build_dependencies\n" +
            "                                        left join build_modules on build_modules.module_id = " +
            "build_dependencies.module_id\n" +
            "                                        left join builds on build_modules.build_id = builds.build_id\n" +
            "                               where builds.build_number = ?\n" +
            "                                 and builds.build_date = ?\n" +
            "                                 and builds.repo = ?\n" +
            "                                 and build_modules.module_name_id = ?) t) v on v.sh2 = x.sh) o" +
            "\n" +
            // get module dependencies which has the diff sha1 on both builds - has changes
            "         union\n" +
            "         SELECT o.c, o.dependency_type, o.sh, o.dependency_scopes, o.status\n" +
            "         from (\n" +
            "                  SELECT *\n" +
            "                  from (\n" +
            "                           SELECT build_dependencies.dependency_name_id c,\n" +
            "                                  build_dependencies.dependency_type,\n" +
            "                                  build_dependencies.sha1               sh,\n" +
            "                                  build_dependencies.dependency_scopes,\n" +
            "                                  'Updated'                             status1\n" +
            "                           FROM build_dependencies\n" +
            "                                    left join build_modules on build_modules.module_id = " +
            "build_dependencies.module_id\n" +
            "                                    left join builds on build_modules.build_id = builds.build_id\n" +
            "                           where builds.build_number = ?\n" +
            "                             and builds.build_date = ?\n" +
            "                             and builds.repo = ?\n" +
            "                             and build_modules.module_name_id = ?) x\n" +
            "\n" +
            "                           inner join (\n" +
            "                      SELECT *\n" +
            "                      from (\n" +
            "                               SELECT build_dependencies.dependency_name_id b,\n" +
            "                                      build_dependencies.dependency_type    type_x,\n" +
            "                                      build_dependencies.sha1               sh2,\n" +
            "                                      build_dependencies.dependency_scopes  scop,\n" +
            "                                      'Updated'                             status\n" +
            "                               FROM build_dependencies\n" +
            "                                        left join build_modules on build_modules.module_id = " +
            "build_dependencies.module_id\n" +
            "                                        left join builds on build_modules.build_id = builds.build_id\n" +
            "                               where builds.build_number = ?\n" +
            "                                 and builds.build_date = ?\n" +
            "                                 and builds.repo = ?\n" +
            "                                 and build_modules.module_name_id = ?) t) v on v.b = x.c\n" +
            "                  where v.sh2 != x.sh) o\n" +
            "     ) z";

    public static final String MODULE_ARTIFACT_DIFF_QUERY = "\n" +
            "select * from (\n" +
            // get module artifacts which are in build b and not in a - removed from last build
            "\n" +
            "         SELECT *\n" +
            "         from (\n" +
            "                  SELECT build_artifacts.artifact_name,\n" +
            "                         build_artifacts.artifact_type,\n" +
            "                         build_artifacts.sha1 sh,\n" +
            "                         'Removed'            status\n" +
            "                  FROM build_artifacts\n" +
            "                           left join build_modules on build_modules.module_id = build_artifacts" +
            ".module_id\n" +
            "                           left join builds on build_modules.build_id = builds.build_id\n" +
            "                  where build_artifacts.artifact_name not in (SELECT distinct build_artifacts" +
            ".artifact_name\n" +
            "                                                              FROM build_artifacts\n" +
            "                                                                       left join build_modules on " +
            "build_modules.module_id = build_artifacts.module_id\n" +
            "                                                                       left join builds on build_modules" +
            ".build_id = builds.build_id\n" +
            "                                                              where builds.build_number = ?\n" +
            "                                                                and builds.build_date = ?\n" +
            "                                                                and builds.repo = ?\n" +
            "                                                                and build_modules.module_name_id = ?)\n" +
            "                    and builds.build_number = ?\n" +
            "                    and builds.build_date = ?\n" +
            "                    and builds.repo = ?\n" +
            "                    and build_modules.module_name_id = ?" +
            // get module artifacts which are in build a and not in b - added to last build
            "\n" +
            "                  union\n" +
            "                  SELECT build_artifacts.artifact_name,\n" +
            "                         build_artifacts.artifact_type,\n" +
            "                         build_artifacts.sha1 sh,\n" +
            "                         'New'                status\n" +
            "                  FROM build_artifacts\n" +
            "                           left join build_modules on build_modules.module_id = build_artifacts" +
            ".module_id\n" +
            "                           left join builds on build_modules.build_id = builds.build_id\n" +
            "                  where build_artifacts.artifact_name not in (SELECT distinct build_artifacts" +
            ".artifact_name\n" +
            "                                                              FROM build_artifacts\n" +
            "                                                                       left join build_modules on " +
            "build_modules.module_id = build_artifacts.module_id\n" +
            "                                                                       left join builds on build_modules" +
            ".build_id = builds.build_id\n" +
            "                                                              where builds.build_number = ?\n" +
            "                                                                and builds.build_date = ?\n" +
            "                                                                and builds.repo = ?\n" +
            "                                                                and build_modules.module_name_id = ?)\n" +
            "                    and builds.build_number = ?\n" +
            "                    and builds.build_date = ?\n" +
            "                    and builds.repo = ?\n" +
            "                    and build_modules.module_name_id = ?) x" +
            "\n" +
            // get module artifacts which has the same sha1 on both builds - no changes
            "         union\n" +
            "         SELECT o.c, o.artifact_type, o.sh, o.status\n" +
            "         from (\n" +
            "                  SELECT *\n" +
            "                  from (\n" +
            "                           SELECT build_artifacts.artifact_name c,\n" +
            "                                  build_artifacts.artifact_type,\n" +
            "                                  build_artifacts.sha1          sh,\n" +
            "                                  'Unchanged'                   status1\n" +
            "                           FROM build_artifacts\n" +
            "                                    left join build_modules on build_modules.module_id = build_artifacts" +
            ".module_id\n" +
            "                                    left join builds on build_modules.build_id = builds.build_id\n" +
            "                           where builds.build_number = ?\n" +
            "                             and builds.build_date = ?\n" +
            "                             and builds.repo = ?\n" +
            "                             and build_modules.module_name_id = ?) x\n" +
            "                           inner join (\n" +
            "                      SELECT *\n" +
            "                      from (\n" +
            "                               SELECT build_artifacts.artifact_name b,\n" +
            "                                      build_artifacts.artifact_type type_x,\n" +
            "                                      build_artifacts.sha1          sh2,\n" +
            "                                      'Unchanged'                   status\n" +
            "                               FROM build_artifacts\n" +
            "                                        left join build_modules on build_modules.module_id = " +
            "build_artifacts.module_id\n" +
            "                                        left join builds on build_modules.build_id = builds.build_id\n" +
            "                               where builds.build_number = ?\n" +
            "                                 and builds.build_date = ?\n" +
            "                                 and builds.repo = ?\n" +
            "                                 and build_modules.module_name_id = ?) t) v on v.sh2 = x.sh) o" +
            "\n" +
            // get module artifacts which has the diff sha1 on both builds - has changes
            "         union\n" +
            "         SELECT o.c, o.artifact_type, o.sh, o.status\n" +
            "         from (\n" +
            "                  SELECT *\n" +
            "                  from (\n" +
            "                           SELECT build_artifacts.artifact_name c,\n" +
            "                                  build_artifacts.artifact_type,\n" +
            "                                  build_artifacts.sha1          sh,\n" +
            "                                  'Updated'                     status1\n" +
            "                           FROM build_artifacts\n" +
            "                                    left join build_modules on build_modules.module_id = build_artifacts" +
            ".module_id\n" +
            "                                    left join builds on build_modules.build_id = builds.build_id\n" +
            "                           where builds.build_number = ?\n" +
            "                             and builds.build_date = ?\n" +
            "                             and builds.repo = ?\n" +
            "                             and build_modules.module_name_id = ?) x\n" +
            "\n" +
            "                           inner join (\n" +
            "                      SELECT *\n" +
            "                      from (\n" +
            "                               SELECT build_artifacts.artifact_name b,\n" +
            "                                      build_artifacts.artifact_type type_x,\n" +
            "                                      build_artifacts.sha1          sh2,\n" +
            "                                      'Updated'                     status\n" +
            "                               FROM build_artifacts\n" +
            "                                        left join build_modules on build_modules.module_id = " +
            "build_artifacts.module_id\n" +
            "                                        left join builds on build_modules.build_id = builds.build_id\n" +
            "                               where builds.build_number = ?\n" +
            "                                 and builds.build_date = ?\n" +
            "                                 and builds.repo = ?\n" +
            "                                 and build_modules.module_name_id = ?) t) v on v.b = x.c\n" +
            "                  where v.sh2 != x.sh) o) z" +
            "\n";

    public static final String BUILD_BY_NAME_NUMBER_DATE = "\n" +
            "SELECT build_id FROM builds WHERE build_name = ?\n" +
            " AND builds.build_number = ?\n" +
            " AND builds.build_date = ?\n" +
            " AND builds.repo = ?\n";

    public static final String BUILD_ENV_PROPS_BY_BUILD_ID = "\n" +
            "SELECT prop_key, prop_value FROM  build_props\n" +
            "  WHERE build_id  = ?\n " +
            " AND prop_key LIKE 'buildInfo.env.%'";

    public static final String BUILD_SYSTEM_PROPS_BY_BUILD_ID = "\n" +
            "SELECT prop_key, prop_value FROM  build_props\n" +
            " WHERE build_id  = ? \n AND prop_key NOT LIKE 'buildInfo.env.%'";

    private static final String GET_ARTIFACT_NODES_SELECT_CLAUSE =
            "select distinct n.repo,\n" +
                    "                n.node_path,\n" +
                    "                n.node_name,\n" +
                    "                n.node_id,\n" +
                    "                n.depth,\n" +
                    "                n.sha1_actual,\n" +
                    "                n.sha1_original,\n" +
                    "                n.md5_actual,\n" +
                    "                n.md5_original\n" +
                    "from nodes n\n";

    private static final String GET_ARTIFACT_NODES_WHERE_CLAUSE =
            "where ((np100.prop_key = 'build.name' and np100.prop_value = ?)\n" +
                    "    and (np101.prop_key = 'build.number' and np101.prop_value = ?)\n" +
                    "    and n.repo != ?\n" +
                    "    and n.node_type = 1";

    public static final String GET_ARTIFACT_NODES =
            (GET_ARTIFACT_NODES_SELECT_CLAUSE +
                    "         left outer join node_props np100 on np100.node_id = n.node_id\n" +
                    "         left outer join node_props np101 on np101.node_id = n.node_id\n") +
                    GET_ARTIFACT_NODES_WHERE_CLAUSE + ")";

    public static final String GET_ARTIFACT_NODES_BY_BUILD_TIMESTAMP =
            (GET_ARTIFACT_NODES_SELECT_CLAUSE +
                    "         left outer join node_props np100 on np100.node_id = n.node_id\n" +
                    "         left outer join node_props np101 on np101.node_id = n.node_id\n" +
                    "         left outer join node_props np102 on np102.node_id = n.node_id\n") +
                    GET_ARTIFACT_NODES_WHERE_CLAUSE +
                    "    and (np102.prop_key = 'build.timestamp' and np102.prop_value = ?))\n";

    public static final String SEARCH_BUILD = """
            SELECT
                a.build_id,
                a.build_name,
                a.build_number,
                a.build_date,
                a.repo
            FROM builds a
            """;

    public static final String BUILD_ARTIFACTS_QUERY =
            """
                    select  n.repo as repo , n.node_path as path ,
                    n.node_name as name, n.sha1_actual as sha1,
                    n.md5_actual as md5, n.bin_length as bin_length
                    from nodes n
                    where (
                    (
                        (exists(select 1
                                    from node_props npsub
                                    where npsub.node_id = n.node_id
                                      and npsub.prop_key = 'build.name'
                                      and npsub.prop_value = ?))
                        and
                        (exists(select 1
                                    from node_props npsub
                                    where npsub.node_id = n.node_id
                                      and npsub.prop_key = 'build.number'
                                      and npsub.prop_value = ?))
                                and
                                %s
                            n.node_type = 1) and (n.repo <> 'auto-trashcan')
                            )
                    """;
}
