package org.artifactory.storage.db.aql.parser.elements.high.level.domain.iteminfo;

import com.google.common.collect.Lists;
import org.artifactory.aql.model.AqlDomainEnum;
import org.artifactory.storage.db.aql.parser.elements.ParserElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.DomainProviderElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.EmptyIncludeDomainElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.IncludeDomainElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.InternalNameElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.LazyParserElement;

import java.util.List;

import static org.artifactory.aql.model.AqlDomainEnum.itemInfos;
import static org.artifactory.aql.model.AqlDomainEnum.items;
import static org.artifactory.storage.db.aql.parser.AqlParser.dot;
import static org.artifactory.storage.db.aql.parser.AqlParser.itemDomains;

public class ItemInfosDomainElement extends LazyParserElement implements DomainProviderElement {
    @Override
    protected ParserElement init() {
        List<ParserElement> list = Lists.newArrayList();
        fillWithDomainFields(list);
        fillWithSubDomains(list);
        return fork(list.toArray(new ParserElement[list.size()]));
    }

    @Override
    public boolean isVisibleInResult() {
        return true;
    }

    private void fillWithDomainFields(List<ParserElement> list) {
        list.add(new IncludeDomainElement(itemInfos));
    }

    private void fillWithSubDomains(List<ParserElement> list) {
        list.add(forward(new InternalNameElement(items.signature),
                fork(new EmptyIncludeDomainElement(items), forward(dot, itemDomains))));
    }

    @Override
    public AqlDomainEnum getDomain() {
        return itemInfos;
    }
}
