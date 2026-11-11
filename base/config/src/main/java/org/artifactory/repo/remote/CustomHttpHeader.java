package org.artifactory.repo.remote;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import org.jfrog.common.config.diff.DiffKey;
import org.jfrog.common.config.diff.GenerateDiffFunction;
import org.jfrog.common.config.validation.NotUnicode;

import java.util.Locale;
import java.util.Objects;

import static org.jfrog.common.ArgUtils.requireNonBlank;


/**
 * Repo config model for a custom HTTP header configured on a remote repository.
 */
@Data
@GenerateDiffFunction
public class CustomHttpHeader {

    @DiffKey
    @Getter
    @NotUnicode
    private String name;
    @NotUnicode
    private String value;
    private boolean sensitive;

    @JsonCreator
    public CustomHttpHeader(
            @JsonProperty("name") String name,
            @JsonProperty("value") String value,
            @JsonProperty("sensitive") boolean sensitive) {
        this.name = Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
        requireNonBlank(name, "name must not be empty");
        this.value = value;
        this.sensitive = sensitive;
    }

    public CustomHttpHeader(CustomHttpHeader other) {
        this(Objects.requireNonNull(other, "other").name, other.value, other.sensitive);
    }

    public CustomHttpHeader copy() {
        return new CustomHttpHeader(this);
    }

    public void setName(String name) {
        this.name = Objects.requireNonNull(name, "name").trim().toLowerCase(Locale.ROOT);
        requireNonBlank(name, "name must not be empty");
    }

}
