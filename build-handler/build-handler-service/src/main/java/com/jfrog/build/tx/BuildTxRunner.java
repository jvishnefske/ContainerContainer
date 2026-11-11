package com.jfrog.build.tx;

import lombok.NonNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

public class BuildTxRunner {

    @Transactional
    public void runTx(@NonNull Runnable runnable) {
        runnable.run();
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void runTxNew(@NonNull Runnable runnable) {
        runnable.run();
    }

    @NonNull
    @Transactional
    public <T> T runSupplierTx(@NonNull Supplier<T> supplier) {
        return supplier.get();
    }
}
