package org.artifactory.ui.rest.model.artifacts.browse.treebrowser.nodes;

import org.apache.commons.lang3.StringUtils;
import org.artifactory.common.ConstantValues;
import org.jfrog.common.legacy.maven.comparator.VersionComparator;
import org.jfrog.common.legacy.maven.comparator.VersionComparators;

import java.util.Comparator;
import java.util.regex.Pattern;

import static org.artifactory.api.repo.BaseBrowsableItem.UP;

/**
 * @author Tomer Slutsky
 */
public class NodeComparator implements Comparator<BaseNode> {
    private final Pattern splitNumsAndStrings = Pattern.compile("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
    private final VersionComparator numericVersionComparator =
            VersionComparators.getVersionComparator("numeric");

    @Override
    public int compare(BaseNode node, BaseNode otherNode) {
        if (!ConstantValues.treebrowserNumericSort.getBoolean()) {
            return compareNatural(node, otherNode);
        }

        int compare = sortFolderFirst(node, otherNode);
        if (compare == 0) {
            return sortNumericFirst(node, otherNode);
        }
        return compare;
    }

    protected int compareNatural(BaseNode node, BaseNode otherNode) {
        if (node.getText().equals(UP) && otherNode.getText().equals(UP) && node.isFolder() && otherNode.isFolder()) {
            return 0;
        } else if (node.getText().equals(UP) || (node.isFolder() && !otherNode.isFolder())) {
            return -1;
        } else if (otherNode.getText().equals(UP) || (!node.isFolder() && otherNode.isFolder())) {
            return 1;
        }
        return node.getText().compareToIgnoreCase(otherNode.getText());
    }

    private int sortFolderFirst(BaseNode node, BaseNode otherNode) {
        if (node.isFolder() && otherNode.isFolder()) {
            return 0;
        } else if (node.isFolder()) {
            return -1;
        } else if (otherNode.isFolder()) {
            return 1;
        }

        return 0;
    }

    protected int sortNumericFirst(BaseNode node, BaseNode otherNode) {
        String[] first = splitNumsAndStrings.split(node.getText());
        String[] second = splitNumsAndStrings.split(otherNode.getText());
        int maxLengthOfSplits = Math.max(first.length, second.length);
        int compare = 0;

        for (int i = 0; i < maxLengthOfSplits; i++) {
            compare = currentIndexCompare(i, first, second);
            if (compare == 0) {
                if (StringUtils.isNumeric(first[i]) && StringUtils.isNumeric(second[i])) {
                    compare = numericVersionComparator.compare(first[i], second[i]);
                } else {
                    String v1 = first[i];
                    String v2 = second[i];
                    compare = v1.compareToIgnoreCase(v2);
                }
                if (compare != 0) {
                    return compare;
                }
            } else {
                return compare;
            }
        }
        return compare;
    }

    private int currentIndexCompare(int index, String[] first, String[] second) {
        if (index >= first.length) {
            return -1;
        } else if (index >= second.length) {
            return 1;
        } else if (StringUtils.isNumeric(first[index]) && !StringUtils.isNumeric(second[index])) {
            return -1;
        } else if (StringUtils.isNumeric(second[index]) && !StringUtils.isNumeric(first[index])) {
            return 1;
        }

        return 0;
    }
}
