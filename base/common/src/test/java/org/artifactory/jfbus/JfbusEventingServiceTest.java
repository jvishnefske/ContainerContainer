package org.artifactory.jfbus;

import jakarta.ws.rs.core.MediaType;
import org.artifactory.api.context.ArtifactoryContext;
import org.artifactory.api.context.ArtifactoryContextThreadBinder;
import org.artifactory.security.access.AccessService;
import org.artifactory.test.ArtifactoryHomeBoundJUnitTest;
import org.jfrog.access.common.ServiceId;
import org.jfrog.eventing.facade.ConsumeOptions;
import org.jfrog.eventing.facade.EventingFacade;
import org.jfrog.eventing.facade.Message;
import org.jfrog.eventing.facade.MessageBatch;
import org.jfrog.eventing.facade.TopicCreationOptions;
import org.jfrog.eventing.facade.TopicId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.artifactory.jfbus.JfbusConstants.HEADER_CONTENT_TYPE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_ID;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SOURCE;
import static org.artifactory.jfbus.JfbusConstants.HEADER_SPEC_VERSION;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TIME;
import static org.artifactory.jfbus.JfbusConstants.HEADER_TYPE;
import static org.artifactory.jfbus.JfbusConstants.PACKAGE_CREATED_EVENT_TYPE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class JfbusEventingServiceTest extends ArtifactoryHomeBoundJUnitTest {

    private static final String TOPIC_KEY = "test-key";
    private static final byte[] PAYLOAD = new byte[]{1, 2, 3};
    private final TopicId topicId = new TopicId("artifactory", "artifactory-package-indexed-events");

    @Mock
    private EventingFacade eventingFacade;

    @Mock
    private AccessService accessService;

    private AutoCloseable mocks;

    @Mock
    private ArtifactoryContext context;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        ServiceId serviceId = mock(ServiceId.class);
        when(serviceId.getFormattedName()).thenReturn("artifactory-0");
        when(accessService.getArtifactoryServiceId()).thenReturn(serviceId);
        when(context.getArtifactoryHome()).thenReturn(getOrCreateArtifactoryHomeStub());
        when(context.beanForType(AccessService.class)).thenReturn(accessService);
        ArtifactoryContextThreadBinder.bind(context);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
        ArtifactoryContextThreadBinder.unbind();
    }

    @Test
    void testConstructor() {
        JfbusEventingService service = new JfbusEventingService(Optional.of(eventingFacade));
        assertNotNull(service);
    }

    @Test
    void isConfigured_reflectsClientPresence() {
        assertTrue(new JfbusEventingService(Optional.of(eventingFacade)).isConfigured());
        assertFalse(new JfbusEventingService(Optional.empty()).isConfigured());
    }

    @Test
    void testCreateTopic_withClientPresent() {
        JfbusEventingService service = new JfbusEventingService(Optional.of(eventingFacade));
        int partitions = 2;
        boolean skipIfExists = true;

        service.createTopic(topicId, partitions, skipIfExists);

        ArgumentCaptor<TopicId> topicIdCaptor = ArgumentCaptor.forClass(TopicId.class);
        ArgumentCaptor<TopicCreationOptions> optionsCaptor =
                ArgumentCaptor.forClass(TopicCreationOptions.class);
        verify(eventingFacade).createTopic(topicIdCaptor.capture(), optionsCaptor.capture());
        assertEquals(topicId, topicIdCaptor.getValue());
        TopicCreationOptions options = optionsCaptor.getValue();
        assertEquals(partitions, options.getPartitions());
        assertEquals(skipIfExists, options.getSkipIfExists());
    }

    @Test
    void testCreateTopic_withClientEmpty() {
        JfbusEventingService service = new JfbusEventingService(Optional.empty());
        service.createTopic(topicId, 2, true);
        // No exception; produce/createTopic never called (no facade to verify)
    }

    @Test
    void testSendEvent_withClientPresent() throws Exception {
        JfbusEventingService service = new JfbusEventingService(Optional.of(eventingFacade));
        Map<String, String> headers = Map.of("custom", "value");

        service.sendEvent(topicId, TOPIC_KEY, PAYLOAD, headers);

        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messageCaptor.capture(), eq(topicId));
        List<Message> messages = messageCaptor.getValue();
        assertEquals(1, messages.size());
        Message message = messages.getFirst();
        assertEquals(TOPIC_KEY, message.getKey());
        assertEquals(PAYLOAD, message.getPayload());
        assertEquals("value", message.getHeaders().get("custom"));
    }

    @Test
    void sendEventBlocking_usesSameProducePipelineAsSendEvent() throws Exception {
        JfbusEventingService service = new JfbusEventingService(Optional.of(eventingFacade));

        service.sendEventBlocking(topicId, "sync-key", new byte[] {1},
                Map.of(HEADER_TYPE, PACKAGE_CREATED_EVENT_TYPE));

        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messageCaptor.capture(), eq(topicId));
        List<Message> messages = messageCaptor.getValue();
        assertEquals(1, messages.size());
        Message message = messages.getFirst();
        assertEquals("sync-key", message.getKey());
        assertArrayEquals(new byte[] {1}, message.getPayload());
        assertEquals(PACKAGE_CREATED_EVENT_TYPE, message.getHeaders().get(HEADER_TYPE));
    }

    @Test
    void testSendEvent_withClientEmpty() throws Exception {
        JfbusEventingService service = new JfbusEventingService(Optional.empty());
        service.sendEvent(topicId, TOPIC_KEY, PAYLOAD, Map.of());
        // No exception; produce is never called (eventingClient.isEmpty())
    }

    @Test
    void testSendEvent_populatesGenericHeaders() throws Exception {
        JfbusEventingService service = new JfbusEventingService(Optional.of(eventingFacade));

        service.sendEvent(topicId, TOPIC_KEY, PAYLOAD, Map.of());

        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messageCaptor.capture(), eq(topicId));
        Map<String, String> headers = messageCaptor.getValue().getFirst().getHeaders();

        assertEquals("1.0", headers.get(HEADER_SPEC_VERSION));
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, headers.get(HEADER_CONTENT_TYPE));
        assertEquals("artifactory://artifactory-0@Artifactory", headers.get(HEADER_SOURCE));
        assertTrue(headers.containsKey(HEADER_ID));
        assertTrue(headers.containsKey(HEADER_TIME));
    }

    @Test
    void testSendEvent_preservesCallerHeaders() throws Exception {
        JfbusEventingService service = new JfbusEventingService(Optional.of(eventingFacade));
        String customType = "custom-event-type";
        Map<String, String> callerHeaders = Map.of(JfbusConstants.HEADER_TYPE, customType);

        service.sendEvent(topicId, TOPIC_KEY, PAYLOAD, callerHeaders);

        ArgumentCaptor<List<Message>> messageCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventingFacade).produceViaOutbox(messageCaptor.capture(), eq(topicId));
        assertEquals(customType,
                messageCaptor.getValue().getFirst().getHeaders().get(JfbusConstants.HEADER_TYPE));
    }

    @Test
    void testSendEvent_throwsWhenProduceViaOutboxFails() throws Exception {
        JfbusEventingService service = new JfbusEventingService(Optional.of(eventingFacade));
        RuntimeException expected = new RuntimeException("produceViaOutbox failed");
        doThrow(expected).when(eventingFacade).produceViaOutbox(any(), eq(topicId));

        Exception thrown = assertThrows(Exception.class,
                () -> service.sendEvent(topicId, TOPIC_KEY, PAYLOAD, Map.of()));

        assertEquals(expected, thrown);
    }

    @Test
    void consumeTopic_returnsFalseWhenClientEmpty() throws Exception {
        JfbusEventingService service = new JfbusEventingService(Optional.empty());
        assertFalse(service.consumeTopic(topicId, "sub", ConsumeOptions.defaults(), batch -> {}));
    }

    @Test
    void consumeTopic_delegatesWhenClientPresent() throws Exception {
        JfbusEventingService service = new JfbusEventingService(Optional.of(eventingFacade));
        Consumer<MessageBatch> handler = batch -> {};
        assertTrue(service.consumeTopic(topicId, "sub", ConsumeOptions.defaults(), handler));
        verify(eventingFacade).consume(eq(topicId), eq("sub"), any(ConsumeOptions.class), eq(handler));
    }
}
