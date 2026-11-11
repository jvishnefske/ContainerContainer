package org.artifactory.addon.support;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.deser.std.DateDeserializers;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;

import jakarta.validation.constraints.Min;
import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor(onConstructor_={@JsonIgnore})
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArtifactorySupportBundleParameters {

    // Note: do not use Lombok Builder and Builder.Default annotations
    // as they mess up default values during JSON deserialization
    private boolean configuration = true;
    private boolean system = true;
    private boolean collectSlowQueriesFromDatabase = false;
    private static final String DATE_CONF_FORMAT = "yyyy-MM-dd";

    @JsonProperty("scrub_ips")
    private boolean scrubIPs = false;

    @JsonProperty("scrub_user_names")
    private boolean scrubUserNames = false;

    private Logs logs = new Logs();
    @JsonProperty("thread_dump")
    private ThreadDump threadDump = new ThreadDump();

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonSerialize(using = Logs.LogsItemSerializer.class)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Logs {

        private boolean include = true;
        private boolean scrubIPs = false;
        private boolean scrubUserNames = false;

        @JsonProperty("start_date")
        @JsonDeserialize(using = SupportBundleDateTimeDeserializer.class)
        private Date startDate = DateUtils.addDays(DateUtils.truncate(new Date(), Calendar.DATE), -1);

        @JsonProperty("end_date")
        @JsonDeserialize(using = SupportBundleDateTimeDeserializer.class)
        private Date endDate = DateUtils.truncate(new Date(), Calendar.DATE);

        @NoArgsConstructor
        public static class LogsItemSerializer extends JsonSerializer<Logs> {
            @Override
            public void serialize(Logs value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeStartObject();
                gen.writeBooleanField("include", value.isInclude());
                gen.writeStringField("start_date", formatDate(value.getStartDate()));
                gen.writeStringField("end_date", formatDate(value.getEndDate()));
                gen.writeEndObject();
            }

            String formatDate(Date date) {
                LocalDate localDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                return localDate.toString();
            }
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ThreadDump {
        @Min(0)
        private int count = 0;
        @Min(0)
        private long interval = 0;
    }

    @Slf4j
    public static class SupportBundleDateTimeDeserializer extends DateDeserializers.DateDeserializer {
        @Override
        public Date deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            Date date = null;
            try {
                date = DateUtils.parseDate(jp.getText(), DATE_CONF_FORMAT);
            } catch (ParseException pe) {
                log.debug("", pe);
                try {
                    long dateAsLong = Long.parseLong(jp.getText());
                    date = new Date(dateAsLong);
                } catch (Exception e) {
                    log.warn("Failed parsing {} , because of the following: {}", jp.getText(), e.getMessage());
                    log.debug("", e);
                }
            }
            return date;
        }
    }
}
