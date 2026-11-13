# The Artifactory webapp of the minimal image, cut from packages.war (built from source by nix/jars.nix).
#
# Two outputs, so the image gets two layers that change at different rates:
#   fork        the webapp docBase: WEB-INF/web.xml and WEB-INF/lib/artifactory-*-<srcVersion>.jar, the fork's own
#               build outputs (they change with every commit);
#   thirdparty  every other WEB-INF/lib jar (open-source dependencies from Maven Central and jfrog-jars; they
#               change only with dependency updates). The Tomcat context mounts it at /WEB-INF/lib.
#
# What is left out, and why (each removal was checked with docker/it/run-min-it.sh, see design.md N6):
#   - the web UI static assets inside artifactory-web-application (about 100 MB of JavaScript, CSS and images);
#     the image serves the REST API, repositories and plugins only, and the UI also needs JFrog's closed frontend
#     service, which is not shipped;
#   - optional jars the server does not load unless configured for them: the Oracle JDBC driver, the AWS RDS
#     helpers and the Kubernetes Java client (io.kubernetes:client-java*, used only for cluster discovery; not
#     org.jfrog.worker:client-java, which the server needs);
#   - build-time annotation processors the WAR packages by accident (Lombok, MapStruct processor);
#   - native libraries for other platforms inside zstd-jni, JNA, grpc-netty-shaded and byte-buddy-agent.
# The rest is re-stored uncompressed by slim-webapp.py (signed jars stay byte for byte).
{
  lib,
  runCommand,
  python3,
  unzip,
  war,
  srcVersion,
  hostPlatform,
}:
let
  nativeKeep =
    {
      x86_64 = [
        "(^|/)linux/amd64/"
        "(^|/)linux/x86_64/"
        "(^|/)linux-x86-64/"
        "_linux_x86_64\\.so$"
      ];
      aarch64 = [
        "(^|/)linux/aarch64/"
        "(^|/)linux-aarch64/"
        "_linux_aarch_64\\.so$"
      ];
    }
    .${hostPlatform.parsed.cpu.name}
      or (throw "minimal image: unsupported CPU ${hostPlatform.parsed.cpu.name}");
  rules = builtins.toJSON {
    forkJar = "artifactory-.*-${lib.escapeRegex srcVersion}\\.jar";
    drop = [
      "ojdbc8-23.26.2.0.0.jar"
      "rds-2.46.17.jar"
      "client-java-21.0.2.jar"
      "client-java-api-21.0.2.jar"
      "client-java-proto-21.0.2.jar"
      "lombok-1.18.46.jar"
      "lombok-mapstruct-binding-0.2.0.jar"
      "mapstruct-processor-1.6.3.jar"
    ];
    uiJar = "artifactory-web-application-${srcVersion}.jar";
    uiPrefixes = [
      "img/"
      "js/"
      "packages/"
      "rtui/"
      "buildinfomfe/"
      "index.html"
    ];
    inherit nativeKeep;
  };
in
runCommand "containercontainer-webapp-${srcVersion}"
  {
    outputs = [
      "out"
      "fork"
      "thirdparty"
    ];
    nativeBuildInputs = [
      python3
      unzip
    ];
    passAsFile = [ "rules" ];
    inherit rules;
    meta.license = lib.licenses.agpl3Plus;
  }
  ''
    mkdir war
    unzip -q ${war}/artifactory.war -d war
    python3 -I ${./slim-webapp.py} war "$rulesPath" "$fork" "$thirdparty" | tee log.txt
    mkdir -p "$out"
    cp log.txt "$out/slim.log"
    (cd "$fork/WEB-INF/lib" && ls) > "$out/fork-jars.txt"
    (cd "$thirdparty" && ls) > "$out/thirdparty-jars.txt"
  ''
