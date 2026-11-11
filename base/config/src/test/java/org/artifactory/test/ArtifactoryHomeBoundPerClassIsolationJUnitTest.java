/*
 *
 * Artifactory is a binaries repository manager.
 * Copyright (C) 2018 JFrog Ltd.
 *
 * Artifactory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * Artifactory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Artifactory.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.artifactory.test;

import org.artifactory.common.home.ArtifactoryHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a single static {@link ArtifactoryHomeStub} shared across subclasses caused parallel Surefire flakes.
 */
class ArtifactoryHomeBoundPerClassIsolationJUnitTest {

    @AfterEach
    void tearDown() {
        ArtifactoryHome.unbind();
        ArtifactoryHomeBoundJUnitTest.clearHomeEnvironmentFor(FirstBinder.class);
        ArtifactoryHomeBoundJUnitTest.clearHomeEnvironmentFor(SecondBinder.class);
    }

    @Test
    void eachConcreteTestClassGetsOwnArtifactoryHomeStubPath() {
        FirstBinder first = new FirstBinder();
        first.bindArtifactoryHome();
        String firstPath = first.getBound().getHomeDir().getAbsolutePath();
        assertTrue(firstPath.contains(FirstBinder.class.getCanonicalName()),
                () -> "unexpected first home path: " + firstPath);
        first.unbindArtifactoryHome();

        SecondBinder second = new SecondBinder();
        second.bindArtifactoryHome();
        String secondPath = second.getBound().getHomeDir().getAbsolutePath();
        second.unbindArtifactoryHome();

        assertTrue(secondPath.contains(SecondBinder.class.getCanonicalName()),
                () -> "second concrete test class should own its home path but got: " + secondPath);
    }

    static final class FirstBinder extends ArtifactoryHomeBoundJUnitTest {
    }

    static final class SecondBinder extends ArtifactoryHomeBoundJUnitTest {
    }
}
