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

package org.artifactory.util;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.file.InvalidPathException;

/**
 * Tests the behavior of {@link PathValidator}
 *
 * @author Shay Yaakov
 */
@Test
public class PathValidatorTest {

    @Test(expectedExceptions = InvalidPathException.class, dataProvider = "invalidData")
    public void testInvalidPathItem(String path) {
        PathValidator.validate(path);
    }

    @DataProvider
    public static Object[][] invalidData() {
        return new Object[][] {
                { "" }, // single slash
                { "&" },
                { "bbb/&" },
                { "." },
                { ".." },
                { "blabla/." },
                { "dot/../dot/file.jar" },
                { "       " },
                { "back\\slash.zors" },
                { "riddle/me_this?.tar" },
                { "make\"believe.hello" },
                { "sup|r|mario" },
                { "the/universe/has/lots/of/*" },
                { " shsh/gaga /hdt" }, // space before slash
                { "blabla/hello/      " } // space after slash
        };
    }

    @Test(dataProvider = "validData")
    public void validPathItem(String path) {
        PathValidator.validate(path);
    }

    @DataProvider
    public static Object[][] validData() {
        return new Object[][] {
                { "antlr/antlr/2.7.6/antlr-2.7.6.jar" },
                { "ca/juliusdavies/not-yet-commons-ssl/0.3.9/not-yet-commons-ssl-0.3.9.jar.sha1" },
                { "commons-codec/commons-codec/1.5/commons-codec-1.5.pom.md5" },
                { "/axis/axis-saaj/1.4/axis-saaj-1.4.pom" },
                { "cccc}dd{dd" },
                { "dsfsdf!cgxcbv$" },
                { "sdf/d dd$dd" },
                { "P@ssw0rd.h$sh" },
                { "sdfs9(vcx).jar" },
                { "xcv[sdf..sdf]" },
                { "vxvc^fvnb^..sdfsde&" },
                { "a=1&b=2" },
                { "copy   maven     project" },
                { "de/regnis/sequence/1.0.0/sequence-library-1.0.0.jar;tmp.sha1.tmp" },
                { "vv/aa..bb/&gg.pom" },
                { "v1.0...0" },
                { "jar-100%.jar" },
                { "gogo,baba.jar" },
                { "file    tab.jar" },
                { ".index" },
                { "..index" },
                { "blabla/.index" },
                { "blabla/..index" },
                { "blabla/index." },
                { "blabla/index./asdasd" },
                { "blabla/index../asdasd" },
                { "hibla<sdf" },
                { "path/to>file.jar" },
                { "漏洞.xls" } // check unicode, "vulnerability.xls"
        };
    }
}
