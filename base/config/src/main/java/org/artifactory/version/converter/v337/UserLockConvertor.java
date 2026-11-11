package org.artifactory.version.converter.v337;

import org.artifactory.config.AccessConfigHolder;
import org.artifactory.version.converter.XmlConverter;
import org.jdom2.Document;

import static org.artifactory.common.ConstantValues.loginBlockDelayMillis;
import static org.artifactory.common.ConstantValues.loginMaxBlockDelayMillis;
import static org.artifactory.common.ConstantValues.maxIncorrectLoginAttempts;

public class UserLockConvertor implements XmlConverter {

    @Override
    public void convert(Document doc) {
        AccessConfigHolder.getInstance().addMaxIncorrectLoginAttempts(maxIncorrectLoginAttempts.getLong());
        AccessConfigHolder.getInstance().addLoginMaxBlockDelayMillis(loginMaxBlockDelayMillis.getLong());
        AccessConfigHolder.getInstance().addLoginBlockDelayMillis(loginBlockDelayMillis.getLong());
    }
}
