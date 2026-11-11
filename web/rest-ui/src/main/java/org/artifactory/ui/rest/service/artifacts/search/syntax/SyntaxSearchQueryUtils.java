package org.artifactory.ui.rest.service.artifacts.search.syntax;

import com.amazonaws.util.CollectionUtils;
import com.google.common.annotations.VisibleForTesting;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.checksum.ChecksumType;
import org.artifactory.common.repo.PackageType;
import org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes.RepositoryType;
import org.artifactory.ui.rest.model.artifacts.search.syntax.DateRange;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Filters;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Query;
import org.artifactory.ui.rest.model.artifacts.search.syntax.State;
import org.artifactory.ui.rest.model.artifacts.search.syntax.Type;
import org.jfrog.storage.JdbcHelper;
import org.jfrog.storage.QueryControls;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.artifactory.ui.rest.service.artifacts.search.syntax.SyntaxSearchUtils.isNull;
import static org.artifactory.util.RepoPathUtils.removeDashCache;

/**
 * @author Igor Usenko
 */
@UtilityClass
@Slf4j
class SyntaxSearchQueryUtils {

    private static final String[] CACHE_SUFFIXES = {"-", "-c", "-ca", "-cac", "-cach", "-cache"};
    private static final int FOLDER_NODE_TYPE = 0;
    private static final int FILE_NODE_TYPE = 1;
    public static final String TIMEOUT_STATE = "57014";
    private static final String COLUMN_MODIFIED = "modified";
    private static final String COLUMN_CREATED = "created";
    private static final String AND_SEPARATOR = " and ";

    private static List<String> split(String term) {
        return List.of(StringUtils.split(term));
    }

    static SqlFragment createReposCondition(String term, boolean isExactMatch) {
        List<String> terms = split(term);
        List<String> parts = terms.stream()
                .flatMap(SEPARATOR_PATTERN::splitAsStream)
                .filter(p -> !p.isEmpty())
                .toList();
        String ftsCondition = createFtsCondition(parts, isExactMatch);

        SqlFragment specialCharsCondition = createRepoSpecialCharsIlikeConditions(terms, isExactMatch);
        SqlFragment cacheRepoExactMatch = createExactRepoMatchesForCachePrefix(terms);
        SqlFragment ftsWithIlike = buildRepoFtsCondition(ftsCondition, specialCharsCondition);
        if (cacheRepoExactMatch.isNotEmpty()) {
            return SqlFragment.formatted("and ((%s) or (%s))", List.of(ftsWithIlike, cacheRepoExactMatch));
        }
        return SqlFragment.formatted("and (%s)", List.of(ftsWithIlike));
    }

    private static SqlFragment buildRepoFtsCondition(String ftsQuery, SqlFragment ilikeCondition) {
        SqlFragment ftsCondition = SqlFragment.of("repo_ts_vector @@ to_tsquery('simple', ?)", List.of(ftsQuery));

        List<SqlFragment> fragments = ilikeCondition.isEmpty() ?
                List.of(ftsCondition) :
                List.of(ftsCondition, ilikeCondition);

        return SqlFragment.join(fragments, AND_SEPARATOR);
    }

    private static SqlFragment createExactRepoMatchesForCachePrefix(List<String> terms) {
        List<SqlFragment> fragments = terms.stream()
                .filter(SyntaxSearchQueryUtils::endsWithCachePrefix)
                .map(SyntaxSearchQueryUtils::extractBaseRepoCondition)
                .filter(SqlFragment::isNotEmpty)
                .toList();
        return SqlFragment.join(fragments, " or ");
    }

    private static boolean endsWithCachePrefix(String term) {
        String lowerTerm = term.toLowerCase();
        return Stream.of(CACHE_SUFFIXES)
                .anyMatch(lowerTerm::endsWith);
    }

    private static SqlFragment extractBaseRepoCondition(String term) {
        int lastHyphen = term.lastIndexOf('-');
        if (lastHyphen > 0) {
            return SqlFragment.of("repository_key = ?", List.of(term.substring(0, lastHyphen)));
        }
        return SqlFragment.EMPTY;
    }

    private static SqlFragment createExactRepoMatch(String term) {
        String repoKey = removeDashCache(term);
        return SqlFragment.of("and (repository_key = ?)", List.of(repoKey));
    }

    static SqlFragment createReposConditionForNodesTable(String term, String tablePrefix) {
        List<String> terms = split(term);
        List<SqlFragment> fragments = terms.stream()
                .map(t -> SqlFragment.of(tablePrefix + "repo=?", List.of(t)))
                .toList();
        SqlFragment joinedWithOr = SqlFragment.join(fragments, " or ");
        return SqlFragment.formatted("and (%s)", List.of(joinedWithOr));
    }

    static SqlFragment createExactReposConditionForNodesTable(String term, String tablePrefix) {
        return SqlFragment.of("and (" + tablePrefix + "repo=?)", List.of(term));
    }

    static SqlFragment createPermittedReposConditionForNodesTable(List<String> permittedRepos,
            String tablePrefix) {
        SqlFragment repositoriesList = SqlFragment.list(permittedRepos.stream()
                .<Object>map(repo -> repo)
                .toList());
        return SqlFragment.formatted("and (" + tablePrefix + "repo IN (%s)) ", List.of(repositoriesList));
    }

    static SqlFragment createStateCondition(State state, String tablePrefix) {
        return switch (state) {
            case null -> SqlFragment.EMPTY;
            case State s when s.type() == Type.STOP -> SqlFragment.EMPTY;
            case State s when s.type() == Type.REPO -> SqlFragment.of(
                    "and (" + tablePrefix + "modified, repository_key) < (?, ?)",
                    List.of(s.modified(), s.id())
            );
            case State s -> SqlFragment.of(
                    "and (" + tablePrefix + "modified, " + tablePrefix + "node_id) < (?, ?)",
                    List.of(s.modified(), Long.valueOf(s.id()))
            );
        };
    }

    static SqlFragment createPathsCondition(String term, String tablePrefix) {
        return SqlFragment.formatted("and %s ", List.of(createPathCondition(term, tablePrefix)));
    }

    static SqlFragment createChildrenPathsCondition(String term, String tablePrefix) {
        String columnName = tablePrefix + "node_path";
        String template = "and (%s = ? or %s like ?)".formatted(columnName, columnName);
        return SqlFragment.of(template, List.of(term, term + "/%"));
    }

    static SqlFragment createPathCondition(String term, String tablePrefix) {
        String stripped = stripSlashes(term);
        if (stripped == null) {
            return SqlFragment.EMPTY;
        }
        SqlFragment fullPathCondition = createFullPathIlikeConditionsForFolder(
                List.of(preserveTrailingSlash(term, stripped)), tablePrefix);
        if (stripped.contains("/")) {
            SqlFragment ftsCondition =
                    createPathsFtsCondition(createPathsFtsQuery(List.of(stripped), false), tablePrefix);
            List<SqlFragment> fragments = Stream.of(ftsCondition, fullPathCondition)
                    .filter(s -> !s.isEmpty())
                    .toList();
            return SqlFragment.join(fragments, AND_SEPARATOR);
        }
        return SqlFragment.formatted("(%s)", List.of(fullPathCondition));
    }

    private static String preserveTrailingSlash(String original, String stripped) {
        return original.endsWith("/") && !stripped.isEmpty() ? stripped + "/" : stripped;
    }

    static SqlFragment createPathsConditionForFile(String term, String tablePrefix) {
        List<String> words = List.of(term);
        SqlFragment ftsCondition = createPathsFtsCondition(createPathsFtsQuery(words, false), tablePrefix);
        SqlFragment specialCharsCondition = createSpecialCharsIlikeConditions(words, tablePrefix);
        SqlFragment combined = SqlFragment.join(Stream.of(ftsCondition, specialCharsCondition)
                .filter(s -> !s.isEmpty()).toList(), AND_SEPARATOR);
        return SqlFragment.formatted("and %s", List.of(combined));
    }

    static SqlFragment createPathsFtsCondition(String query, String tablePrefix) {
        return SqlFragment.of(tablePrefix + "node_ts_vector @@ to_tsquery('simple', ?)", List.of(query));
    }

    static final Pattern SLASH_TRIM_PATTERN = Pattern.compile("(^/+)|(/+$)");

    static String stripSlashes(String text) {
        if (text == null) {
            return null;
        }
        return SLASH_TRIM_PATTERN.matcher(text).replaceAll("");
    }

    static SqlFragment createPathsConditionForText(String text, String tablePrefix) {
        return createTextCondition(text, tablePrefix, false);
    }

    static SqlFragment createFilesConditionForText(String text, String tablePrefix) {
        return createTextCondition(text, tablePrefix, true);
    }

    private static SqlFragment createTextCondition(String text, String tablePrefix, boolean withTrailingSpace) {
        List<String> words = split(text);

        SqlFragment ftsCondition = SqlFragment.EMPTY;
        String pathsFtsQuery = createPathsFtsQuery(words, false);
        if (!pathsFtsQuery.isEmpty()) {
            ftsCondition = createPathsFtsCondition(pathsFtsQuery, tablePrefix);
        }

        SqlFragment specialCharsCondition = createSpecialCharsIlikeConditions(words, tablePrefix);

        SqlFragment joined = SqlFragment.join(
                Stream.of(ftsCondition, specialCharsCondition)
                        .filter(s -> !s.isEmpty())
                        .toList(), AND_SEPARATOR
        );

        if (joined.isEmpty()) {
            return joined;
        }
        return withTrailingSpace ?
                SqlFragment.formatted("and (%s) ", List.of(joined)) :
                SqlFragment.formatted("and (%s)", List.of(joined));
    }

    static SqlFragment createShaCondition(String sha, String tablePrefix) {
        SqlFragment condition = createShasCondition(List.of(sha), tablePrefix);
        return condition.isEmpty() ?
                SqlFragment.EMPTY :
                SqlFragment.formatted("and %s ", List.of(condition));
    }

    static SqlFragment createExactMatchTextCondition(String text, String tablePrefix) {
        List<String> words = split(text);

        String ftsQuery = createPathsFtsQuery(words, true);
        SqlFragment ftsCondition = SqlFragment.EMPTY;
        if (!ftsQuery.isEmpty()) {
            ftsCondition = createPathsFtsCondition(ftsQuery, tablePrefix);
        }

        EscapedText escaped = escapeForIlike(text, true);
        final SqlFragment ilikeCondition = createExactMatchIlikeCondition(escaped);

        List<SqlFragment> fragments = Stream.of(ftsCondition, ilikeCondition)
                .filter(SqlFragment::isNotEmpty)
                .toList();

        SqlFragment combined = SqlFragment.join(fragments, AND_SEPARATOR);
        return SqlFragment.formatted("and (%s)", List.of(combined));
    }

    private static SqlFragment createExactMatchIlikeCondition(final EscapedText escaped) {
        final String esc = escaped.escapeClause;
        if (escaped.text.startsWith("/")) {
            return SqlFragment.of("full_path ILIKE ?" + esc, List.of("%" + escaped.text));
        }
        return SqlFragment.of(
                "(full_path ILIKE ?" + esc + " OR full_path ILIKE ?" + esc + ")",
                List.of(escaped.text, "%/" + escaped.text));
    }

    static SqlFragment createExactNodeConditions(Query query, String tablePrefix) {
        List<SqlFragment> conditions = new ArrayList<>();

        if (isNotBlank(query.folder())) {
            String path = stripSlashes(query.folder());
            if (path != null && !path.isEmpty()) {
                conditions.add(SqlFragment.of(tablePrefix + "node_path = ?", List.of(path)));
            }
        }

        if (isNotBlank(query.text())) {
            EscapedText escaped = escapeForIlike(query.text(), true);
            conditions.add(
                    SqlFragment.of(tablePrefix + "node_name ILIKE ?" + escaped.escapeClause, List.of(escaped.text)));
        }

        SqlFragment joined = SqlFragment.join(conditions, AND_SEPARATOR);
        return SqlFragment.formatted("and %s", List.of(joined));
    }

    /**
     * Escapes special characters for ILIKE queries.
     * Handles ILIKE wildcards (_, %), escape char (^), and user wildcards (*).
     * In exact match mode, * is treated as literal. In fuzzy mode, * is converted to %.
     * Returns escaped text and ESCAPE clause only if escaping was needed.
     */
    @VisibleForTesting
    static EscapedText escapeForIlike(String text, boolean isExactMatch) {
        if (text == null || text.isEmpty()) {
            return new EscapedText("", "");
        }
        boolean needsEscaping = text.contains("_") || text.contains("%") ||
                text.contains("*") || text.contains("^");
        if (!needsEscaping) {
            return new EscapedText(text, "");
        }
        String escaped = text
                .replace("^", "^^")
                .replace("_", "^_")
                .replace("%", "^%");
        if (isExactMatch) {
            escaped = escaped.replace("*", "^*");
        } else {
            escaped = escaped.replace("*", "%");
        }
        return new EscapedText(escaped, " ESCAPE '^'");
    }

    static SqlFragment createShasCondition(List<String> terms, String tablePrefix) {
        return SqlFragment.join(terms.stream()
                .flatMap(term -> getChecksumConditionsForTerm(term, tablePrefix))
                .toList(), " or "
        );
    }

    private static Stream<SqlFragment> getChecksumConditionsForTerm(String term, String tablePrefix) {
        List<SqlFragment> conditions = new ArrayList<>();
        if (ChecksumType.sha1.isValid(term)) {
            conditions.add(
                    SqlFragment.of(tablePrefix + "sha1_actual=?", List.of(term))
            );
        }
        if (ChecksumType.md5.isValid(term)) {
            conditions.add(
                    SqlFragment.of(tablePrefix + "md5_actual=?", List.of(term))
            );
        }
        if (ChecksumType.sha256.isValid(term)) {
            conditions.add(
                    SqlFragment.of(tablePrefix + "sha256=?", List.of(term))
            );
        }
        return conditions.stream();
    }

    private static SqlFragment createRepoSpecialCharsIlikeConditions(List<String> words, boolean isExactMatch) {
        List<SqlFragment> fragments = words.stream()
                .filter(SyntaxSearchQueryUtils::hasSpecialCharacters)
                .map(word -> createRepoKeyIlikeCondition(word, isExactMatch))
                .toList();
        return SqlFragment.join(fragments, AND_SEPARATOR);
    }

    private static SqlFragment createSpecialCharsIlikeConditions(List<String> words, String tablePrefix) {
        List<SqlFragment> fragments = words.stream()
                .filter(SyntaxSearchQueryUtils::hasSpecialCharacters)
                .map(word -> createFullPathIlikeCondition(word, tablePrefix))
                .toList();

        return SqlFragment.join(fragments, AND_SEPARATOR);
    }

    private static SqlFragment createFullPathIlikeConditionsForFolder(List<String> words, String tablePrefix) {
        List<SqlFragment> fragments = words.stream()
                .map(word -> createFullPathIlikeConditionForFolder(word, tablePrefix))
                .toList();
        return SqlFragment.join(fragments, AND_SEPARATOR);
    }

    private static boolean hasSpecialCharacters(String word) {
        return word.chars().anyMatch(ch -> SEPARATOR_CHARACTERS.contains((char) ch) || (char) ch == '.');
    }

    private static SqlFragment createRepoKeyIlikeCondition(String text, boolean isExactMatch) {
        if (isExactMatch) {
            return SqlFragment.of("repository_key = ?", List.of(text));
        } else {
            EscapedText escaped = escapeForIlike(text, false);
            return SqlFragment.of("repository_key ilike ?" + escaped.escapeClause, List.of("%" + escaped.text + "%"));
        }
    }

    private static SqlFragment createFullPathIlikeConditionForFolder(String text, String tablePrefix) {
        EscapedText escaped = escapeForIlike(text, false);
        String formatted = "%s || '/' || %s ilike ?%s".formatted(
                tablePrefix + "node_path",
                tablePrefix + "node_name",
                escaped.escapeClause
        );
        return SqlFragment.of(formatted, List.of("%" + escaped.text + "%"));
    }

    private static SqlFragment createFullPathIlikeCondition(String text, String tablePrefix) {
        EscapedText escaped = escapeForIlike(text, false);
        String formatted = "%s || '/' || %s || '/' || %s ilike ?%s".formatted(
                tablePrefix + "repo",
                tablePrefix + "node_path",
                tablePrefix + "node_name",
                escaped.escapeClause
        );
        return SqlFragment.of(formatted, List.of("%" + escaped.text + "%"));
    }

    record EscapedText(String text, String escapeClause) {
    }

    static final Set<Character> SEPARATOR_CHARACTERS = Set.of(
            // --- Basic Punctuation ---
            ',', ':', ';', '!', '?', '\'', '"', '`',
            // --- Brackets & Enclosures ---
            '(', ')', '[', ']', '{', '}', '<', '>',
            // --- Mathematical & Logical ---
            '+', '-', '*', '/', '=', '%', '&', '|', '^', '~',
            // --- Structural & Special ---
            '#', '@', '_', '$'
    );

    static final String SEPARATOR_REGEX = SEPARATOR_CHARACTERS.stream()
            .map(String::valueOf)
            .map(Pattern::quote)
            .collect(Collectors.joining("", "[", "]+"));

    static final Pattern SEPARATOR_PATTERN = Pattern.compile(SEPARATOR_REGEX);

    static String createPathsFtsQuery(List<String> words, boolean isExactMatch) {
        if (words == null || words.isEmpty()) {
            return "";
        }

        final List<String> parts = words.stream()
                .map(SyntaxSearchQueryUtils::trimSeparators)
                .filter(word -> !word.isEmpty())
                .flatMap(SEPARATOR_PATTERN::splitAsStream)
                .filter(part -> !part.isEmpty())
                .toList();
        return createFtsCondition(parts, isExactMatch);
    }

    private static String createFtsCondition(final List<String> parts, boolean isExactMatch) {
        if (parts.isEmpty()) {
            return "";
        }

        if (isExactMatch) {
            // Exact match: no prefix wildcards
            return (parts.size() == 1
                    ? parts.getFirst() + " & " + parts.getFirst()
                    : String.join(" & ", parts));
        } else {
            // Regular search: add prefix wildcards
            //FTS works bad when it has only one token - in this case we duplicate the token.
            return (parts.size() == 1
                    ? parts.getFirst() + ":* & " + parts.getFirst() + ":*"
                    : String.join(" & ", parts) + ":*");
        }
    }

    static String trimSeparators(String word) {
        if (word == null || word.isEmpty()) {
            return "";
        }

        int start = 0;
        int end = word.length() - 1;

        while (start <= end && SEPARATOR_CHARACTERS.contains(word.charAt(start))) {
            start++;
        }

        while (end >= start && SEPARATOR_CHARACTERS.contains(word.charAt(end))) {
            end--;
        }

        if (start > end) {
            return "";
        }

        return word.substring(start, end + 1);
    }

    static List<Repository> queryRepositories(Query query, Filters filters, int limit, State state,
            boolean isExactMatch, JdbcHelper jdbcHelper, Deadline deadline, RequestExecutor<Repository> executor)
            throws SQLException {
        PreparedQuery preparedQuery = new PreparedQuery();

        preparedQuery.add("select repository_key, type, package_type, modified, created from repos_view");
        preparedQuery.add("where");

        SqlFragment reposCondition = createReposCondition(query, isExactMatch);
        SqlFragment typeCondition = createTypeCondition(filters);
        SqlFragment packageTypeFilter = createPackageTypeFilter(filters.packageTypes());
        SqlFragment lastModifiedFilter = createModifiedFilter(filters.lastModified());
        SqlFragment createdFilter = createCreatedFilter(filters.created());

        preparedQuery.add(typeCondition);
        preparedQuery.add(reposCondition);
        preparedQuery.add(packageTypeFilter);
        preparedQuery.add(lastModifiedFilter);
        preparedQuery.add(createdFilter);

        if (!query.autoComplete()) {
            SqlFragment stateCondition = createStateCondition(state, "");
            preparedQuery.add(stateCondition);
            preparedQuery.add("order by modified desc, repository_key desc");
        }

        preparedQuery.add(SqlFragment.of("limit ?", List.of(limit)));

        return executor.execute(jdbcHelper, preparedQuery, deadline);
    }

    private static SqlFragment createTypeCondition(Filters filters) {
        SqlFragment repositoryTypeFilter = createRepositoryTypeFilter(filters.repositoryTypes());
        return repositoryTypeFilter.isEmpty()
                ? SqlFragment.of("(type = 'local' or type = 'remote' or type = 'federated' or type = 'virtual') ")
                : repositoryTypeFilter;
    }

    private static SqlFragment createReposCondition(Query query, boolean isExactMatch) {
        String repoTerm = query.repo();
        String textTerm = query.text();
        if (isNotBlank(repoTerm)) {
            return isExactMatch ? createExactRepoMatch(repoTerm) : createReposCondition(repoTerm, isExactMatch);
        } else if (isNotBlank(textTerm)) {
            return isExactMatch ? createExactRepoMatch(textTerm) : createReposConditionForText(textTerm, isExactMatch);
        }

        return SqlFragment.EMPTY;
    }

    private SqlFragment createModifiedFilter(DateRange lastModified) {
        return createDateRangeFilter(COLUMN_MODIFIED, lastModified);
    }

    private SqlFragment createCreatedFilter(DateRange created) {
        return createDateRangeFilter(COLUMN_CREATED, created);
    }

    private SqlFragment createDateRangeFilter(String columnName, DateRange dateRange) {
        if (dateRange == null || dateRange.isEmpty()) {
            return SqlFragment.EMPTY;
        }
        if (dateRange.hasFrom() && dateRange.hasTo()) {
            String template = "and %s >= ? and %s <= ? ".formatted(columnName, columnName);
            return SqlFragment.of(template, List.of(dateRange.from(), dateRange.to()));
        }
        if (dateRange.hasFrom()) {
            String template = "and %s >= ? ".formatted(columnName);
            return SqlFragment.of(template, List.of(dateRange.from()));
        }
        return SqlFragment.EMPTY;
    }

    static int getRequestBudgetSeconds(Query query) {
        return query.autoComplete() ? 9 : 30;
    }

    private static SqlFragment createReposConditionForText(String text, boolean isExactMatch) {
        List<String> words = split(text);

        SqlFragment ftsCondition = SqlFragment.EMPTY;
        String repoKeyFtsQuery = createPathsFtsQuery(words, isExactMatch);
        if (!repoKeyFtsQuery.isEmpty()) {
            ftsCondition = createRepoKeyFtsCondition(repoKeyFtsQuery);
        }

        SqlFragment specialCharsCondition = createRepoSpecialCharsIlikeConditions(words, isExactMatch);
        SqlFragment cacheRepoExactMatch = createExactRepoMatchesForCachePrefix(words);

        List<SqlFragment> combinedList = Stream.of(ftsCondition, specialCharsCondition)
                .filter(SqlFragment::isNotEmpty)
                .toList();
        SqlFragment combined = SqlFragment.join(combinedList, AND_SEPARATOR);

        if (cacheRepoExactMatch.isNotEmpty()) {
            if (combined.isEmpty()) {
                return SqlFragment.formatted("and (%s)", List.of(cacheRepoExactMatch));
            }
            return SqlFragment.formatted("and ((%s) or (%s))", List.of(combined, cacheRepoExactMatch));
        }

        return SqlFragment.formatted(combined.isEmpty() ? "" : "and (%s)", List.of(combined));
    }

    static SqlFragment createRepoKeyFtsCondition(String query) {
        return SqlFragment.of("repo_ts_vector @@ to_tsquery('simple', ?)", List.of(query));
    }

    static List<Node> queryPaths(Query query, Filters filters, int limit, State state, boolean isExactMatch,
            JdbcHelper jdbcHelper, Deadline deadline, RequestExecutor<Node> executor)
            throws SQLException {
        SyntaxSearchQueryBuilder builder =
                new SyntaxSearchQueryBuilder(filters, FOLDER_NODE_TYPE, query.autoComplete());
        String tablePrefix = hasRepositoryFilters(filters) ? "n." : "";

        addCommonConditions(builder, query, tablePrefix, filters);
        addPathSpecificConditions(builder, query, tablePrefix, isExactMatch);

        builder.addRepositoryFilters();
        if (!query.autoComplete()) {
            builder.addCondition(createStateCondition(state, tablePrefix));
        }

        PreparedQuery preparedQuery = builder.build(limit);
        return executor.execute(jdbcHelper, preparedQuery, deadline);
    }

    static List<Node> queryFiles(Query query, Filters filters, int limit, State state, boolean isExactMatch,
            JdbcHelper jdbcHelper, Deadline deadline, RequestExecutor<Node> executor) throws SQLException {
        SyntaxSearchQueryBuilder builder = new SyntaxSearchQueryBuilder(filters, FILE_NODE_TYPE, query.autoComplete());
        String tablePrefix = hasRepositoryFilters(filters) ? "n." : "";

        addCommonConditions(builder, query, tablePrefix, filters);
        addFileSpecificConditions(builder, query, tablePrefix, isExactMatch);

        if (builder.noConditions()) {
            return List.of();
        }

        builder.addRepositoryFilters();
        if (!query.autoComplete()) {
            builder.addCondition(createStateCondition(state, tablePrefix));
        }

        PreparedQuery preparedQuery = builder.build(limit);
        return executor.execute(jdbcHelper, preparedQuery, deadline);
    }

    /**
     * Adds conditions common to both paths and files queries.
     */
    private static void addCommonConditions(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix,
            Filters filters) {
        addRepoCondition(builder, query, tablePrefix);
        addPermittedRepos(builder, query, tablePrefix);
        addDateRangeCondition(builder, COLUMN_MODIFIED, filters.lastModified(), tablePrefix);
        addDateRangeCondition(builder, COLUMN_CREATED, filters.created(), tablePrefix);
    }

    private static void addRepoCondition(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix) {
        if (isNotBlank(query.repo())) {
            if (query.isRepoFixed()) {
                builder.addCondition(createExactReposConditionForNodesTable(query.repo(), tablePrefix));
            } else {
                builder.addCondition(createReposConditionForNodesTable(query.repo(), tablePrefix));
            }
        }
    }

    private static void addPermittedRepos(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix) {
        if (!CollectionUtils.isNullOrEmpty(query.permittedRepos())) {
            builder.addCondition(
                    createPermittedReposConditionForNodesTable(query.permittedRepos(), tablePrefix));
        }
    }

    private static void addDateRangeCondition(SyntaxSearchQueryBuilder builder, String columnName,
            DateRange dateRange, String tablePrefix) {
        if (dateRange == null || dateRange.isEmpty()) {
            return;
        }
        if (dateRange.hasFrom() && dateRange.hasTo()) {
            builder.addCondition(
                    SqlFragment.of("and " + tablePrefix + columnName + " >= ? " +
                                    AND_SEPARATOR + tablePrefix + columnName + " <= ?",
                            List.of(dateRange.from(), dateRange.to())
                    )
            );
        } else if (dateRange.hasFrom()) {
            builder.addCondition(
                    SqlFragment.of("and " + tablePrefix + columnName + " >= ?", List.of(dateRange.from()))
            );
        }
    }

    private static void addPathSpecificConditions(SyntaxSearchQueryBuilder builder, Query query,
            String tablePrefix,
            boolean isExactMatch) {
        handlePathExactMatchTextOnly(builder, query, tablePrefix, isExactMatch);
        handlePathFolderConditions(builder, query, tablePrefix, isExactMatch);
        handlePathTextConditions(builder, query, tablePrefix, isExactMatch);
    }

    private static void handlePathExactMatchTextOnly(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix,
            boolean isExactMatch) {
        if (isExactMatch && isNotBlank(query.text()) && !isNotBlank(query.folder())) {
            builder.addCondition(createExactMatchTextCondition(query.text(), tablePrefix));
        }
    }

    private static void handlePathFolderConditions(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix,
            boolean isExactMatch) {
        if (!isNotBlank(query.folder())) {
            return;
        }

        if (isNull(query.text()) || (isExactMatch && !isNotBlank(query.text()))) {
            if (isExactMatch) {
                builder.addCondition(createExactMatchTextCondition(query.folder(), tablePrefix));
            } else {
                builder.addCondition(createPathsCondition(query.folder(), tablePrefix));
            }
        } else {
            builder.addCondition(createChildrenPathsCondition(query.folder(), tablePrefix));
        }
    }

    private static void handlePathTextConditions(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix,
            boolean isExactMatch) {
        if (!isNotBlank(query.text())) {
            return;
        }

        if (isNotBlank(query.folder())) {
            if (isExactMatch) {
                builder.addCondition(createExactMatchTextCondition(query.text(), tablePrefix));
            } else {
                builder.addCondition(createPathsConditionForText(query.text(), tablePrefix));
            }
        } else if (!isExactMatch) {
            builder.addCondition(createPathsConditionForText(query.text(), tablePrefix));
        }
    }

    private static void addFileSpecificConditions(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix,
            boolean isExactMatch) {
        processSha(builder, query, tablePrefix);

        if (tryHandleExactMatchEarlyReturns(builder, query, tablePrefix, isExactMatch)) {
            return;
        }

        addFolderConditions(builder, query, tablePrefix, isExactMatch);
        addTextConditions(builder, query, tablePrefix, isExactMatch);
    }

    private static void processSha(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix) {
        if (!isNotBlank(query.sha())) {
            return;
        }

        SqlFragment shaCondition = createShaCondition(query.sha(), tablePrefix);
        if (shaCondition.isEmpty()) {
            builder.setNoNeedToQuery();
        } else {
            builder.addCondition(shaCondition);
        }
    }

    private static boolean tryHandleExactMatchEarlyReturns(SyntaxSearchQueryBuilder builder, Query query,
            String tablePrefix, boolean isExactMatch) {
        if (!isExactMatch) {
            return false;
        }

        if (query.hasTextRepoAndFolder()) {
            builder.addCondition(createExactNodeConditions(query, tablePrefix));
            return true;
        }

        // Exact match: text only (e.g., "my-app") -> full_path ILIKE '%my-app'
        if (isNotBlank(query.text()) && isNull(query.folder())) {
            builder.addCondition(createExactMatchTextCondition(query.text(), tablePrefix));
            return true;
        }

        // Exact match: folder only (e.g., repo:docker path:org/jfrog) -> skip files, query folders instead
        if (isNotBlank(query.folder()) && !isNotBlank(query.text())) {
            builder.setNoNeedToQuery();
            return true;
        }

        return false;
    }

    private static void addFolderConditions(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix,
            boolean isExactMatch) {
        if (!isNotBlank(query.folder())) {
            return;
        }

        if (isNull(query.text())) {
            if (isExactMatch) {
                builder.addCondition(createExactMatchTextCondition(query.folder(), tablePrefix));
            } else {
                builder.addCondition(createPathsConditionForFile(query.folder(), tablePrefix));
            }
        } else {
            builder.addCondition(createChildrenPathsCondition(query.folder(), tablePrefix));
        }
    }

    private static void addTextConditions(SyntaxSearchQueryBuilder builder, Query query, String tablePrefix,
            boolean isExactMatch) {
        if (!isNotBlank(query.text())) {
            return;
        }

        if (isNotBlank(query.folder())) {
            if (isExactMatch) {
                builder.addCondition(createExactMatchTextCondition(query.text(), tablePrefix));
            } else {
                builder.addCondition(createFilesConditionForText(query.text(), tablePrefix));
            }
        } else if (!isExactMatch) {
            builder.addCondition(createFilesConditionForText(query.text(), tablePrefix));
        }
    }

    @FunctionalInterface
    interface ResultSetRowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private static <T> List<T> executeQuery(JdbcHelper jdbcHelper, PreparedQuery preparedQuery,
            Deadline deadline, ResultSetRowMapper<T> rowMapper, String entityType) throws SQLException {
        List<T> results = new ArrayList<>();
        String template = preparedQuery.getTemplate();
        Object[] params = preparedQuery.getParams().toArray();
        log.info("SyntaxSearch SQL query : \r\n{}", JdbcHelper.resolveQuery(template, params));
        QueryControls controls = QueryControls.builder().queryTimeout(deadline.remainingQueryTimeoutSeconds()).build();
        try (ResultSet resultSet = jdbcHelper.executeSelect(controls, template, params)) {
            while (resultSet.next()) {
                results.add(rowMapper.map(resultSet));
            }
        } catch (SQLException e) {
            handleSqlException(e, entityType);
        }
        return results;
    }

    static List<Repository> getRepositories(JdbcHelper jdbcHelper, PreparedQuery preparedQuery, Deadline deadline)
            throws SQLException {
        return executeQuery(jdbcHelper, preparedQuery, deadline, rs -> {
            String name = rs.getString("repository_key");
            String type = rs.getString("type");
            String packageType = rs.getString("package_type");
            long modified = rs.getLong(COLUMN_MODIFIED);
            long created = rs.getLong(COLUMN_CREATED);
            return new Repository(name, type, packageType, modified, created);
        }, "Repositories");
    }

    static List<Node> getNodes(JdbcHelper jdbcHelper, PreparedQuery preparedQuery, Deadline deadline)
            throws SQLException {
        return executeQuery(jdbcHelper, preparedQuery, deadline, rs -> {
            long nodeId = rs.getLong("node_id");
            String repo = rs.getString("repo");
            String path = rs.getString("node_path");
            String name = rs.getString("node_name");
            long binLength = rs.getLong("bin_length");
            long modified = rs.getLong(COLUMN_MODIFIED);
            long created = rs.getLong(COLUMN_CREATED);
            return new Node(nodeId, repo, path, name, binLength, modified, created);
        }, "Nodes");
    }

    private static void handleSqlException(SQLException e, String entityType) throws SQLException {
        if (TIMEOUT_STATE.equals(e.getSQLState())) {
            log.warn("{} syntax search query timed out", entityType);
            SyntaxSearchTimeoutTracker.markTimeout();
        } else {
            throw e;
        }
    }

    /**
     * Checks if filters contain any repository-related filters (repositoryTypes or packageTypes).
     * Returns true if we need to JOIN with repository_config table.
     */
    static boolean hasRepositoryFilters(Filters filters) {
        boolean hasRepoTypes = filters.repositoryTypes() != null && !filters.repositoryTypes().isEmpty();
        boolean hasPkgTypes = filters.packageTypes() != null && !filters.packageTypes().isEmpty();
        return hasRepoTypes || hasPkgTypes;
    }

    /**
     * Creates SQL condition for filtering by repository types (local, remote, federated).
     * Returns empty string if no filter provided.
     *
     * Example output: "and type IN ('local', 'remote') "
     */
    static SqlFragment createRepositoryTypeFilter(Set<RepositoryType> repositoryTypes) {
        if (repositoryTypes == null || repositoryTypes.isEmpty()) {
            return SqlFragment.EMPTY;
        }
        List<Object> types = repositoryTypes.stream()
                .<Object>map(rt -> {
                    if (rt == RepositoryType.CACHED) {
                        return RepositoryType.REMOTE.getTypeNameLoweCase();
                    }
                    return rt.getTypeNameLoweCase();
                })
                .distinct()
                .toList();
        SqlFragment fragment = SqlFragment.list(types);
        return SqlFragment.formatted("type IN (%s)", List.of(fragment));
    }

    /**
     * Creates SQL condition for filtering by package types (maven, npm, docker, etc.).
     * Returns empty string if no filter provided.
     *
     * Example output: "and package_type IN ('maven', 'npm') "
     */
    static SqlFragment createPackageTypeFilter(Set<PackageType> packageTypes) {
        if (packageTypes == null || packageTypes.isEmpty()) {
            return SqlFragment.EMPTY;
        }
        List<Object> types = packageTypes.stream()
                .<Object>map(pt -> pt.name().toLowerCase())
                .toList();
        SqlFragment fragment = SqlFragment.list(types);
        return SqlFragment.formatted("and package_type IN (%s)", List.of(fragment));
    }
}

