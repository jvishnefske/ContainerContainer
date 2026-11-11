package org.artifactory.queue.behaviors;

import org.artifactory.common.ConstantValues;
import org.artifactory.event.EventType;
import org.artifactory.event.queue.QueueReductionBehaviours;
import org.artifactory.queue.QueueEventImpl;

import java.util.EnumMap;
import java.util.List;

public class QueueReductionBehavioursImpl {

    private static final EnumMap<QueueReductionBehaviours, QueueReductionBehavioursImpl> ReductionBehaviours =
            new EnumMap<>(QueueReductionBehaviours.class);

    static {
        ReductionBehaviours.put(QueueReductionBehaviours.None, new QueueReductionBehavioursImpl());
        ReductionBehaviours.put(QueueReductionBehaviours.Basic, new QueueReductionBehavioursImpl(
                new CreateEliminateDeleteBehavior(),
                new DeleteEliminateUpdateOrCreateBehavior(),
                //must be after CreateEliminateDelete (selfElimination)
                new PrioritizedEventDedupBehavior(EventType.create, EventType.update)));
        ReductionBehaviours.put(QueueReductionBehaviours.FederatedReplication, getFederatedReplicationBehavior());
        ReductionBehaviours.put(QueueReductionBehaviours.IncrementalIndex, new QueueReductionBehavioursImpl(
                new DeleteEliminateUpdateOrCreateBehavior(),
                new PrioritizedEventDedupBehavior(EventType.create, EventType.update, EventType.props)
        ));
    }

    public static QueueReductionBehavioursImpl get(QueueReductionBehaviours queueReductionBehaviours) {
        return ReductionBehaviours.get(queueReductionBehaviours);
    }

    private final EventReductionBehavior[] chain;

    private QueueReductionBehavioursImpl(EventReductionBehavior... chain) {
        this.chain = chain;
    }

    public List<QueueEventImpl> applyChain(List<QueueEventImpl> events) {
        return applyChain(chain, events);
    }

    public static List<QueueEventImpl> applyChain(EventReductionBehavior[] chain, List<QueueEventImpl> events) {
        List<QueueEventImpl> result = events;
        for (EventReductionBehavior behavior : chain) {
            result = behavior.apply(result);
        }
        return result;
    }

    private static QueueReductionBehavioursImpl getFederatedReplicationBehavior() {
        return ConstantValues.federatedSkipCreateEliminateDeleteOnDeduplication.getBoolean() ?
                new QueueReductionBehavioursImpl(new DeleteEliminateUpdateOrCreateBehavior(),
                        new FederatedDedupBehavior()) :
                new QueueReductionBehavioursImpl(
                        new CreateEliminateDeleteBehavior(),
                        new DeleteEliminateUpdateOrCreateBehavior(),
                        new FederatedDedupBehavior()
                );
    }
}
