/**
 * This package takes care of collecting metrics from the most important components and frameworks: JVM, JAX-RS,
 * Logback and many others.
 *  <p>
 *      The {@link org.artifactory.metrics.framework.spring.OpenMetricsBeansConfig} class actually instantiates
 *      the Micrometer's Prometheus metrics registry. Spring will use it to both maintain the in-memory registry
 *      (and a last snapshot) of metrics, and also to serialize these metrics in the OpenMetrics format.
 * </p>
 * <p>
 *     In addition, this package provides the {@link org.artifactory.metrics.framework.spring.SpringOpenMetricsWriter}
 *     class, which can take the current snapshot of Spring metrics (from the Prometheus-specific registry) and
 *     dump them to the given stream, using the OpenMetrics format,
 * </p>
 * <p>
 *     We did not implement the instrumentation by ourselves, but used the well-known Micrometer framework
 *     (which is the default choice for Spring and Spring Boot applications).  We just create ready-to-use beans,
 *     implementing the {@link io.micrometer.core.instrument.binder.MeterBinder} interface, and Micrometer take care
 *     of injecting correct filters / interceptors / callbacks.  Most of the code in ...MetricsConfiguration classes
 *     was simply copied from the Spring Boot Actuator's auto-configuration (with slight adjustments).  Spring Boot
 *     projects get these features "for free", but Artifactory needs to instantiate these beans by itself.
 *  </p>
 */

package org.artifactory.metrics.framework.spring;