package org.artifactory.storage.db.aql.parser.elements.high.level.domain.iteminfo;

import com.google.common.collect.Lists;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.storage.db.aql.parser.AqlParser;
import org.artifactory.storage.db.aql.parser.elements.ParserElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.DomainProviderElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.InternalNameElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.LazyParserElement;

import java.util.List;

import static org.artifactory.aql.model.AqlDomainEnum.itemInfos;
import static org.artifactory.aql.model.AqlDomainEnum.items;
import static org.artifactory.storage.db.aql.parser.AqlParser.dot;
import static org.artifactory.storage.db.aql.parser.AqlParser.itemStar;

public class ItemInfosStarElement extends LazyParserElement implements DomainProviderElement {
    @Override
    protected ParserElement init() {
        List<ParserElement> list = Lists.newArrayList();
        fillWithSubDomains(list);
        fillWithDomainFields(list);
        return fork(list.toArray(new ParserElement[list.size()]));
    }

    @Override
    public boolean isVisibleInResult() {
        return true;
    }

    private void fillWithDomainFields(List<ParserElement> list) {
        list.add(AqlParser.star);
    }

    private void fillWithSubDomains(List<ParserElement> list) {
        list.add(forward(new InternalNameElement(items.signature), dot, itemStar));
    }

    @Override
    public AqlDomainEnum getDomain() {
        return itemInfos;
    }
}