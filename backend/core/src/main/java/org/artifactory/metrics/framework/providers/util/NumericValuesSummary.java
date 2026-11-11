package org.artifactory.metrics.framework.providers.util;

import lombok.Data;

/**
 * A helper class that can use used to count items in a stream of numeric values,
 * compute the average and the maximal values.
 */
@Data
public class NumericValuesSummary {
    long sum;
    long max;
    long count;

    public double getAverage() {
        return (this.count == 0 ? 0.0 : (double)this.sum / this.count);
    }

    public void append(int value) {
        append((long)value);
    }

    public void append(long value) {
        this.count++;
        this.sum += value;
        this.max = Math.max(this.max, value);
    }
}
