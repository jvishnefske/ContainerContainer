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

package org.artifactory.api.jackson;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;

/**
 * Jackson generator factory class
 *
 * @author Noam Tenne
 */
public abstract class JacksonFactory {
    private JacksonFactory() {
        // utility class
    }

    /**
     * Creates a JsonGenerator using the given output stream as a writer. This method
     * allow choosing if the json will be pretty formatted or not
     *
     * @param outputStream     Stream to write to
     * @param usePrettyPrinter Use pretty printer format
     * @return Json Generator
     * @throws IOException
     */
    public static JsonGenerator createGenerator(OutputStream outputStream, boolean usePrettyPrinter)
            throws IOException {
        JsonFactory jsonFactory = getFactory();
        JsonGenerator jsonGenerator = jsonFactory.createGenerator(outputStream, JsonEncoding.UTF8);
        updateGenerator(jsonFactory, jsonGenerator, usePrettyPrinter);
        return jsonGenerator;
    }

    /**
     * Creates a JsonGenerator using the given output stream as a writer
     *
     * @param outputStream Stream to write to
     * @return Json Generator
     * @throws IOException
     */
    public static JsonGenerator createGenerator(OutputStream outputStream) throws IOException {
        return createGenerator(outputStream, true);
    }

    /**
     * Creates a JsonGenerator using the given writer
     *
     * @param writer Stream to write to
     * @return Json Generator
     * @throws IOException
     */
    public static JsonGenerator createGenerator(Writer writer) throws IOException {
        JsonFactory jsonFactory = getFactory();
        JsonGenerator jsonGenerator = jsonFactory.createGenerator(writer);
        updateGenerator(jsonFactory, jsonGenerator, true);
        return jsonGenerator;
    }

    /**
     * Creates a JsonParser using the given input stream as a reader.<BR>
     * Please do not use this method directly for simple parsing tasks, instead use {@link JacksonReader}.
     *
     * @param inputStream Stream to read from
     * @return Json Parser
     * @throws IOException
     */
    public static JsonParser createJsonParser(InputStream inputStream) throws IOException {
        JsonFactory jsonFactory = getFactory();
        JsonParser jsonParser = jsonFactory.createParser(inputStream);
        updateParser(jsonFactory, jsonParser);
        return jsonParser;
    }

    /**
     * Creates a JsonParser using the given byte[] as a reader.<BR>
     * Please do not use this method directly for simple parsing tasks, instead use {@link JacksonReader}.
     *
     * @param input Input to read from
     * @return Json Parser
     * @throws IOException
     */
    public static JsonParser createJsonParser(byte[] input) throws IOException {
        JsonFactory jsonFactory = getFactory();
        JsonParser jsonParser = jsonFactory.createParser(input);
        updateParser(jsonFactory, jsonParser);
        return jsonParser;
    }

    /**
     * Create the JSON factory
     *
     * @return JSON factory
     */
    private static JsonFactory getFactory() {
        JsonFactory jsonFactory = new JsonFactory();
        //Do not auto-close target output when writing completes
        jsonFactory.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);

        //Do not auto-close source output when reading completes
        jsonFactory.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
        return jsonFactory;
    }

    /**
     * Update the generator with a default codec and pretty printer
     *
     * @param jsonFactory   Factory to set as codec
     * @param jsonGenerator Generator to configure
     */
    private static void updateGenerator(JsonFactory jsonFactory, JsonGenerator jsonGenerator,
            boolean usePrettyPrinter) {
        JakartaXmlBindAnnotationModule module =
                new JakartaXmlBindAnnotationModule().setPriority(JakartaXmlBindAnnotationModule.Priority.SECONDARY);
        ObjectMapper mapper = new ObjectMapper(jsonFactory);
        mapper.registerModule(module);

        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        jsonGenerator.setCodec(mapper);
        if (usePrettyPrinter) {
            jsonGenerator.useDefaultPrettyPrinter();
        }
    }

    /**
     * Update the parser with a default codec
     *
     * @param jsonFactory Factory to set as codec
     * @param jsonParser  Parser to configure
     */
    private static void updateParser(JsonFactory jsonFactory, JsonParser jsonParser) {
        JakartaXmlBindAnnotationModule module =
                new JakartaXmlBindAnnotationModule().setPriority(JakartaXmlBindAnnotationModule.Priority.SECONDARY);
        ObjectMapper mapper = new ObjectMapper(jsonFactory);
        mapper.registerModule(module);

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        jsonParser.setCodec(mapper);
    }

    /**
     * Creates a Jackson object mapper
     *
     * @param indent indent output for pretty printing
     * @return {@link com.fasterxml.jackson.databind.ObjectMapper}
     */
    public static ObjectMapper createObjectMapper(boolean indent) {
        JakartaXmlBindAnnotationModule module =
                new JakartaXmlBindAnnotationModule().setPriority(JakartaXmlBindAnnotationModule.Priority.SECONDARY);
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        // According to well-known Jackson issue: https://github.com/FasterXML/jackson-modules-base/issues/98
        mapper.enable(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        if (indent) {mapper.enable(SerializationFeature.INDENT_OUTPUT);}
        return mapper;
    }

    /**
     * Creates a Jackson object mapper
     *
     * @return {@link com.fasterxml.jackson.databind.ObjectMapper}
     */
    public static ObjectMapper createObjectMapper() {
        return createObjectMapper(false);
    }
}
