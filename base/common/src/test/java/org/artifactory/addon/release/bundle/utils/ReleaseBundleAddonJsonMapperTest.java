package org.artifactory.addon.release.bundle.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * @author Yevdo Abramov
 * Created on 25/10/2023
 */
public class ReleaseBundleAddonJsonMapperTest {

    private ReleaseBundleAddonJsonMapper jsonMapper;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ObjectWriter objectWriter;
    private AutoCloseable openedMocks;

    @BeforeMethod
    public void setUp() {
        openedMocks = MockitoAnnotations.openMocks(this);
        jsonMapper = new ReleaseBundleAddonJsonMapper(objectMapper, objectWriter);
    }

    @AfterMethod
    public void tearDown() throws Exception {
        verifyNoMoreInteractions(objectMapper, objectWriter);
        openedMocks.close();
    }

    @Test
    public void testReadValue() throws IOException {
        jsonMapper.readValue("A".getBytes(), Object.class);
        verify(objectMapper).readValue("A".getBytes(), Object.class);
    }

    @Test
    public void testIOReadValue() throws IOException {
        InputStream inputStream = new ByteArrayInputStream("A".getBytes());
        jsonMapper.readValue(inputStream, Object.class);
        verify(objectMapper).readValue(inputStream, Object.class);
    }

    @Test
    public void testWriteAsJson() throws JsonProcessingException {
        List<String> list = List.of("a", "b");
        jsonMapper.writeAsJson(list);
        verify(objectWriter).writeValueAsString(list);
    }
}