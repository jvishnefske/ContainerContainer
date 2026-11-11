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

package org.artifactory.storage.db.aql.parser.elements.low.level;

import org.artifactory.storage.db.aql.parser.AqlParserContext;
import org.artifactory.storage.db.aql.parser.ParserElementResultContainer;
import org.artifactory.storage.db.aql.parser.elements.ParserElement;

import java.util.List;
import java.util.Optional;

/**
 * @author Gidi Shabat
 */
public abstract class LazyParserElement implements ParserElement {

    private ParserElement element;

    @Override
    public Optional<ParserElementResultContainer> peelOff(String queryRemainder, AqlParserContext context) {
        Optional<ParserElementResultContainer> possiblePath = element.peelOff(queryRemainder, context);
        possiblePath.ifPresent(this::addToPath);
        return possiblePath;
    }

    private void addToPath(ParserElementResultContainer path) {
        if (isVisibleInResult()) {
            path.add(this, path.getElement());
        }
    }

    @Override
    public void initialize() {
        if (element == null) {
            element = init();
            element.initialize();
        }
    }

    protected abstract ParserElement init();

    public boolean isVisibleInResult() {
        return false;
    }

    public ForkParserElement fork(ParserElement... elements) {
        return new ForkParserElement(elements);
    }

    public ParserElement forward(ParserElement... elements) {
        return new ForwardElement(elements);
    }

    public List<String> next() {
        return element.next();
    }
}
