package org.artifactory.storage.db.itest;

import org.testng.IHookCallBack;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.Test;

/**
 * Base class for DB integration tests that are only meaningful on PostgreSQL (e.g. tests that rely on
 * PostgreSQL-specific DDL such as table partitioning or {@code EXPLAIN} plan shape).
 * <p>
 * The PostgreSQL gate is enforced structurally via TestNG's {@link org.testng.IHookable} seam rather than
 * a {@code requirePostgresql()} call that each {@code @Test} method must remember to make: every test method
 * in a subclass is gated automatically, so a newly added test cannot accidentally run on a non-PostgreSQL DB.
 * <p>
 * Why the hook and not a {@code @BeforeMethod} skip: TestNG fires {@code onTestStart} <em>before</em> invoking
 * {@link #run(IHookCallBack, ITestResult)}, so throwing {@link SkipException} here happens after the test
 * descriptor has started — the same lifecycle position as throwing from the test-method body. Throwing from a
 * {@code @Before*} fixture instead skips the {@code @Test} before it ever starts, which confuses the Develocity
 * build-scan listener ({@code IllegalArgumentException: Received a completed event for unknown test descriptor}).
 * <p>
 * {@link org.springframework.test.context.testng.AbstractTestNGSpringContextTests} (via {@link DbBaseTest})
 * already implements {@code IHookable} to drive its {@code TestContextManager} around each test method, so the
 * happy path must delegate to {@code super.run(...)} — calling {@code callBack.runTestMethod(...)} directly would
 * bypass Spring's per-method test-context lifecycle (transactions, context injection, etc.).
 */
public abstract class PostgresOnlyDbTest extends DbBaseTest {

    protected boolean isPostgresqlDb() {
        return dbProperties.isPostgres();
    }

    // enabled = false so TestNG does not mistake this IHookable override for a @Test method: the enclosing
    // test classes are annotated with class-level @Test, which would otherwise schedule run(...) as a test and
    // fail with "Cannot inject @Test annotated Method [run]". The IHookable invoker still calls it as the hook.
    @Test(enabled = false)
    @Override
    public void run(IHookCallBack callBack, ITestResult testResult) {
        if (!isPostgresqlDb()) {
            throw new SkipException("PostgreSQL-only test: " + testResult.getName());
        }
        super.run(callBack, testResult);
    }
}
