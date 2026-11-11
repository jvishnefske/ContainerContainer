package org.artifactory.storage.db.locks.service.annotation;

import org.jfrog.storage.DbType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface NativeDbLocksType {

    DbType value();
}
