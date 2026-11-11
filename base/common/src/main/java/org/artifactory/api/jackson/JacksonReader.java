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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;

/**
 * JSON parsing helper.<br>
 * All typical JSON parsing operations should be performed through this class and not directly on a parser.
 *
 * @author Noam Y. Tenne
 */
public final class JacksonReader {

    private JacksonReader() {
    }

    /**
     * Parses bytes to an object
     *
     * @param bytes   Bytes to parse
     * @param asClass Class of resulting object
     * @param <T>     Object class
     * @return Parsed object
     */
    public static <T> T bytesAsClass(byte[] bytes, Class<T> asClass) throws IOException {
        ObjectMapper mapper = JacksonFactory.createObjectMapper();
        return mapper.readValue(bytes, asClass);
    }

    /**
     * Parses bytes to an object
     *
     * @param bytes        Bytes to parse
     * @param valueTypeRef Type reference of the resulting object. Supports generics
     * @param <T>          Object class
     * @return Parsed object
     */
    public static <T> T bytesAsClass(byte[] bytes, TypeReference<T> valueTypeRef) throws IOException {
        ObjectMapper mapper = JacksonFactory.createObjectMapper();
        return mapper.readValue(bytes, valueTypeRef);
    }

    /**
     * Parse bytes to a JSON tree.<BR>
     * ATTENTION: closing the given stream is the responsibility of the caller.
     *
     * @param bytes Bytes to parse
     * @return JSON node
     */
    public static JsonNode bytesAsTree(byte[] bytes) throws IOException {
        ObjectMapper mapper = JacksonFactory.createObjectMapper();
        return mapper.readTree(bytes);
    }

    /**
     * Parses bytes to an object
     *
     * @param bytes              Bytes to parse
     * @param valueTypeReference Type reference of resulting object
     * @param <T>                Object class
     * @return Parsed object
     */
    public static <T> T bytesAsValueTypeReference(byte[] bytes, TypeReference<T> valueTypeReference)
            throws IOException {
        ObjectMapper mapper = JacksonFactory.createObjectMapper();
        return mapper.readValue(bytes, valueTypeReference);
    }

    /**
     * Parses the content of an input stream to an object.<BR>
     * ATTENTION: closing the given stream is the responsibility of the caller.
     *
     * @param inputStream Stream to read and parse
     * @param valueType   Class of resulting object
     * @param <T>         Object class
     * @return Parsed object
     */
    public static <T> T streamAsClass(InputStream inputStream, Class<T> valueType) throws IOException {
        ObjectMapper mapper = JacksonFactory.createObjectMapper();
        mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        return mapper.readValue(inputStream, valueType);
    }

    /**
     * Parses the content of an input stream to an object.<BR>
     * ATTENTION: closing the given stream is the responsibility of the caller.
     *
     * @param inputStream        Stream to read and parse
     * @param valueTypeReference Type reference of resulting object
     * @param <T>                Object class
     * @return Parsed object
     */
    public static <T> T streamAsValueTypeReference(InputStream inputStream, TypeReference<T> valueTypeReference)
            throws IOException {
        ObjectMapper mapper = JacksonFactory.createObjectMapper();
        mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        return mapper.readValue(inputStream, valueTypeReference);
    }

    /**
     * Parse the content of an input stream to a JSON tree.<BR>
     * ATTENTION: closing the given stream is the responsibility of the caller.
     *
     * @param inputStream Stream to read and parse
     * @return JSON node
     */
    public static JsonNode streamAsTree(InputStream inputStream) throws IOException {
        ObjectMapper mapper = JacksonFactory.createObjectMapper();
        mapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        return mapper.readTree(inputStream);
    }
}
