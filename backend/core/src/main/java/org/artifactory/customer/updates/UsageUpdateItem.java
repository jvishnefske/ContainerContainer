package org.artifactory.customer.updates;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author Omri Ziv
 */
@Data
@NoArgsConstructor
public class UsageUpdateItem {
    double percentage;
    long used;
    long limit;
    String units;
    String status;
}
