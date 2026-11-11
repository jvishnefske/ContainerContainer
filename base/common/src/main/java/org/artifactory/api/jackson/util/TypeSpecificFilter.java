package org.artifactory.api.jackson.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import org.apache.commons.lang3.NotImplementedException;
import org.artifactory.descriptor.repo.RepoType;
import org.artifactory.repo.IncludeTypeSpecific;
import org.artifactory.repo.RepoDetailsType;
import org.artifactory.repo.RepositoryConfigurationBase;

import java.util.Arrays;

/**
 * Filter {@link RepositoryConfigurationBase} fields according to {@link IncludeTypeSpecific} annotation.
 *
 * @author Inbar Tal
 */
public class TypeSpecificFilter implements PropertyFilter {
    public static final String TYPE_SPECIFIC_FILTER = "typeSpecificFilter";

    @Override
    public void serializeAsField(Object pojo, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
            PropertyWriter propertyWriter) throws Exception {
        IncludeTypeSpecific typeSpecificAnnotation = propertyWriter.getAnnotation(IncludeTypeSpecific.class);

        if (typeSpecificAnnotation == null) {
            return;
        }

        if (!(pojo instanceof RepositoryConfigurationBase)) {
            throw new ClassCastException("Cannot serialize object not from type " + RepositoryConfigurationBase.class);
        }

        RepositoryConfigurationBase repoConfig = (RepositoryConfigurationBase) pojo;
        RepoType pkgType = RepoType.fromType(repoConfig.getPackageType().toLowerCase());
        RepoDetailsType repoType = RepoDetailsType.valueOf(repoConfig.getType().toUpperCase());

        RepoType[] pkgTypes = typeSpecificAnnotation.packageType();
        boolean matchPkgType = (pkgTypes.length == 0) || Arrays.asList(pkgTypes).contains(pkgType);

        RepoDetailsType[] repoTypes = typeSpecificAnnotation.repoType();
        boolean matchRepoType = (repoTypes.length == 0) || Arrays.asList(repoTypes).contains(repoType);

        if (matchPkgType && matchRepoType) {
            propertyWriter.serializeAsField(pojo, jsonGenerator, serializerProvider);
        }
    }

    @Override
    public void serializeAsElement(Object o, JsonGenerator jsonGenerator, SerializerProvider serializerProvider,
            PropertyWriter propertyWriter) throws Exception {
        throw new NotImplementedException("Method is not supported by filter");
    }

    @Override
    public void depositSchemaProperty(PropertyWriter propertyWriter, ObjectNode objectNode,
            SerializerProvider serializerProvider) throws JsonMappingException {
        throw new NotImplementedException("Method is not supported by filter");
    }

    @Override
    public void depositSchemaProperty(PropertyWriter propertyWriter, JsonObjectFormatVisitor jsonObjectFormatVisitor,
            SerializerProvider serializerProvider) throws JsonMappingException {
        throw new NotImplementedException("Method is not supported by filter");
    }
}