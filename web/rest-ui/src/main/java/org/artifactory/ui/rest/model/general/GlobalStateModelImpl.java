package org.artifactory.ui.rest.model.general;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.artifactory.ui.rest.service.state.FooterMessage;
import org.artifactory.ui.rest.service.state.GlobalStateModel;
import org.artifactory.ui.rest.service.state.State;

import java.util.List;

/**
 * @author Alexei Vainshtein
 */
@Getter
@NoArgsConstructor
public class GlobalStateModelImpl implements GlobalStateModel {

    private List<FooterMessage> messages;
    private State state;

    public void setMessages(List<FooterMessage> messages) {
        this.messages = messages;
    }

    @Override
    public void setState(State state) {
        this.state = state;
    }
}
