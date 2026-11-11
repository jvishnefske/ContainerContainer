package org.artifactory.aql.model;

/**
 * @author Uriah Levy
 */
public enum AqlFunctionFieldEnum implements AqlFieldEnum {
    COUNT("count", AqlVariableTypeEnum.longInt);

    private final String signature;
    private final AqlVariableTypeEnum returnValueType;

    AqlFunctionFieldEnum(String signature, AqlVariableTypeEnum longInt) {
        this.signature = signature;
        returnValueType = longInt;
    }

    public AqlVariableTypeEnum getReturnValueType() {
        return returnValueType;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getSignature() {
        return signature;
    }

    @Override
    public AqlDomainEnum getDomain() {
        return null;
    }

    @Override
    public boolean isId() {
        return false;
    }

    @Override
    public <T> T doSwitch(AqlFieldEnumSwitch<T> fieldEnumSwitch) {
        return fieldEnumSwitch.caseOf(this);
    }
}
