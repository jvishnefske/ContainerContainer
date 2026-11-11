package org.artifactory.probes.tomcat;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import java.util.Set;

/**
 * @author Igor Usenko
 */
public interface MBeanServerProxy {

    Object getAttribute(ObjectName name, String attribute)
            throws ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException;

    Set<ObjectName> queryNames(ObjectName name, QueryExp query);

    Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query);
}
