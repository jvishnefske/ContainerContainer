package com.jfrog.build.model.api;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Request body for the versions-by-list search endpoint: the explicit build numbers to look up.
 */
@Data
@NoArgsConstructor
public class BuildNumbersSearchModel {

    private List<String> versions = new ArrayList<>();
}
