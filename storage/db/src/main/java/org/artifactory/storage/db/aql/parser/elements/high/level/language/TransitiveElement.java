package org.artifactory.storage.db.aql.parser.elements.high.level.language;

import org.artifactory.storage.db.aql.parser.elements.ParserElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.InternalNameElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.LazyParserElement;

import static org.artifactory.storage.db.aql.parser.AqlParser.closeBrackets;
import static org.artifactory.storage.db.aql.parser.AqlParser.openBrackets;

/**
 * @author Tomer Slutsky
 */
public class TransitiveElement extends LazyParserElement {
    @Override
    protected ParserElement init() {
        return forward(new InternalNameElement("transitive"), openBrackets, closeBrackets);
    }

    @Override
    public boolean isVisibleInResult() {
        return true;
    }
}
