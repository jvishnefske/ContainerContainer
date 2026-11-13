# CATALINA_HOME of the minimal image: Apache Tomcat 11 from nixpkgs (tomcat11), cut to the jars the stock
# Artifactory 7.161.26 image's Tomcat has (its lib/ minus JFrog's closed jfrog-tomcat-lib.jar and the
# jakartaee-migration tool), plus the JDBC drivers that image puts next to them, fetched from Maven Central:
# Apache Derby 10.17.1.0 (the embedded default database) and PostgreSQL 42.7.13. The hashes are those of the
# stock image's copies.
#
# No JSP engine, clustering, i18n bundles or scripts: the launcher starts org.apache.catalina.startup.Bootstrap
# directly, and the configuration (CATALINA_BASE) comes from the image's config layer.
{
  lib,
  runCommand,
  fetchurl,
  tomcat11,
}:
let
  maven =
    path: hash:
    fetchurl {
      url = "https://repo1.maven.org/maven2/${path}";
      inherit hash;
    };
  drivers = [
    (maven "org/apache/derby/derby/10.17.1.0/derby-10.17.1.0.jar" "sha256-dk5MEz+GCoh26DXvIwbv7K0np0LSfwW8S85mlDLGs5c=")
    (maven "org/apache/derby/derbyshared/10.17.1.0/derbyshared-10.17.1.0.jar" "sha256-+2i6sweFN1wfjLXpWDs0l1D4kE1vfuFc6OHDC2vjDvQ=")
    (maven "org/apache/derby/derbytools/10.17.1.0/derbytools-10.17.1.0.jar" "sha256-weJOR9lEp8TG+oitGgmJ18835BCNwvvejLB+dYCHmW4=")
    (maven "org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar" "sha256-bg5MwtjK6QIIT4orGHKLBzpv2dH4fJ2L/48pjBgYW5M=")
  ];
  tomcatLib = [
    "annotations-api.jar"
    "catalina.jar"
    "jaspic-api.jar"
    "servlet-api.jar"
    "tomcat-api.jar"
    "tomcat-coyote-ffm.jar"
    "tomcat-coyote.jar"
    "tomcat-jni.jar"
    "tomcat-util-scan.jar"
    "tomcat-util.jar"
    "websocket-client-api.jar"
  ];
in
runCommand "containercontainer-tomcat-${tomcat11.version}"
  {
    passthru = { inherit (tomcat11) version; };
    meta.license = lib.licenses.asl20;
  }
  ''
    mkdir -p "$out/bin" "$out/lib"
    cp ${tomcat11}/bin/bootstrap.jar ${tomcat11}/bin/tomcat-juli.jar "$out/bin/"
    for jar in ${lib.escapeShellArgs tomcatLib}; do cp ${tomcat11}/lib/$jar "$out/lib/"; done
    ${lib.concatMapStringsSep "\n" (d: ''cp ${d} "$out/lib/${d.name}"'') drivers}
    cp ${tomcat11}/LICENSE ${tomcat11}/NOTICE "$out/"
    chmod 0444 "$out"/bin/* "$out"/lib/*
  ''
