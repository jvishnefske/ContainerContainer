# packages.release: containercontainer-<version>-jars.tar.gz plus SHA256SUMS.
#
# The tarball holds the jars and manifest from packages.jars, LICENSE, the WASM ABI schema and a NOTICE naming the
# source revision and the AGPL source offer. Packing is deterministic (nix/pack-release.sh); SOURCE_DATE_EPOCH is
# the commit time of the flake source. passthru.deterministic packs twice and requires identical bytes and valid sums.
{
  lib,
  runCommand,
  jars,
  version,
  sourceRev,
  sourceDateEpoch,
  license,
  abiSchema,
}:
let
  top = "containercontainer-${version}-jars";
  repo = "https://github.com/jvishnefske/ContainerContainer";
  notice = ''
    ContainerContainer ${version}
    A fork of JFrog Artifactory OSS 7.161.26, not affiliated with or endorsed by JFrog.

    Built from source revision ${sourceRev} of ${repo}
    (release tag v${version}) with the Nix flake in that repository.

    The software is licensed under the GNU Affero General Public License v3 or later; see LICENSE.
    Source offer: the complete corresponding source of these binaries is the repository above at
    tag v${version} (revision ${sourceRev}), available at no charge from ${repo}/tree/v${version}.
  '';
  stage = ''
    stage="$(mktemp -d)"
    cp -r ${jars}/lib "$stage/lib"
    cp ${jars}/manifest.json ${jars}/replace.txt ${jars}/add.txt ${jars}/stock.txt ${jars}/expected.txt "$stage/"
    cp ${license} "$stage/LICENSE"
    mkdir -p "$stage/plugins/wasm-abi"
    cp ${abiSchema} "$stage/plugins/wasm-abi/abi-v1.schema.json"
    printf '%s' ${lib.escapeShellArg notice} > "$stage/NOTICE"
  '';
  env = {
    SOURCE_DATE_EPOCH = toString sourceDateEpoch;
  };
  release =
    runCommand "${top}"
      (
        env
        // {
          passthru = { inherit top deterministic; };
          meta.license = lib.licenses.agpl3Plus;
        }
      )
      ''
        source ${./pack-release.sh}
        ${stage}
        pack_release "$stage" ${top} "$out"
      '';
  deterministic = runCommand "${top}-deterministic" env ''
    source ${./pack-release.sh}
    ${stage}
    cp -r "$stage" "$TMPDIR/stage2"
    touch -d @0 "$TMPDIR/stage2/NOTICE"   # different input mtimes must not change the output
    pack_release "$stage" ${top} a
    pack_release "$TMPDIR/stage2" ${top} b
    cmp a/${top}.tar.gz b/${top}.tar.gz
    cmp a/${top}.tar.gz ${release}/${top}.tar.gz
    (cd ${release} && sha256sum -c SHA256SUMS)
    tar -tzvf ${release}/${top}.tar.gz | awk '$2 != "0/0" { print "bad owner: " $0; bad = 1 } END { exit bad }'
    mkdir -p "$out" && cp ${release}/SHA256SUMS "$out/"
  '';
in
release
