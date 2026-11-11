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

package org.artifactory.storage.db.aql.parser;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.aql.AqlParserException;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.action.ActionPropertyKeysElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.action.ActionValueElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.action.DryRunElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.BuildDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.BuildLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.BuildPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.BuildStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.BuildValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.artifact.BuildArtifactDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.artifact.BuildArtifactLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.artifact.BuildArtifactPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.artifact.BuildArtifactStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.artifact.BuildArtifactValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.dependency.BuildDependenciesDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.dependency.BuildDependenciesLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.dependency.BuildDependenciesPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.dependency.BuildDependenciesStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.dependency.BuildDependenciesValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModuleDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModuleLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModulePhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModulePropertyDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModulePropertyLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModulePropertyPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModulePropertyStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModulePropertyValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModuleStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.module.BuildModuleValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.promotion.BuildPromotionsDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.promotion.BuildPromotionsLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.promotion.BuildPromotionsPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.promotion.BuildPromotionsStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.promotion.BuildPromotionsValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.property.BuildPropertyDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.property.BuildPropertyLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.property.BuildPropertyPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.property.BuildPropertyStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.build.property.BuildPropertyValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.item.ItemDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.item.ItemLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.item.ItemPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.item.ItemStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.item.ItemValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.iteminfo.ItemInfosDomainElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.iteminfo.ItemInfosLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.iteminfo.ItemInfosPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.iteminfo.ItemInfosStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.iteminfo.ItemInfosValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.property.PropertyDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.property.PropertyLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.property.PropertyPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.property.PropertyStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.property.PropertyValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundle.ReleaseBundleDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundle.ReleaseBundleLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundle.ReleaseBundlePhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundle.ReleaseBundleStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundle.ReleaseBundleValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundlefile.ReleaseBundleFileDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundlefile.ReleaseBundleFileLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundlefile.ReleaseBundleFilePhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundlefile.ReleaseBundleFileStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.releasebundlefile.ReleaseBundleFileValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.statistics.StatisticsDomainsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.statistics.StatisticsLogicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.statistics.StatisticsPhysicalFieldsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.statistics.StatisticsStarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.domain.statistics.StatisticsValuesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.BooleanValueElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.CloseBracketsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.CloseCurlyBracketsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.CloseParenthesisElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.ColonElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.CommaElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.ComparatorElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.DistinctElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.DistinctValueElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.DotElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.LimitElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.LimitValueElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.NullElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.NumberElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.OffsetElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.OffsetValueElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.OpenBracketsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.OpenCurlyBracketsElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.OpenParenthesisElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.QuotedComparator;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.QuotedRelativeDateComparator;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.QuotesElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.RelativeDateComparatorElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.RootElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.SortTypeElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.StarElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.TransitiveElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.ValueElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.ValueNumberNullElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.action.ActionNewValueElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.action.ActionPropertyKeysTailElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.action.ActionPropertyKeysValueElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.action.DeleteActionElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.action.DryRunValueElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.action.FindActionElement;
import org.artifactory.storage.db.aql.parser.elements.high.level.language.action.UpdateActionElement;
import org.artifactory.storage.db.aql.parser.elements.low.level.InternalEmptyElement;

/**
 * Parse the Aql string query into parser result which is a list of parser elements
 * The parser does not have state therefore a single instance can be used on single JVM
 *
 * @author Gidi Shabat
 */
public class AqlParser {
    public static final String[] DELIMITERS =
            {"<=", ">=", "!=", " ", "<", ">", "(", ")", "[", "]", "{", "}", "=", "'", ".", ":", "\"", ":"};
    public static final String[] USED_KEYS = {"$mt", "$lt", "$eq", "and", "not", "or", "artifacts"};

    // Basic elements
    public static final DotElement dot = new DotElement();
    public static final CommaElement comma = new CommaElement();
    public static final StarElement star = new StarElement();
    public static final QuotedComparator quotedComparator = new QuotedComparator();
    public static final QuotedRelativeDateComparator quotedRelativeDateComparator = new QuotedRelativeDateComparator();
    public static final ComparatorElement comparator = new ComparatorElement();
    public static final RelativeDateComparatorElement relativeDateComparator = new RelativeDateComparatorElement();
    public static final QuotesElement quotes = new QuotesElement();
    public static final ColonElement colon = new ColonElement();
    public static final ValueNumberNullElement valueOrNumberOrNull = new ValueNumberNullElement();
    public static final ValueElement value = new ValueElement();
    public static final NumberElement number = new NumberElement();
    public static final NullElement nullElement = new NullElement();
    public static final OpenParenthesisElement openParenthesis = new OpenParenthesisElement();
    public static final CloseParenthesisElement closeParenthesis = new CloseParenthesisElement();
    public static final OpenCurlyBracketsElement openCurlyBrackets = new OpenCurlyBracketsElement();
    public static final CloseCurlyBracketsElement closedCurlyBrackets = new CloseCurlyBracketsElement();
    public static final OpenBracketsElement openBrackets = new OpenBracketsElement();
    public static final CloseBracketsElement closeBrackets = new CloseBracketsElement();
    public static final InternalEmptyElement empty = new InternalEmptyElement();
    public static final SortTypeElement sortType = new SortTypeElement();
    public static final LimitValueElement limitValue = new LimitValueElement();
    public static final OffsetValueElement offsetValue = new OffsetValueElement();
    public static final DistinctValueElement distinctValue = new DistinctValueElement();
    public static final BooleanValueElement booleanValue = new BooleanValueElement();
    public static final DryRunValueElement dryRunValue = new DryRunValueElement();
    public static final ActionNewValueElement actionNewValue = new ActionNewValueElement();
    public static final ActionPropertyKeysValueElement actionPropertyKeysValue = new ActionPropertyKeysValueElement();
    public static final ActionPropertyKeysTailElement actionPropertyKeysTrail = new ActionPropertyKeysTailElement();

    // Domain dynamic elements
    public static final BuildPromotionsDomainsElement buildPromotionsDomains = new BuildPromotionsDomainsElement();
    public static final StatisticsDomainsElement statisticsDomains = new StatisticsDomainsElement();
    public static final BuildDomainsElement buildDomains = new BuildDomainsElement();
    public static final BuildPropertyDomainsElement buildPropertiesDomains = new BuildPropertyDomainsElement();
    public static final BuildModulePropertyDomainsElement buildModulePropertiesDomains =
            new BuildModulePropertyDomainsElement();
    public static final BuildModuleDomainsElement buildModuleDomains = new BuildModuleDomainsElement();
    public static final BuildDependenciesDomainsElement buildDependenciesDomains =
            new BuildDependenciesDomainsElement();
    public static final BuildArtifactDomainsElement buildArtifactDomains = new BuildArtifactDomainsElement();
    public static final PropertyDomainsElement propertiesDomains = new PropertyDomainsElement();
    public static final ItemInfosDomainElement itemsInfosDomains = new ItemInfosDomainElement();
    public static final ItemDomainsElement itemDomains = new ItemDomainsElement();
    public static final ReleaseBundleDomainsElement releaseBundleDomains = new ReleaseBundleDomainsElement();
    public static final ReleaseBundleFileDomainsElement releaseBundleFileDomains =
            new ReleaseBundleFileDomainsElement();

    // Star dynamic elements
    public static final BuildPromotionsStarElement buildPromotionsStar = new BuildPromotionsStarElement();
    public static final StatisticsStarElement statisticsStar = new StatisticsStarElement();
    public static final BuildStarElement buildStar = new BuildStarElement();
    public static final BuildPropertyStarElement buildPropertiesStar = new BuildPropertyStarElement();
    public static final BuildModulePropertyStarElement buildModulePropertiesStar = new BuildModulePropertyStarElement();
    public static final BuildModuleStarElement buildModuleStar = new BuildModuleStarElement();
    public static final BuildDependenciesStarElement buildDependenciesStar = new BuildDependenciesStarElement();
    public static final BuildArtifactStarElement buildArtifactStar = new BuildArtifactStarElement();
    public static final PropertyStarElement propertiesStar = new PropertyStarElement();
    public static final ItemInfosStarElement itemInfosStar = new ItemInfosStarElement();
    public static final ItemStarElement itemStar = new ItemStarElement();
    public static final ReleaseBundleStarElement releaseBundlesStar = new ReleaseBundleStarElement();
    public static final ReleaseBundleFileStarElement releaseBundleFilesStar = new ReleaseBundleFileStarElement();

    // Value dynamic elements
    public static final BuildPromotionsValuesElement buildPromotionsValues = new BuildPromotionsValuesElement();
    public static final StatisticsValuesElement statisticsValues = new StatisticsValuesElement();
    public static final BuildValuesElement buildValues = new BuildValuesElement();
    public static final BuildPropertyValuesElement buildPropertiesValues = new BuildPropertyValuesElement();
    public static final BuildModulePropertyValuesElement buildModulePropertiesValues =
            new BuildModulePropertyValuesElement();
    public static final BuildModuleValuesElement buildModuleValues = new BuildModuleValuesElement();
    public static final BuildDependenciesValuesElement buildDependenciesValues = new BuildDependenciesValuesElement();
    public static final BuildArtifactValuesElement buildArtifactValues = new BuildArtifactValuesElement();
    public static final PropertyValuesElement propertiesValues = new PropertyValuesElement();
    public static final ItemInfosValuesElement itemInfosValues = new ItemInfosValuesElement();
    public static final ItemValuesElement itemValues = new ItemValuesElement();
    public static final ReleaseBundleValuesElement releaseBundlesValues = new ReleaseBundleValuesElement();
    public static final ReleaseBundleFileValuesElement releaseBundleFilesValues = new ReleaseBundleFileValuesElement();

    // Physical fields elements
    public static final BuildPromotionsPhysicalFieldsElement buildPromotionsPhysicalFields =
            new BuildPromotionsPhysicalFieldsElement();
    public static final StatisticsPhysicalFieldsElement statisticsPhysicalFields =
            new StatisticsPhysicalFieldsElement();
    public static final BuildPhysicalFieldsElement buildPhysicalFields = new BuildPhysicalFieldsElement();
    public static final BuildPropertyPhysicalFieldsElement buildPropertiesPhysicalFields =
            new BuildPropertyPhysicalFieldsElement();
    public static final BuildModulePropertyPhysicalFieldsElement buildModulePropertiesPhysicalFields =
            new BuildModulePropertyPhysicalFieldsElement();
    public static final BuildModulePhysicalFieldsElement buildModulePhysicalFields =
            new BuildModulePhysicalFieldsElement();
    public static final BuildDependenciesPhysicalFieldsElement buildDependenciesPhysicalFields =
            new BuildDependenciesPhysicalFieldsElement();
    public static final BuildArtifactPhysicalFieldsElement buildArtifactPhysicalFields =
            new BuildArtifactPhysicalFieldsElement();
    public static final ItemInfosPhysicalFieldsElement itemInfosPhysicalFields = new ItemInfosPhysicalFieldsElement();
    public static final PropertyPhysicalFieldsElement propertiesPhysicalFields = new PropertyPhysicalFieldsElement();
    public static final ItemPhysicalFieldsElement itemPhysicalFields = new ItemPhysicalFieldsElement();
    public static final ReleaseBundlePhysicalFieldsElement releaseBundlesPhysicalFields =
            new ReleaseBundlePhysicalFieldsElement();
    public static final ReleaseBundleFilePhysicalFieldsElement releaseBundleFilesPhysicalFields =
            new ReleaseBundleFilePhysicalFieldsElement();

    // Logical fields elements
    public static final BuildPromotionsLogicalFieldsElement buildPromotionsLogicalFields =
            new BuildPromotionsLogicalFieldsElement();
    public static final StatisticsLogicalFieldsElement statisticsLogicalFields = new StatisticsLogicalFieldsElement();
    public static final BuildLogicalFieldsElement buildLogicalFields = new BuildLogicalFieldsElement();
    public static final BuildPropertyLogicalFieldsElement buildPropertiesLogicalFields =
            new BuildPropertyLogicalFieldsElement();
    public static final BuildModulePropertyLogicalFieldsElement buildModulePropertiesLogicalFields =
            new BuildModulePropertyLogicalFieldsElement();
    public static final BuildModuleLogicalFieldsElement buildModuleLogicalFields =
            new BuildModuleLogicalFieldsElement();
    public static final BuildDependenciesLogicalFieldsElement buildDependenciesLogicalFields =
            new BuildDependenciesLogicalFieldsElement();
    public static final BuildArtifactLogicalFieldsElement buildArtifactLogicalFields =
            new BuildArtifactLogicalFieldsElement();
    public static final ItemInfosLogicalFieldsElement itemInfosLogicalFields = new ItemInfosLogicalFieldsElement();
    public static final PropertyLogicalFieldsElement propertiesLogicalFields = new PropertyLogicalFieldsElement();
    public static final ItemLogicalFieldsElement itemLogicalFields = new ItemLogicalFieldsElement();
    public static final ReleaseBundleLogicalFieldsElement releaseBundlesLogicalFields =
            new ReleaseBundleLogicalFieldsElement();
    public static final ReleaseBundleFileLogicalFieldsElement releaseBundleFilesLogicalFields =
            new ReleaseBundleFileLogicalFieldsElement();

    public static final OffsetElement offset = new OffsetElement();
    public static final TransitiveElement transitive = new TransitiveElement();
    public static final LimitElement limit = new LimitElement();
    public static final DistinctElement distinct = new DistinctElement();
    // Root element
    public static final RootElement root = new RootElement();

    public static final FindActionElement findAction = new FindActionElement();
    public static final DeleteActionElement deleteAction = new DeleteActionElement();
    public static final UpdateActionElement updateAction = new UpdateActionElement();
    public static final DryRunElement dryRun = new DryRunElement();
    public static final ActionValueElement actionValue = new ActionValueElement();
    public static final ActionPropertyKeysElement actionPropertyKeys = new ActionPropertyKeysElement();

    /*
     * Init once during the class initialisation.
     * All the parser instances will use the same parser elements instances
     */
    static {
        root.initialize();
    }

    /**
     * Initialize the parser process starting from the root element which represent the entire language
     *
     * @param query The AQL query string
     * @return Parsing result
     * @throws AqlParserException If query parsing fails
     */
    public ParserElementResultContainer parse(String query) {
        AqlParserContext parserContext = new AqlParserContext();

        // peelOff will always return a single value, we use a lambda for cleaner code
        return root.peelOff(query, parserContext).filter(res -> StringUtils.isBlank(res.getQueryRemainder())).stream()
                .findFirst().orElseThrow(() -> new AqlParserException(String.format(
                        "Failed to parse query: %s, it looks like there is syntax error near" +
                                " the following sub-query: %s", query, parserContext.getQueryRemainder() != null ?
                                parserContext.getQueryRemainder().trim() : query.trim())));
    }
}


