package org.artifactory.repo.remote;

import org.jfrog.common.config.diff.GenerateDiffFunction;

/**
 * @author Tamar Rosen
 */
@GenerateDiffFunction
public class SourceContent {

    public SourceContent() {
        super();
    }

    public SourceContent(SourceContent sourceContent) {
        this.originAbsenceDetection = sourceContent.isOriginAbsenceDetection();
    }

    private boolean originAbsenceDetection = false;

    /**
     * Checks whether remote content absence check is enabled
     *
     * @return boolean
     */
    public boolean isOriginAbsenceDetection() {
        return originAbsenceDetection;
    }

    /**
     * Sets whether remote content absence check is enabled
     */
    public void setOriginAbsenceDetection(boolean originAbsenceDetection) {
        this.originAbsenceDetection = originAbsenceDetection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SourceContent that = (SourceContent) o;

        return originAbsenceDetection == that.originAbsenceDetection;
    }

    @Override
    public int hashCode() {
        return (originAbsenceDetection ? 1 : 0);
    }
}
