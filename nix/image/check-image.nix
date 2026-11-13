# checks.image-structure and checks.image-example-structure: run check-image.py against an image from
# lib.mkImage. The image must have exactly the jar set of the fork's WAR (minus the removed jars), Tomcat, the
# JDBC drivers and the JRE's jrt-fs.jar; ELF files only from the JRE and busybox closures (nixpkgs builds); none
# of the JFrog services the stock image carries; the expected files; the layer order of nix/image.nix; and a
# non-root user owning the var volume.
{
  lib,
  runCommand,
  python3,
  zstd,
  unzip,
  closureInfo,
  busybox,
  image,
  war,
  plugins ? [ ],
}:
let
  inherit (image.passthru) parts;
  home = "/opt/jfrog/artifactory";
  elfRoots = closureInfo {
    rootPaths = [
      parts.jre
      busybox
    ];
  };
  # Path components of the stock image's other services and tools, none of which may appear anywhere.
  services = [
    "access"
    "router"
    "frontend"
    "event"
    "metadata"
    "observability"
    "topology"
    "jfconfig"
    "jfbus"
    "jfconnect"
    "evidence"
    "onemodel"
    "jfmelt"
    "node"
    "yq"
    "third-party"
  ];
  expect = {
    layers = [
      "busybox"
      "jre"
      "tomcat"
      "thirdparty"
      "fork"
      "entrypoint"
      "plugins"
      "customisation"
    ];
    files = [
      "${parts.entrypoint}/bin/artifactory"
      "${parts.jre}/bin/java"
      "${parts.jre}/lib/modules"
      "${parts.tomcat}/bin/bootstrap.jar"
      "${parts.tomcat}/lib/catalina.jar"
      "${parts.tomcat}/lib/derby-10.17.1.0.jar"
      "${parts.config}/tomcat/conf/server.xml"
      "${parts.config}/tomcat/conf/Catalina/localhost/artifactory.xml"
      "${parts.config}/tomcat/webapps/ROOT/WEB-INF/rewrite.config"
      "${parts.config}/app/LICENSE"
      "${parts.config}/app/NOTICE"
      "${parts.config}/app/artifactory.product.version.properties"
      "${parts.webapp.fork}/WEB-INF/web.xml"
      "${parts.webapp.fork}/WEB-INF/lib/artifactory-core-7.161.15.jar"
      "${parts.webapp.thirdparty}/groovy-4.0.32.jar"
      "/etc/passwd"
      "/etc/group"
      "/etc/ssl/certs/ca-certificates.crt"
      "/bin"
      "/tmp"
      "${home}/app"
      "${home}/var"
      "${home}/plugins"
      "/var/opt/jfrog/artifactory"
    ]
    ++ map (p: "${parts.pluginsDir}/${p}") plugins;
    forbidden = [
      "(^|/)(${lib.concatStringsSep "|" services})(/|$)"
      "jfrog-tomcat-lib"
      "access-server"
      "\\.war$"
    ];
    user = "1030:1030";
    uid = 1030;
    var = "/var/opt/jfrog/artifactory";
  };
in
runCommand "check-${image.name}-structure"
  {
    nativeBuildInputs = [
      python3
      zstd
      unzip
    ];
    expectJson = builtins.toJSON expect;
    passAsFile = [ "expectJson" ];
  }
  ''
    # The allowed jar set: the WAR's own jars that the webapp keeps, and Tomcat's.
    unzip -Z1 ${war}/artifactory.war | sed -n 's|^WEB-INF/lib/\([^/]*\.jar\)$|\1|p' | sort > war-jars
    cat ${parts.webapp}/fork-jars.txt ${parts.webapp}/thirdparty-jars.txt | sort > kept-jars
    comm -13 war-jars kept-jars > not-from-war
    [ ! -s not-from-war ] || { echo "webapp jars not in the WAR:"; cat not-from-war; exit 1; } >&2
    (cd ${parts.tomcat} && ls bin/*.jar lib/*.jar | xargs -n1 basename) >> kept-jars
    find ${parts.jre} -name '*.jar' -printf '%f\n' >> kept-jars   # jrt-fs.jar, the JDK's own
    python3 -I - "$expectJsonPath" kept-jars ${elfRoots}/store-paths > expect.json <<'EOF'
    import json, sys
    expect = json.load(open(sys.argv[1]))
    expect["jars"] = sorted(set(open(sys.argv[2]).read().split()))
    expect["elfPrefixes"] = open(sys.argv[3]).read().split()
    print(json.dumps(expect))
    EOF
    python3 -I ${./check-image.py} ${image}/${image.fileName} expect.json | tee $out
  ''
