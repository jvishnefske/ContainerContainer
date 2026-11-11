package org.artifactory.probes.tomcat;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;
import java.util.Set;

/**
 * @author Igor Usenko
 */
class TomcatMBeanServer implements MBeanServerProxy {

    private final MBeanServer mBeanServer;

    public TomcatMBeanServer(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
    }

    @Override
    public Object getAttribute(ObjectName name, String attribute)
            throws ReflectionException, AttributeNotFoundException, InstanceNotFoundException, MBeanException {
        return mBeanServer.getAttribute(name, attribute);
    }

    @Override
    public Set<ObjectName> queryNames(ObjectName name, QueryExp query) {
        return mBeanServer.queryNames(name, query);
    }

    @Override
    public Set<ObjectInstance> queryMBeans(ObjectName name, QueryExp query) {
        return mBeanServer.queryMBeans(name, query);
    }
}
