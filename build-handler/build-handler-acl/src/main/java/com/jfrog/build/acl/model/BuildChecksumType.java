package com.jfrog.build.acl.model;

import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;

import static java.util.Objects.nonNull;

@Getter
public enum BuildChecksumType {

    SHA1("SHA-1", ".sha1", 40),
    MD5("MD5", ".md5", 32),
    SHA256("SHA-256", ".sha256", 64);

    private final String alg;
    private final String ext;

    /**
     * Length of the hexadecimal string representation of the checksum.
     */
    private final int length;

    BuildChecksumType(String alg, String ext, int length) {
        this.alg = alg;
        this.ext = ext;
        this.length = length;
    }

    public boolean isValid(@Nullable String checksum) {
        return nonNull(checksum)
                && checksum.length() == length
                && checksum.chars().allMatch(this::isValidHexadecimalCharacter);
    }

    private boolean isValidHexadecimalCharacter(int c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
    }

    /**
     * Always use lowercase for DB column names - some databases are sensitive to it.
     * (e.g. MSSQL differentiates between 'SHA256' and 'sha256')
     */
    @NonNull
    public String toColumnName() {
        return name().toLowerCase();
    }
}
