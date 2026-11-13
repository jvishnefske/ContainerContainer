# lib.mkImage: the minimal from-scratch OCI image of the fork (design.md N3, N6).
#
#   mkImage { pkgs, plugins ? [ ], extraConfig ? { }, tag ? version }
#
# Built with dockerTools.streamLayeredImage (no base image) from Nix-built parts only: busybox and CA certificates,
# a jlink'd OpenJDK 25 (nix/image/jre.nix), Apache Tomcat 11 with the Derby and PostgreSQL drivers
# (nix/image/tomcat.nix), the fork's webapp cut from packages.war (nix/image/webapp.nix), a config layer (POSIX sh
# launcher, Tomcat configuration, the ROOT rewrite webapp, LICENSE and NOTICE) and the plugins. Nothing comes from
# JFrog's stock image: no Access, router, frontend or any other JFrog service. Access runs embedded in the webapp
# (users, groups, permissions and tokens in Artifactory's Derby or PostgreSQL database), so the image needs no other
# service and carries no Access configuration and no default credential: on first start Artifactory writes a
# one-time setup token to var/etc/security/initial-admin.token (or uses INITIAL_ADMIN_TOKEN_FILE), and the server
# answers 503 until it is claimed with POST /artifactory/api/setup/initial-admin.
#
# Layers, in this order so that what changes least comes first: base (busybox, glibc, CA certificates), JRE, Tomcat,
# third-party jars, the fork's jars, config, plugins, then the small customisation layer (/etc/passwd, /bin,
# /opt/jfrog/artifactory links, the var directory owned by the artifactory user). The layering pipeline takes each
# group's closure minus the groups before it.
#
# Arguments:
#   plugins      files or directories (for example lib.buildWasmPlugin outputs); every regular file at their top
#                level is installed read-only in /opt/jfrog/artifactory/plugins and copied into
#                var/etc/artifactory/plugins by the launcher on each start. Allowed: *.groovy, *.wasm (with its
#                *.wasm.yaml), *.wasm.yaml; names must be unique.
#   extraConfig  merged into the OCI image config (Env lists are appended, Labels merged).
#
# Result: a derivation with <name>-<tag>.oci.tar (OCI layout with zstd layers, also loadable by docker load) and
# layers.tsv (per-layer uncompressed and compressed sizes). passthru: stream (the uncompressed docker-archive
# stream script), parts (jre, tomcat, webapp, config, pluginsDir), imageName, imageTag.
{
  pkgs,
  war,
  version,
  srcVersion,
  sourceRev,
  license,
  rootWebapp,
  created ? "1970-01-01T00:00:01Z",
  plugins ? [ ],
  extraConfig ? { },
  name ? "containercontainer",
  tag ? version,
  zstdLevel ? 19,
}:
let
  inherit (pkgs) lib;
  uid = 1030;
  gid = 1030;
  home = "/opt/jfrog/artifactory";
  var = "/var/opt/jfrog/artifactory";
  repo = "https://github.com/jvishnefske/ContainerContainer";

  jre = pkgs.callPackage ./image/jre.nix {
    modules = lib.filter (l: l != "" && !(lib.hasPrefix "-" l)) (
      map (l: lib.trim (lib.head (lib.splitString "#" l))) (
        lib.splitString "\n" (lib.readFile ./image/jre-modules.txt)
      )
    );
  };
  tomcat = pkgs.callPackage ./image/tomcat.nix { };
  webapp = pkgs.callPackage ./image/webapp.nix {
    inherit war srcVersion;
    inherit (pkgs.stdenv) hostPlatform;
  };

  notice = ''
    ContainerContainer ${version} - minimal OCI image
    A fork of JFrog Artifactory OSS 7.161.26, not affiliated with or endorsed by JFrog.

    Built from source revision ${sourceRev} of ${repo} with the Nix flake in that repository
    (lib.mkImage). The image contains the fork's Artifactory webapp, Apache Tomcat ${tomcat.version}, an OpenJDK 25
    runtime, Apache Derby, the PostgreSQL JDBC driver, busybox and CA certificates, all built or fetched by Nix.
    It contains no JFrog Access, router, frontend or other JFrog service binaries.

    The fork is licensed under the GNU Affero General Public License v3 or later; see LICENSE. Source offer: the
    complete corresponding source is the repository above at revision ${sourceRev}, available at no charge.
  '';

  productVersion = ''
    artifactory.product.version=7.161.26
    artifactory.product.revision=86126900
    artifactory.product.timestamp=20260907150505
    artifactory.product.buildNumber=${version}
  '';

  artifactoryContext = ''
    <?xml version="1.0" encoding="UTF-8"?>
    <!-- The Artifactory webapp: the fork's jars in the docBase, the third-party jars mounted at /WEB-INF/lib. -->
    <Context crossContext="true" docBase="${webapp.fork}" unloadDelay="5000" clearReferencesThreadLocals="false">
        <Resources cachingAllowed="false">
            <PostResources className="org.apache.catalina.webresources.DirResourceSet"
                           base="${webapp.thirdparty}" webAppMount="/WEB-INF/lib" readOnly="true"/>
        </Resources>
    </Context>
  '';

  launcher = pkgs.replaceVars ./image/launcher.sh {
    shell = "${pkgs.busybox}/bin/sh";
    java = "${jre}/bin/java";
    base = "${config}/tomcat";
    inherit tomcat;
  };

  config =
    pkgs.runCommand "containercontainer-config-${version}"
      {
        inherit notice productVersion artifactoryContext;
        passAsFile = [
          "notice"
          "productVersion"
          "artifactoryContext"
        ];
      }
      ''
        install -D -m 0644 ${./image/tomcat-conf/server.xml} "$out/tomcat/conf/server.xml"
        install -D -m 0644 ${./image/tomcat-conf/context.xml} "$out/tomcat/conf/context.xml"
        install -D -m 0644 ${./image/tomcat-conf/web.xml} "$out/tomcat/conf/web.xml"
        install -D -m 0644 ${./image/tomcat-conf/logging.properties} "$out/tomcat/conf/logging.properties"
        install -D -m 0644 ${./image/tomcat-conf/catalina.properties} "$out/tomcat/conf/catalina.properties"
        install -D -m 0644 "$artifactoryContextPath" "$out/tomcat/conf/Catalina/localhost/artifactory.xml"
        mkdir -p "$out/tomcat/webapps"
        cp -r ${rootWebapp} "$out/tomcat/webapps/ROOT"
        install -D -m 0644 "$productVersionPath" "$out/app/artifactory.product.version.properties"
        install -D -m 0644 ${license} "$out/app/LICENSE"
        install -D -m 0644 "$noticePath" "$out/app/NOTICE"
      '';

  # The launcher refers to config (CATALINA_BASE), so it lives in its own tiny derivation beside it.
  entrypoint = pkgs.runCommand "containercontainer-entrypoint-${version}" { } ''
    install -D -m 0555 ${launcher} "$out/bin/artifactory"
  '';

  pluginsDir = pkgs.runCommand "containercontainer-plugins" { } ''
    mkdir -p "$out"
    add() { # add <file> <name>
      case "$2" in
        *.groovy|*.wasm|*.wasm.yaml) ;;
        *) echo "plugin file $2: not .groovy, .wasm or .wasm.yaml" >&2; exit 1 ;;
      esac
      [ ! -e "$out/$2" ] || { echo "plugin file $2 given twice" >&2; exit 1; }
      install -m 0444 "$1" "$out/$2"
    }
    for p in ${lib.escapeShellArgs (map (p: "${p}") plugins)}; do
      if [ -d "$p" ]; then
        for f in "$p"/*; do [ -f "$f" ] && add "$f" "$(basename "$f")"; done
      else
        name=$(basename "$p")
        add "$p" "''${name#*-}"   # a single file in the store is <hash>-<name>
      fi
    done
    for wasm in "$out"/*.wasm; do
      [ -e "$wasm" ] || continue
      [ -e "$wasm.yaml" ] || { echo "$(basename "$wasm") has no $(basename "$wasm").yaml manifest" >&2; exit 1; }
    done
    ls "$out"
  '';

  groups = [
    [
      pkgs.busybox
      pkgs.cacert
    ]
    [ jre ]
    [ tomcat ]
    [ webapp.thirdparty ]
    [ webapp.fork ]
    [
      config
      entrypoint
    ]
    [ pluginsDir ]
  ];
  # [["subcomponent_out", G1], ["over", "rest", ["pipe", <the same for G2...>]]]: one layer per group, in order.
  pipelineFor =
    gs:
    if gs == [ ] then
      [ ]
    else
      [
        [
          "subcomponent_out"
          (map toString (lib.head gs))
        ]
        [
          "over"
          "rest"
          [
            "pipe"
            (pipelineFor (lib.tail gs))
          ]
        ]
      ];

  baseConfig = {
    User = "${toString uid}:${toString gid}";
    Entrypoint = [ "${entrypoint}/bin/artifactory" ];
    WorkingDir = var;
    ExposedPorts."8081/tcp" = { };
    Volumes.${var} = { };
    Env = [
      "PATH=/bin"
      "JAVA_HOME=${jre}"
      "JF_PRODUCT_HOME=${home}"
      "SSL_CERT_FILE=/etc/ssl/certs/ca-certificates.crt"
    ];
    Labels = {
      "org.opencontainers.image.title" = "ContainerContainer";
      "org.opencontainers.image.description" =
        "Artifactory OSS 7.161.26 fork (REST, repositories, Groovy and WASM plugins) with embedded access; claim it with the setup token in var/etc/security/initial-admin.token";
      "org.opencontainers.image.version" = version;
      "org.opencontainers.image.revision" = sourceRev;
      "org.opencontainers.image.source" = repo;
      "org.opencontainers.image.licenses" = "AGPL-3.0-or-later";
    };
  };
  imageConfig = lib.recursiveUpdate baseConfig (removeAttrs extraConfig [ "Env" ]) // {
    Env = baseConfig.Env ++ (extraConfig.Env or [ ]);
  };

  stream = pkgs.dockerTools.streamLayeredImage {
    inherit name tag created;
    config = imageConfig;
    layeringPipeline = pipelineFor groups;
    contents = [ ];
    extraCommands = ''
      mkdir -p etc/ssl/certs tmp opt/jfrog/artifactory .${var}
      ln -s ${pkgs.busybox}/bin bin
      ln -s ${pkgs.cacert}/etc/ssl/certs/ca-bundle.crt etc/ssl/certs/ca-certificates.crt
      ln -s ${config}/app opt/jfrog/artifactory/app
      ln -s ${pluginsDir} opt/jfrog/artifactory/plugins
      ln -s ${var} opt/jfrog/artifactory/var
      printf '%s\n' 'root:x:0:0:root:/root:/bin/sh' \
        'artifactory:x:${toString uid}:${toString gid}:Artifactory:${var}:/bin/sh' \
        'nobody:x:65534:65534:nobody:/nonexistent:/bin/false' > etc/passwd
      printf '%s\n' 'root:x:0:' 'artifactory:x:${toString gid}:' 'nogroup:x:65534:' > etc/group
      printf 'hosts: files dns\n' > etc/nsswitch.conf
    '';
    fakeRootCommands = ''
      chown -R ${toString uid}:${toString gid} .${var}
      chmod 0750 .${var}
      chmod 1777 tmp
    '';
  };

  fileName = "${name}-${tag}.oci.tar";
in
pkgs.runCommand "${name}-${tag}-oci"
  {
    nativeBuildInputs = [ pkgs.python3 ];
    passthru = {
      inherit stream fileName;
      imageName = name;
      imageTag = tag;
      parts = {
        inherit
          jre
          tomcat
          webapp
          config
          entrypoint
          pluginsDir
          ;
      };
    };
    meta.license = lib.licenses.agpl3Plus;
  }
  ''
    mkdir -p "$out"
    ${stream} > "$TMPDIR/docker-archive.tar"
    python3 -I ${./image/to-oci.py} "$TMPDIR/docker-archive.tar" "$out/${fileName}" "$out/layers.tsv" \
      ${pkgs.zstd}/bin/zstd ${toString zstdLevel}
    cat "$out/layers.tsv"
  ''
