package org.artifactory.ui.rest.service.state;

import java.util.List;

/**
 * @author Alexei Vainshtein
 */
public interface GlobalStateModel {

    void setMessages(List<FooterMessage> messages);

    void setState(State state);
}
