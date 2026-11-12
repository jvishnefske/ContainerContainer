# The fork's jars, built from source with Maven inside the Nix sandbox.
#
# Two derivations, both from nixpkgs' maven.buildMavenPackage:
#   - a fixed-output derivation (hash in nix/mvn-hash.txt) that runs the Maven build once with network access and
#     keeps only the local repository it populated, from Maven Central and jfrog-jars (.github/maven/settings.xml);
#   - the real build, offline (`mvn -o`) against a copy of that repository, with tests skipped.
#
# Outputs: war/artifactory.war (the whole webapp, for a future from-scratch image) and lib/ with the overlay set.
# The overlay set is chosen exactly as docker/build-overlay.sh chooses it, but against the committed stock listing
# nix/stock-lib.txt instead of a running image (no image is available inside the sandbox; the listing was taken
# from releases-docker.jfrog.io/jfrog/artifactory-oss:7.161.26, digest sha256:d103c046...aeac1d1):
#   replace = stock jars named artifactory-*-<source version>.jar; each must be in the built WAR's WEB-INF/lib;
#   add     = jars in the built WAR's WEB-INF/lib that the stock listing lacks;
# and every artifactory-*-<source version>.jar the WAR packages must be a stock jar (never silently added).
{
  lib,
  maven,
  jdk25,
  src,
  version,
  srcVersion,
  stockListing,
  mvnHash,
}:
maven.buildMavenPackage {
  pname = "containercontainer-jars-build";
  inherit version src mvnHash;
  mvnJdk = jdk25;

  # Only the WAR and the modules it depends on; bins/devenv/coverage are distribution packaging the overlay never
  # uses. The fixed timestamp makes the jars byte-reproducible (maven-jar-plugin / maven-war-plugin honour it).
  mvnParameters = lib.concatStringsSep " " [
    "-s .github/maven/settings.xml"
    "--no-transfer-progress"
    "-pl web/war -am"
    "-Dproject.build.outputTimestamp=1980-01-01T00:00:02Z"
    "-Dmaven.javadoc.skip=true"
  ];
  doCheck = false;
  dontFixup = true;

  installPhase = ''
    runHook preInstall

    warLib=web/war/target/artifactory/WEB-INF/lib
    [ -d "$warLib" ] || { echo "no built WAR at $warLib" >&2; exit 1; }
    stock=${stockListing}
    srcVer=${srcVersion}

    mkdir -p "$out/lib"
    : > "$out/replace.txt"
    : > "$out/add.txt"

    # replace: every stock artifactory-*-<srcVer>.jar must have exactly one built counterpart in the WAR.
    grep -x "artifactory-.*-$srcVer\.jar" "$stock" | while read -r jar; do
      [ -f "$warLib/$jar" ] || { echo "stock jar $jar has no built counterpart in the WAR" >&2; exit 1; }
      install -m 0644 "$warLib/$jar" "$out/lib/$jar"
      echo "$jar" >> "$out/replace.txt"
    done
    # every module jar the WAR packages must be one the stock image has
    ls "$warLib" | grep -x "artifactory-.*-$srcVer\.jar" | while read -r jar; do
      grep -qx "$jar" "$out/replace.txt" || { echo "WAR jar $jar is not in the stock listing; refusing to add it" >&2; exit 1; }
    done
    # add: runtime dependencies of the WAR that the stock image lacks
    ls "$warLib" | LC_ALL=C sort | while read -r jar; do
      grep -qx "$jar" "$stock" && continue
      install -m 0644 "$warLib/$jar" "$out/lib/$jar"
      echo "$jar" >> "$out/add.txt"
    done

    [ -s "$out/replace.txt" ] || { echo "nothing to replace" >&2; exit 1; }
    install -D -m 0644 web/war/target/artifactory.war "$out/war/artifactory.war"
    cp "$stock" "$out/stock.txt"
    LC_ALL=C sort -u "$stock" "$out/add.txt" > "$out/expected.txt"

    runHook postInstall
  '';

  meta = {
    description = "ContainerContainer (Artifactory OSS fork) jars built from source";
    license = lib.licenses.agpl3Plus;
  };
}
