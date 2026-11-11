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

package org.artifactory.rest.util;

import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.annotation.Nonnull;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.commons.io.IOUtils;
import org.artifactory.api.common.BasicStatusHolder;
import org.artifactory.api.jackson.JacksonFactory;
import org.artifactory.aql.result.AqlRestResult;
import org.artifactory.common.status.StatusEntry;

/**
 * Utility class that helps creating responses
 *
 * @author Shay Bagants
 */
public class ResponseUtils {

    public static StreamingOutput getStreamingOutput(BasicStatusHolder result) {
        return outputStream -> {
            JsonGenerator jsonGenerator = JacksonFactory.createGenerator(outputStream);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeArrayFieldStart("messages");
            if (result.hasErrors()) {
                for (StatusEntry error : result.getErrors()) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField("level", error.getLevel().name());
                    jsonGenerator.writeStringField("message", error.getMessage());
                    jsonGenerator.writeEndObject();
                }
            }
            if (result.hasWarnings()) {
                for (StatusEntry warning : result.getWarnings()) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField("level", warning.getLevel().name());
                    jsonGenerator.writeStringField("message", warning.getMessage());
                    jsonGenerator.writeEndObject();
                }
            }
            //No errors \ warnings - return last info
            if (!result.hasErrors() && !result.hasWarnings()) {
                jsonGenerator.writeStartObject();
                jsonGenerator.writeStringField("level", result.getLastStatusEntry().getLevel().name());
                jsonGenerator.writeStringField("message", result.getLastStatusEntry().getMessage());
                jsonGenerator.writeEndObject();
            }
            jsonGenerator.writeEndArray();
            jsonGenerator.writeEndObject();
            jsonGenerator.close();
        };
    }

    public static StreamingOutput getErrorResponse(BasicStatusHolder result) {
        return outputStream -> {
            JsonGenerator jsonGenerator = JacksonFactory.createGenerator(outputStream);
            jsonGenerator.writeStartObject();
            if (result.hasErrors()) {
                jsonGenerator.writeArrayFieldStart("errors");
                for (StatusEntry error : result.getErrors()) {
                    jsonGenerator.writeStartObject();
                    jsonGenerator.writeStringField("message", error.getMessage());
                    jsonGenerator.writeEndObject();
                }
                jsonGenerator.writeEndArray();
                jsonGenerator.writeEndObject();
                jsonGenerator.close();
            }
        };
    }

    public static StreamingOutput getResponse(BasicStatusHolder result) {
        return outputStream -> {
            JsonGenerator jsonGenerator = JacksonFactory.createGenerator(outputStream);
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("message", result.getStatusMsg());
            jsonGenerator.writeEndObject();
            jsonGenerator.close();
        };
    }

    @Nonnull
    public static StreamingOutput aqlRestResultToStreamingOutput(AqlRestResult restResult) {
        return os -> {
            byte[] array = restResult.read();
            try {
                while (array != null) {
                    os.write(array);
                    array = restResult.read();
                }
                os.flush();
            } finally {
                IOUtils.closeQuietly(restResult);
            }
        };
    }
}
