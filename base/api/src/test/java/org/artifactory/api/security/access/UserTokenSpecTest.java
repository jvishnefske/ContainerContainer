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

package org.artifactory.api.security.access;

import org.jfrog.access.common.ServiceId;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.testng.Assert.assertEquals;

/**
 * Created by Yinon Avraham.
 */
public class UserTokenSpecTest {

    @Test
    public void testCreate() throws Exception {
        UserTokenSpec tokenSpec = UserTokenSpec.create("theuser")
                .scope(singletonList("scope"))
                .audience(singletonList("audience"))
                .expiresIn(300L)
                .refreshable(true);
        assertEquals(tokenSpec.getUsername(), "theuser");
        assertEquals(tokenSpec.getScope(), singletonList("scope"));
        assertEquals(tokenSpec.getAudience(), singletonList("audience"));
        assertEquals(tokenSpec.getExpiresIn(), Long.valueOf(300L));
        assertEquals(tokenSpec.getRefreshable(), Boolean.TRUE);
        assertEquals(tokenSpec.isRefreshable(), true);
        ServiceId serviceId = new ServiceId("type", "123");
        assertEquals(tokenSpec.createSubject(serviceId).getFormattedName(), serviceId + "/users/theuser");
    }

    @Test
    public void testCreateMaxLength() {
        UserTokenSpec tokenSpec =
                UserTokenSpec.create("token:rnrdlnvazlzajpkpfqljitzfbtvvzepppupkhrugxjjthxxyeokfl12345");
        assertEquals(tokenSpec.getUsername(),
                "token:rnrdlnvazlzajpkpfqljitzfbtvvzepppupkhrugxjjthxxyeokfl12345");
        ServiceId serviceId = new ServiceId("type", "123");
        assertEquals(tokenSpec.createSubject(serviceId).getFormattedName(),
                serviceId + "/users/token:rnrdlnvazlzajpkpfqljitzfbtvvzepppupkhrugxjjthxxyeokfl12345");
    }

    @Test
    public void testCreateWithDefaults() throws Exception {
        UserTokenSpec tokenSpec = UserTokenSpec.create(null);
        assertEquals(tokenSpec.getUsername(), null);
        assertEquals(tokenSpec.getScope(), emptyList());
        assertEquals(tokenSpec.getAudience(), emptyList());
        assertEquals(tokenSpec.getExpiresIn(), null);
        assertEquals(tokenSpec.getRefreshable(), null);
        assertEquals(tokenSpec.isRefreshable(), false);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, dataProvider = "provideIllegalUsernames")
    public void failCreateSubjectWithIllegalUsername(String illegalUsername) throws Exception {
        UserTokenSpec tokenSpec = UserTokenSpec.create(illegalUsername);
        tokenSpec.createSubject(new ServiceId("type", "123"));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = "URL length exceeds " +
            "maximum length of 249 characters")
    public void failCreateSubjectWithIllegalUsernameAsUrl() throws Exception {
        UserTokenSpec tokenSpec = UserTokenSpec.create(String.valueOf(provideIllegalUsernames()[3][0]));
        tokenSpec.createSubject(new ServiceId("type", "123"));
    }

    @DataProvider
    public static Object[][] provideIllegalUsernames() {
        return new Object[][] {
                {null},
                {""},
                {"NoY6UQKOwYoEcIj0zoZ2ezTsbKCqWCRmc7QJWvn0XHQTxVw0C1CItYSLSzNn" +
                        "p1aeEwhbW5RyYPyY7JlV2CIeECVTaNS6cSaimXJ7NKupuwlksUTpzX4yl7Ez" +
                        "gbQKpsmkEmNN91ZZfZnynrkreENoDgG8qhYn7SOofZ1Clx9APVygfQi5UhaL" +
                        "QuVDm45VbR9vMSbtVIvw3Zg54YCtOv1COggLwD7INoc5pVCCzq3uOWo71tOr" +
                        "bkSKwYQvlL"}, // 250 characters (see: RTFACT-27164)
                {"http://NoY6UQKOwYoEcIj0zoZ2ezTsbKCqWCRmc7QJWvn0XHQTxVw0C1CItYSLSzNn" +
                        "p1aeEwhbW5RyYPyY7JlV2CIeECVTaNS6cSaimXJ7NKupuwlksUTpzX4yl7Ez" +
                        "gbQKpsmkEmNN91ZZfZnynrkreENoDgG8qhYn7SOofZ1Clx9APVygfQi5UhaL" +
                        "QuVDm45VbR9vMSbtVIvw3Zg54YCtOv1COggLwD7INoc5pVCCzq3uOWo71tOr" +
                        "bkSKwY.aaaa"}, // >250 characters + contains URL marker
                {"token:NoY6UQKOwYoEcIj0zoZ2ezTsbKCqWCRmc7QJWvn0XHQTxVw0C1CItYSLSzNn" +
                        "p1aeEwhbW5RyYPyY7JlV2CIeECVTaNS6cSaimXJ7NKupuwlksUTpzX4yl7Ez" +
                        "gbQKpsmkEmNN91ZZfZnynrkreENoDgG8qhYn7SOofZ1Clx9APVygfQi5UhaL" +
                        "QuVDm45VbR9vMSbtVIvw3Zg54YCtOv1COggLwD7INoc5pVCCzq3uOWo71tOr" +
                        "bkSKwYQvlL"}
        };
    }

    @Test(dataProvider = "provideIsUserToken")
    public void testIsUserToken(String subject, boolean expected) throws Exception {
        TokenInfo tokenInfo = createTokenInfo(subject);
        assertEquals(UserTokenSpec.isUserToken(tokenInfo), expected);
    }

    @DataProvider
    public static Object[][] provideIsUserToken() {
        return new Object[][] {
                {"", false},
                {null, false},
                {"service@id", false},
                {"service@id/users", false},
                {"service@id/nodes/node-id", false},
                {"service@id/users/the-user/foo", false},
                {"service@id/users/the-user", true}
        };
    }

    private static TokenInfo createTokenInfo(String subject) {
        TokenInfo tokenInfo = createMock(TokenInfo.class);
        expect(tokenInfo.getSubject()).andReturn(subject).anyTimes();
        replay(tokenInfo);
        return tokenInfo;
    }
}