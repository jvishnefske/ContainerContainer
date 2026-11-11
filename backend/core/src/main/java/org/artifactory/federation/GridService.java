package org.artifactory.federation;

import java.util.Optional;

/**
 * SPI for reading grid membership and its derived values. Used by grid-aware
 * federated repository create/update flows to gate on membership before
 * calling local Grid for approval.
 * <p>
 * The Pro / JFConfig-backed implementation is wired separately.
 *
 * @author Tomer Ben Kereth
 */
public interface GridService {

    /**
     * Convenience gate for grid-aware flows. Returns {@code false} when the
     * JFConfig membership entry is absent, has an empty role, or has a role
     * other than {@code PRIMARY} / {@code MEMBER}.
     */
    boolean isPartOfGrid();

    /**
     * Topology key from {@code grid.topology.membership}. Used as path parameter
     * in Grid API URLs (e.g. {@code POST .../topology/{key}/repos/approve-creation}).
     * Empty when this JPD is not a grid member.
     */
    Optional<String> getTopologyKey();

    /**
     * This JPD's identifier within the grid topology. Passed as {@code jpd_id} in
     * Grid approve-creation requests. Empty when this JPD is not a grid member.
     */
    Optional<String> getJpdId();

    /**
     * URL of the main Grid site that local Grid forwards to. Empty when this
     * JPD is not a grid member.
     */
    Optional<String> getGridMainSiteUrl();
}
