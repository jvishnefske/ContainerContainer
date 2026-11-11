package org.artifactory.storage.db.version.converter;

import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.artifactory.common.home.ArtifactoryHome;
import org.jfrog.converter.DbConverterExecutionException;
import org.jfrog.converter.DbConverterRuntimeException;
import org.jfrog.converter.predefined.CommonScriptFileDbConverter;
import org.jfrog.storage.DbType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiPredicate;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.jfrog.converter.util.PreDefinedConditions.executeUnconditional;
import static org.jfrog.storage.util.DbUtils.getDbTypeNameForSqlResources;

/**
 *
 * Converter should be used to execute script from passed file conditionally. It can be either classpath resource or
 * external resource from {@link ArtifactoryHome#getExternalConversionsDir()} in case
 * {@link ConstantValues#allowExternalConversionScripts} is true.
 *
 * <pre>{@code
 *     new ScriptFileDbConverter("v26_node_props_index", dbSpecific(DbType.POSTGRESQL))
 * }</pre>
 *
 * @author Michael Akushsky
 * @author Volodymyr Shulga
 * @author shaharz
 */
@Slf4j
public class ScriptFileDbConverter extends CommonScriptFileDbConverter {

    @SafeVarargs
    public ScriptFileDbConverter(String file, BiPredicate<Connection, DbType>... conditions) {
        this(file, file, conditions);
    }

    @SafeVarargs
    public ScriptFileDbConverter(String file, String description, BiPredicate<Connection, DbType>... conditions) {
        super(file, description, conditions);
    }

    public ScriptFileDbConverter(String file) {
        super(file, file, executeUnconditional());
    }

    @Override
    protected String calculateSqlStatement(DbType dbType) throws DbConverterExecutionException {
        String dbTypeName = getDbTypeNameForSqlResources(dbType);
        try {
            if (!ConstantValues.allowExternalConversionScripts.getBoolean()) {
                return super.calculateSqlStatement(dbType);
            }
            String externalConversionScriptPath = ArtifactoryHome.get().getExternalConversionsDir().toPath() + "/" +
                    dbTypeName + "_" + file + ".sql";
            boolean externalConversionScriptExists = new File(externalConversionScriptPath).exists();
            if (!externalConversionScriptExists) {
                log.warn("Could not find external sql script file to execute under '{}'", externalConversionScriptPath);
                return super.calculateSqlStatement(dbType);
            }
            // Check whether the user provided his own conversion script for this conversion
            return readUserProvidedConversionScript(externalConversionScriptPath);
        } catch (IOException e) {
            throw new DbConverterExecutionException("Could not convert DB using " + file + " converter", e);
        }
    }

    @VisibleForTesting
    @Override
    protected String getConversionScriptPath(String file, String dbTypeName) {
        return super.getConversionScriptPath(file, dbTypeName);
    }

    @SuppressWarnings("squid:S1163")
    private String readUserProvidedConversionScript(String resourcePath) throws DbConverterExecutionException,
            IOException {
        File externalConversionScript = new File(resourcePath);
        if (!externalConversionScript.exists()) {
            return null;
        }
        // Note: we support empty external conversion scripts to allow "skipping" of real conversion files
        StringBuilder conversionFileContent = new StringBuilder();
        try (FileReader reader = new FileReader(externalConversionScript)) {
            BufferedReader bufferedReader = new BufferedReader(reader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (isNotBlank(line)) {
                    DropStatementValidator.validate(line);
                    conversionFileContent.append(line);
                    conversionFileContent.append(System.lineSeparator());
                }
            }
            return StringUtils.chomp(conversionFileContent.toString());
        } catch (IOException e) {
            // The startup bound to this conversion will fail. If this is the first conversion in line, the user can
            // rename the script file back to its original name, restart, and let the conversion re-run.
            throw new DbConverterExecutionException("Unable to read conversion script file", e);
        } finally {
            File externalConversionScriptBackup = new File(resourcePath + "." + System.currentTimeMillis() + ".bak");
            // Rename this file immediately to avoid an erroneous re-conversion attempt
            log.info("Renaming the external conversion script file to: {}", externalConversionScriptBackup.toPath());
            Files.move(externalConversionScript.toPath(), externalConversionScriptBackup.toPath());
        }
    }

    private static class DropStatementValidator {
        private static final List<String> ILLEGAL_QUERIES = Arrays.asList("drop table", "drop database");

        public static void validate(String query) {
            for (String illegalQuery : ILLEGAL_QUERIES) {
                if (query.toLowerCase().contains(illegalQuery)) {
                    log.error("Offending DROP query with the following SQL syntax found while reading an externalized" +
                            " conversion script: {}", query);
                    throw new DbConverterRuntimeException("DROP DATABASE and DROP TABLE statements are " +
                            "forbidden inside external conversion files. Correct the conversion script or contact " +
                            "Support for help");
                }
            }
        }
    }
}
