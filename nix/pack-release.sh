# Deterministic release packing, sourced by nix/release.nix.
#
# pack_release <stage-dir> <top-name> <out-dir>: writes <out-dir>/<top-name>.tar.gz with every entry under
# <top-name>/, sorted by name, mtime $SOURCE_DATE_EPOCH, owner and group 0, modes normalised (0644 files, 0755
# directories), gzip without name or timestamp; then <out-dir>/SHA256SUMS in `sha256sum` format.
pack_release() {
    local stage="$1" top="$2" out="$3"
    mkdir -p "$out"
    find "$stage" -type d -exec chmod 0755 {} +
    find "$stage" -type f -exec chmod 0644 {} +
    tar --create --format=gnu --sort=name \
        --mtime="@$SOURCE_DATE_EPOCH" \
        --owner=0 --group=0 --numeric-owner \
        --transform="s:^\.:$top:" \
        -C "$stage" . \
        | gzip -9 -n > "$out/$top.tar.gz"
    (cd "$out" && sha256sum "$top.tar.gz" > SHA256SUMS)
}
