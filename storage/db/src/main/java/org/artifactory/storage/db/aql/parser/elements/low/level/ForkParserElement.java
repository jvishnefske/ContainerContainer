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
import org.apache.commons.lang3.StringUtils;
import org.artifactory.storage.db.aql.parser.AqlParserContext;
import org.artifactory.storage.db.aql.parser.ParserElementResultContainer;
import org.artifactory.storage.db.aql.parser.elements.ParserElement;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Gidi Shabat
 */
public class ForkParserElement implements ParserElement {

    private final List<ParserElement> elements = Lists.newArrayList();

    public ForkParserElement(ParserElement... elements) {
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
            }
        }
        return list;
    }

    @Override
    public Optional<ParserElementResultContainer> peelOff(String queryRemainder, AqlParserContext context) {
        Optional<ParserElementResultContainer> finalResult = Optional.empty();
        for (ParserElement element : elements) {
            Optional<ParserElementResultContainer> parserResult = element.peelOff(queryRemainder, context);

            if (parserResult.isPresent()) {
                Optional<ParserElementResultContainer> minResult = finalResult;
                finalResult = this.handleRecursionEnd(parserResult.get())
                        .or(() -> this.handleRecursionPartEnd(minResult, parserResult.get()));
            }
        }

        return finalResult;
    }

    private Optional<ParserElementResultContainer> handleRecursionPartEnd(
            Optional<ParserElementResultContainer> minResult, ParserElementResultContainer parserResult) {
        if (minResult.isEmpty() ||
                minResult.get().getQueryRemainder().length() > parserResult.getQueryRemainder().length()) {
            minResult = Optional.of(parserResult);
        }

        return minResult;
    }

    private Optional<ParserElementResultContainer> handleRecursionEnd(ParserElementResultContainer parserResult) {
        return StringUtils.isBlank(parserResult.getQueryRemainder()) ? Optional.of(parserResult) : Optional.empty();
    }
}
