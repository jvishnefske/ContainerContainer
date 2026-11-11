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
class NoopMBeanServer implements MBeanServerProxy {

    static final NoopMBeanServer NOOP_M_BEAN_SERVER = new NoopMBeanServer();

    @Override
    public Object getAttribute(ObjectName name, String attribute)
            throws ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        return null;
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        return Set.of();
    }

    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        return Set.of();
    }
}
