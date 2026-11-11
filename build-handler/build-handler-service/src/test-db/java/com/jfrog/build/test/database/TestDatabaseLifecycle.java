package com.jfrog.build.test.database;

import lombok.NonNull;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

public class TestDatabaseLifecycle implements BeforeAllCallback, ExtensionContext.Store.CloseableResource {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    @Override
    public void beforeAll(@NonNull ExtensionContext context) {
        if (STARTED.compareAndSet(false, true)) {
            context.getRoot().getStore(GLOBAL).put("any unique name", this);
            TestDatabaseProvider.initDatabase();
        }
    }

    @Override
    public void close() {
        TestDatabaseProvider.stopDatabase();
    }
}
