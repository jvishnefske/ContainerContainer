package org.artifactory.storage.db.aql.parser.elements.high.level.language;

import org.artifactory.storage.db.aql.parser.elements.ParserElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.InternalBooleanValueElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.LazyParserElement;

public class DistinctValueElement extends LazyParserElement {

    @Override
    protected ParserElement init() {
        return forward(new InternalBooleanValueElement());
    }

    @Override
    public boolean isVisibleInResult() {
        return true;
    }
}
