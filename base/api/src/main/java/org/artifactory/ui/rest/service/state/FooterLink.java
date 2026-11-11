package org.artifactory.ui.rest.service.state;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author Nadav Yogev
 */
@Data
@AllArgsConstructor
public class FooterLink {

    public static final String URL_PLACEHOLDER = "___URL___";

    private String url;
    private String text;

    public String toLink() {
        return "<a href='" + url + "'>" + text + "</a>";
    }
}
