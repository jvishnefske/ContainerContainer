package org.artifactory.version.converter.v304;

import org.artifactory.version.converter.v160.AddonsLayoutConverterTestBase;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.testng.annotations.Test;

import static org.artifactory.version.converter.v304.RemoveTaskAffinityConverter.TASK_EXECUTOR;
import static org.testng.AssertJUnit.assertNull;


public class RemoveTaskAffinityConverterTest extends AddonsLayoutConverterTestBase {

    @Test
    protected void testRemoveAffinityConverter() throws Exception {
        Document doc =
                convertXml("/config/test/config.3.0.4_remove_task_affinity.xml", new RemoveTaskAffinityConverter());
        Element rootElement = doc.getRootElement();
        Namespace namespace = rootElement.getNamespace();
        Element affinityConfig = rootElement.getChild(TASK_EXECUTOR, namespace);
        assertNull(affinityConfig);
    }
}