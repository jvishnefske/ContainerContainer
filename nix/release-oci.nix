# packages.release-oci: the minimal image (packages.oci) as release assets, beside packages.release:
#   <name>-<tag>.oci.tar        OCI image archive with zstd layers (podman load, docker load, skopeo oci-archive:)
#   <name>-<tag>.oci.json       its sha256 and size in bytes, the image manifest digest, and per-layer sizes
#   SHA256SUMS                  `sha256sum` line for the tar
# The release workflow merges this SHA256SUMS with packages.release's into the one published SHA256SUMS.
{
  lib,
  runCommand,
  jq,
  oci,
}:
let
  file = oci.fileName;
in
runCommand "${lib.removeSuffix ".tar" file}-release"
  {
    nativeBuildInputs = [ jq ];
    meta.license = lib.licenses.agpl3Plus;
  }
  ''
    mkdir -p "$out"
    cp ${oci}/${file} "$out/${file}"
    cd "$out"
    sha256sum ${file} > SHA256SUMS
    manifest="$(tar -xOf ${file} index.json | jq -r '.manifests[0].digest')"
    jq -n -S \
      --arg file ${file} \
      --arg sha256 "$(cut -d' ' -f1 SHA256SUMS)" \
      --argjson size "$(stat -c %s ${file})" \
      --arg manifest "$manifest" \
      --arg image ${oci.imageName}:${oci.imageTag} \
      --rawfile layers ${oci}/layers.tsv \
      '{file: $file, sha256: $sha256, size: $size, image: $image, manifestDigest: $manifest,
        layers: ($layers | split("\n") | .[1:] | map(select(length > 0) | split("\t")
                 | {index: (.[0] | tonumber), uncompressed: (.[1] | tonumber), zstd: (.[2] | tonumber),
                    digest: .[3], content: .[4]}))}' \
      > ${lib.removeSuffix ".tar" file}.json
  ''
