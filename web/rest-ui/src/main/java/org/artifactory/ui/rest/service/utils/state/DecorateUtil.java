package org.artifactory.ui.rest.service.utils.state;

import org.artifactory.customer.updates.UsageUpdateItem;
import org.artifactory.customer.updates.UsageUpdateModel;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexei Vainshtein
 */
public class DecorateUtil {

    public static String getWarningMessage(UsageUpdateModel usage) {
        StringBuilder builder = new StringBuilder("Quota Alert: ");
        List<String> listOfWarnings = new ArrayList<>();
        UsageUpdateItem traffic = usage.getTraffic();
        if (traffic != null && traffic.getStatus().equalsIgnoreCase(UsageUpdateModel.StatusType.WARNING.getType())) {
            listOfWarnings.add(String
                    .format("Data Transfer (%s out of %s)", getUserReadableSize(traffic.getUsed()),
                            getUserReadableSize(traffic.getLimit())));
        }
        UsageUpdateItem storage = usage.getStorage();
        if (storage != null && storage.getStatus().equalsIgnoreCase(UsageUpdateModel.StatusType.WARNING.getType())) {
            listOfWarnings.add(String
                    .format("Storage (%s out of %s)", getUserReadableSize(storage.getUsed()),
                            getUserReadableSize(storage.getLimit())));
        }
        UsageUpdateItem buildTime = usage.getBuildTime();
        if (buildTime != null && buildTime.getStatus()
                .equalsIgnoreCase(UsageUpdateModel.StatusType.WARNING.getType())) {
            listOfWarnings
                    .add(String.format("Build Minutes (%d out of %d)", buildTime.getUsed(), buildTime.getLimit()));
        }
        buildMessage(builder, listOfWarnings);
        builder.append(". To enjoy unlimited usage, Upgrade");
        return builder.toString();
    }

    public static String getErrorMessage(UsageUpdateModel usage) {
        StringBuilder builder = new StringBuilder("You have reached your monthly quota for ");
        List<String> errorMessages = new ArrayList<>();
        UsageUpdateItem traffic = usage.getTraffic();
        if (traffic != null && traffic.getStatus()
                .equalsIgnoreCase(UsageUpdateModel.StatusType.BLOCKED.getType())) {
            errorMessages.add("Data Transfer");
        }
        UsageUpdateItem storage = usage.getStorage();
        if (storage != null && storage.getStatus()
                .equalsIgnoreCase(UsageUpdateModel.StatusType.BLOCKED.getType())) {
            errorMessages.add("Storage");
        }
        UsageUpdateItem buildTime = usage.getBuildTime();
        if (buildTime != null && buildTime.getStatus()
                .equalsIgnoreCase(UsageUpdateModel.StatusType.BLOCKED.getType())) {
            errorMessages.add("Build Minutes");
        }
        buildMessage(builder, errorMessages);
        builder.append(". To enjoy unlimited usage, Upgrade");
        return builder.toString();
    }

    /**
     * Returns a readable size for the user
     */
    static String getUserReadableSize(double size) {
        String userReadableSize = "";
        double kb = size / 1024;
        double mb = kb / 1024;
        double gb = mb / 1024;
        double tb = gb / 1024;
        if (size < 1024L) {
            userReadableSize = size + " Bytes";
        } else if (size < (1024L * 1024)) {
            userReadableSize = String.format("%.2f", kb) + " KB";
        } else if (size < (1024L * 1024 * 1024)) {
            userReadableSize = String.format("%.2f", mb) + " MB";
        } else if (size < (1024L * 1024 * 1024 * 1024)) {
            userReadableSize = String.format("%.2f", gb) + " GB";
        } else {
            userReadableSize = String.format("%.2f", tb) + " TB";
        }
        return userReadableSize;
    }

    /**
     * Appends all the messages into one readable message.
     */
    static void buildMessage(StringBuilder builder, List<String> messages) {
        if (messages.size() == 1) {
            builder.append(messages.get(0));
            return;
        }
        int i = 1;
        for (String message : messages) {
            builder.append(message);
            if (messages.size() <= i) {
                return;
            }

            if (messages.size() == i + 1) {
                builder.append(" and ");
            } else {
                builder.append(", ");
            }
            i++;
        }
    }
}
