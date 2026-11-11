package org.artifactory.debug.threads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class ThreadDumpResponse {
    private List<SingleThreadDump> threadDumps = new ArrayList<>();

    public void addSingleThreadDump(SingleThreadDump singleThreadDump) {
        threadDumps.add(singleThreadDump);
    }

    public void addAllThreadDumps(List<SingleThreadDump> singleThreadDumps) {
        threadDumps.addAll(singleThreadDumps);
    }

    public String convertToJsonWithLineBreaks() throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsString(this)
                .replace("\\n", "\n");
    }
}
