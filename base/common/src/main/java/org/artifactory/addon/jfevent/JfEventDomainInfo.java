package org.artifactory.addon.jfevent;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author Yevdo Abramov
 * Created on 24/05/2020
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JfEventDomainInfo {
    private String id;
    private String name;
    private String service;
    private List<JfEventTypeInfo> eventTypes;
}
