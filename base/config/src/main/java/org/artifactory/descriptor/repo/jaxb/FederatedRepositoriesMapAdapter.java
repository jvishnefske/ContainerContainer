package org.artifactory.descriptor.repo.jaxb;

import com.google.common.collect.Maps;
import org.artifactory.descriptor.Descriptor;
import org.artifactory.descriptor.repo.FederatedRepoDescriptor;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FederatedRepositoriesMapAdapter extends
        XmlAdapter<FederatedRepositoriesMapAdapter.Wrappper, Map<String, FederatedRepoDescriptor>> {

    @Override
    public Map<String, FederatedRepoDescriptor> unmarshal(FederatedRepositoriesMapAdapter.Wrappper wrapper) {
        Map<String, FederatedRepoDescriptor> federatedRepositoriesMap = Maps.newLinkedHashMap();
        for (FederatedRepoDescriptor repository : wrapper.getList()) {
            String key = repository.getKey();
            FederatedRepoDescriptor repo = federatedRepositoriesMap.put(key, repository);
            //Test for repositories with the same key
            if (repo != null) {
                //Throw an error since jaxb swallows exceptions
                throw new Error(
                        "Duplicate repository key in configuration: " + key + ".");
            }
        }
        return federatedRepositoriesMap;
    }

    @Override
    public FederatedRepositoriesMapAdapter.Wrappper marshal(Map<String, FederatedRepoDescriptor> map) {
        return new FederatedRepositoriesMapAdapter.Wrappper(map);
    }

    @XmlType(name = "FederatedRepositoriesType", namespace = Descriptor.NS)
    public static class Wrappper {
        @XmlElement(name = "federatedRepository", required = true, namespace = Descriptor.NS)
        private final List<FederatedRepoDescriptor> list = new ArrayList<>();

        public Wrappper() {
        }

        public Wrappper(Map<String, FederatedRepoDescriptor> map) {
            list.addAll(map.values());
        }

        public List<FederatedRepoDescriptor> getList() {
            return list;
        }
    }
}


