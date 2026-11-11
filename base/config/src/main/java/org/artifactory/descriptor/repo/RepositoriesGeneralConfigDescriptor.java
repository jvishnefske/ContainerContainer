package org.artifactory.descriptor.repo;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.Getter;
import lombok.Setter;
import org.artifactory.descriptor.Descriptor;
import org.jfrog.common.config.diff.GenerateDiffFunction;

@XmlRootElement
@XmlType(name = "RepositoriesGeneralType",
        propOrder = {"helm", "nuget", "rpm", "docker", "huggingfaceml", "pypi", "debian"},
        namespace = Descriptor.NS)
@XmlAccessorType(XmlAccessType.FIELD)
@GenerateDiffFunction
public class RepositoriesGeneralConfigDescriptor implements Descriptor {

    @XmlElement(name = "helm")
    @Getter
    @Setter
    private HelmGeneralConfig helm = new HelmGeneralConfig();

    @XmlElement(name = "nuget")
    @Getter
    @Setter
    private NugetGeneralConfig nuget = new NugetGeneralConfig();

    @XmlElement(name = "rpm")
    @Getter
    @Setter
    private RpmGeneralConfig rpm = new RpmGeneralConfig();

    @XmlElement(name = "docker")
    @Setter
    @Getter
    private DockerGeneralConfig docker = new DockerGeneralConfig();

    @XmlElement(name = "huggingfaceml")
    @Setter
    @Getter
    private HuggingFaceMlGeneralConfig huggingfaceml = new HuggingFaceMlGeneralConfig();

    @XmlElement(name = "pypi")
    @Setter
    @Getter
    private PypiGeneralConfig pypi = new PypiGeneralConfig();

    @XmlElement(name = "debian")
    @Setter
    @Getter
    private DebianGeneralConfig debian = new DebianGeneralConfig();

}