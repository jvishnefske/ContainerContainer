package org.artifactory.ui.rest.model.artifacts.search.syntax;

/**
 * @author Igor Usenko
 */
public record State(
        Type type,
        String id,
        long modified
) {
    public static State copy(State state) {
        if (state == null) {
            return null;
        } else {
            return new State(state.type(), state.id(), state.modified());
        }
    }

    public static final State STOP_STATE = new State(Type.STOP, "", 0L);
}
