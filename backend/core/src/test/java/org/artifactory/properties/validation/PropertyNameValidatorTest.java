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

package org.artifactory.properties.validation;

import org.artifactory.exception.ValidationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Asaf Zalcman
 */
class PropertyNameValidatorTest {

    private static final String EXPECTED_EXCEPTION_MESSAGE =
            "Name must begin with a letter, cannot contain a whitespace, and cannot contain the following " +
                    "special characters: )(}{][*+^$\\/~`!@#%&<>;=,±§.";

    @ParameterizedTest
    @ValueSource(strings = {"x.yz", "x-x3df", "latest:build", "ff_gh"})
    void checkValidationWithValidName(String propName) throws ValidationException {
        PropertyNameValidator.validate(propName);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"x yz", "3ss", "sd!", "w@", "fds#", "fds$fr", "df^yy", "fg%gbfd", "fd&jj",
            "kk*kk", " ", "fs/f", "das\\gfd", "ds~sf"
            , "ss+s", "sd>f", "ww>w", "oo=o", "hh;h", "fgg,g", "vv±f", "aa§a",
            "das`fds", "fds[", "fd]fds", "qw{d", "tr}fd", "fds(f", "as)fd", ""})
    void checkValidationWithInValidName(String badPropName) {
        Throwable validationException =
                assertThrows(ValidationException.class, () -> PropertyNameValidator.validate(badPropName));
        assertEquals(EXPECTED_EXCEPTION_MESSAGE,validationException.getMessage());
    }
}
