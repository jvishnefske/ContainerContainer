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

import com.google.common.collect.Lists;
import org.artifactory.storage.db.aql.parser.AqlParserContext;
import org.artifactory.storage.db.aql.parser.ParserElementResultContainer;
import org.artifactory.storage.db.aql.parser.elements.ParserElement;
import org.jfrog.security.util.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Represents an ordered list of sub elements that must appear in the query string in the same order.
 *
 * @author Gidi Shabat
 */
public class ForwardElement implements ParserElement {
    private List<ParserElement> elements = Lists.newArrayList();

    public ForwardElement(ParserElement... elements) {
        Collections.addAll(this.elements, elements);
    }

    @Override
    public void initialize() {
        for (ParserElement element : elements) {
            element.initialize();
        }
    }

    @Override
    public List<String> next() {
        List<String> list = Lists.newArrayList();
        for (ParserElement element : elements) {
            List<String> next = element.next();
            if (next.size() > 0) {
                list.addAll(next);
                return list;
            }
        }
        return list;
    }

    @Override
    public Optional<ParserElementResultContainer> peelOff(String queryRemainder, AqlParserContext context) {
        List<ParserElement> tempElements = Lists.newArrayList(elements);
        return peelOffRecursive(tempElements, new ParserElementResultContainer(
                queryRemainder, ""), context);
    }

    private Optional<ParserElementResultContainer> peelOffRecursive(List<ParserElement> elements,
            ParserElementResultContainer query,
            AqlParserContext context) {
        ParserElement element = elements.remove(0);
        Optional<ParserElementResultContainer> result = element.peelOff(query.getQueryRemainder(), context);
        if (result.isEmpty() || elements.isEmpty()) {
            return result;
        }

        ArrayList<ParserElement> tempElements = Lists.newArrayList(elements);
        Optional<ParserElementResultContainer> internalResult =
                peelOffRecursive(tempElements, result.get(), context);
        internalResult.ifPresent(inner -> this.concatResults(inner, result.get()));
        return internalResult;

    }

    private void concatResults(ParserElementResultContainer internalResult,
            ParserElementResultContainer previousResult) {
        for (Pair<ParserElement, String> pair : previousResult.getAll()) {
            ParserElement parserElement = pair.getFirst();
            String value = pair.getSecond();
            internalResult.add(parserElement, value);
        }
    }

}
