# packages.jars: the overlay jar set from the Maven build plus manifest.json (names, sha256s, replace/add role,
# source git revision). A separate cheap derivation, so a new commit rewrites the manifest without rebuilding Maven.
{
  lib,
  runCommand,
  jq,
  jarsBuild,
  version,
  srcVersion,
  sourceRev,
}:
runCommand "containercontainer-jars-${version}"
  {
    nativeBuildInputs = [ jq ];
    passthru = { inherit jarsBuild; };
    meta.license = lib.licenses.agpl3Plus;
  }
  ''
    mkdir -p "$out/lib"
    cp ${jarsBuild}/lib/*.jar "$out/lib/"
    chmod 0644 "$out"/lib/*.jar
    for f in replace add stock expected; do cp ${jarsBuild}/$f.txt "$out/$f.txt"; done

    {
      while read -r jar; do [ -n "$jar" ] && printf '%s\treplace\n' "$jar"; done < "$out/replace.txt"
      while read -r jar; do [ -n "$jar" ] && printf '%s\tadd\n' "$jar"; done < "$out/add.txt"
    } | LC_ALL=C sort | while IFS="$(printf '\t')" read -r jar role; do
      sum="$(sha256sum "$out/lib/$jar" | cut -d' ' -f1)"
      jq -n --arg name "$jar" --arg role "$role" --arg sha256 "$sum" '{name: $name, role: $role, sha256: $sha256}'
    done | jq -S -s \
      --arg version ${lib.escapeShellArg version} \
      --arg srcVersion ${lib.escapeShellArg srcVersion} \
      --arg sourceRev ${lib.escapeShellArg sourceRev} \
      '{version: $version, upstreamRelease: "7.161.26", sourceVersion: $srcVersion, sourceRev: $sourceRev,
        webappLib: "/opt/jfrog/artifactory/app/artifactory/tomcat/webapps/artifactory/WEB-INF/lib", jars: .}' \
      > "$out/manifest.json"

    # every jar in lib/ is in the manifest and vice versa
    diff <(ls "$out/lib" | LC_ALL=C sort) <(jq -r '.jars[].name' "$out/manifest.json")
  ''
