/*
 * Copyright (C) 2026 the fork authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package org.artifactory.security.access.embedded.client;

import org.jfrog.access.client.AccessClientBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Builds the objects embedded access hands out for the Access client interfaces.
 * <p>
 * The closed Access client interfaces are large (one has about forty methods) and grow between releases, while the
 * fork uses only part of each. Implementing them directly would mean dozens of hand-written refusal bodies per
 * interface and a compile break on every upgrade that adds a method. Instead each adapter is a plain
 * <em>delegate</em> class whose public methods copy the signatures it implements, and the object callers see is a
 * {@link Proxy} for the interface:
 * <ul>
 *     <li>a call whose name and parameter types match a public delegate method goes to that method;</li>
 *     <li>{@code useAuth(..)} returns the proxy itself and {@code hasAuth()} returns {@code true}, unless the
 *     delegate defines them: there is no remote server to authenticate to;</li>
 *     <li>a {@code default} interface method the delegate does not define runs its interface body, which can only
 *     reach other proxy methods;</li>
 *     <li>every other call throws {@link EmbeddedAccessUnsupportedException} and is logged at WARN once per
 *     method;</li>
 *     <li>{@code toString}, {@code hashCode} and {@code equals} are answered by the proxy itself (identity).</li>
 * </ul>
 * The mapping is resolved and checked when the proxy is built: every public instance method declared by the
 * delegate must match an interface method with an assignable return type, so a typo in a delegate signature fails
 * at construction instead of silently falling through to the refusal. {@link #isImplemented} exposes the mapping
 * to tests.
 */
public final class FailClosed {

    private static final Logger log = LoggerFactory.getLogger(FailClosed.class);

    /** Operations already logged, so each refused method is logged once per JVM. */
    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    private FailClosed() {
    }

    /**
     * Returns a proxy that refuses every method of an interface.
     *
     * @param iface   the interface
     * @param surface the surface name used in messages, for example {@code cert}
     * @param <T>     the interface type
     * @return the proxy
     */
    public static <T> T of(Class<T> iface, String surface) {
        return create(iface, surface, null);
    }

    /**
     * Returns a proxy that dispatches to a delegate and refuses every method the delegate does not define.
     *
     * @param iface    the interface
     * @param surface  the surface name used in messages, for example {@code users}
     * @param delegate the object holding the implemented methods
     * @param <T>      the interface type
     * @return the proxy
     * @throws IllegalArgumentException if a public delegate method matches no interface method, or matches one
     *                                  with an incompatible return type
     */
    public static <T> T backedBy(Class<T> iface, String surface, Object delegate) {
        return create(iface, surface, Objects.requireNonNull(delegate, "delegate"));
    }

    /**
     * Tells whether a call to an interface method reaches an implementation rather than the refusal.
     *
     * @param proxy  an object built by this class
     * @param method an interface method
     * @return {@code true} if the delegate defines the method or it is answered by the proxy itself
     * @throws IllegalArgumentException if {@code proxy} was not built by this class
     */
    public static boolean isImplemented(Object proxy, Method method) {
        return handler(proxy).implemented(method);
    }

    /**
     * Returns the surface name of a proxy built by this class.
     *
     * @param proxy the proxy
     * @return the surface, or empty if {@code proxy} was not built by this class
     */
    public static Optional<String> surfaceOf(Object proxy) {
        if (proxy != null && Proxy.isProxyClass(proxy.getClass())
                && Proxy.getInvocationHandler(proxy) instanceof Handler h) {
            return Optional.of(h.surface);
        }
        return Optional.empty();
    }

    /**
     * Tells whether a proxy built by this class has no delegate, so that it refuses everything.
     *
     * @param proxy the proxy
     * @return {@code true} for a proxy from {@link #of}
     */
    public static boolean refusesEverything(Object proxy) {
        return handler(proxy).targets.isEmpty();
    }

    private static Handler handler(Object proxy) {
        if (proxy != null && Proxy.isProxyClass(proxy.getClass())
                && Proxy.getInvocationHandler(proxy) instanceof Handler h) {
            return h;
        }
        throw new IllegalArgumentException("not built by FailClosed: " + proxy);
    }

    private static <T> T create(Class<T> iface, String surface, Object delegate) {
        if (!iface.isInterface()) {
            throw new IllegalArgumentException(iface + " is not an interface");
        }
        Map<Method, Method> targets = delegate == null ? Map.of() : resolve(iface, delegate.getClass());
        Handler handler = new Handler(iface, surface, delegate, targets);
        return iface.cast(Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, handler));
    }

    private static Map<Method, Method> resolve(Class<?> iface, Class<?> delegateType) {
        Map<Method, Method> targets = new HashMap<>();
        Map<String, Method> byKey = new HashMap<>();
        for (Method m : iface.getMethods()) {
            if (Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            byKey.putIfAbsent(key(m), m);
            Method impl = findPublic(delegateType, m);
            if (impl != null) {
                if (!m.getReturnType().isAssignableFrom(impl.getReturnType())) {
                    throw new IllegalArgumentException(delegateType.getSimpleName() + "." + impl.getName()
                            + " returns " + impl.getReturnType().getName() + ", not " + m.getReturnType().getName());
                }
                targets.put(m, impl);
            }
        }
        for (Method declared : delegateType.getDeclaredMethods()) {
            int mod = declared.getModifiers();
            if (Modifier.isPublic(mod) && !Modifier.isStatic(mod) && !declared.isSynthetic()
                    && !byKey.containsKey(key(declared))) {
                throw new IllegalArgumentException(delegateType.getSimpleName() + "." + declared.getName()
                        + Arrays.toString(declared.getParameterTypes()) + " matches no method of "
                        + iface.getName());
            }
        }
        return Map.copyOf(targets);
    }

    private static Method findPublic(Class<?> type, Method m) {
        try {
            Method found = type.getMethod(m.getName(), m.getParameterTypes());
            return found.getDeclaringClass() == Object.class ? null : found;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static String key(Method m) {
        return m.getName() + Arrays.stream(m.getParameterTypes()).map(Class::getName)
                .collect(Collectors.joining(",", "(", ")"));
    }

    private static boolean isObjectMethod(Method m) {
        return switch (m.getName()) {
            case "toString", "hashCode" -> m.getParameterCount() == 0;
            case "equals" -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == Object.class;
            default -> false;
        };
    }

    private static boolean isAuthMethod(Method m) {
        return m.getDeclaringClass() == AccessClientBase.class
                && ("useAuth".equals(m.getName()) || "hasAuth".equals(m.getName()));
    }

    /** The invocation handler; immutable after construction. */
    private static final class Handler implements InvocationHandler {
        private final Class<?> iface;
        private final String surface;
        private final Object delegate;
        private final Map<Method, Method> targets;

        private Handler(Class<?> iface, String surface, Object delegate, Map<Method, Method> targets) {
            this.iface = iface;
            this.surface = surface;
            this.delegate = delegate;
            this.targets = targets;
        }

        boolean implemented(Method method) {
            return targets.containsKey(method) || isObjectMethod(method) || isAuthMethod(method)
                    || (method.isDefault() && !targets.isEmpty());
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Method target = targets.get(method);
            if (target != null) {
                try {
                    return target.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            }
            if (isObjectMethod(method)) {
                return switch (method.getName()) {
                    case "toString" -> "EmbeddedAccess[" + surface + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> proxy == args[0];
                };
            }
            if (isAuthMethod(method)) {
                return "hasAuth".equals(method.getName()) ? Boolean.TRUE : proxy;
            }
            if (method.isDefault() && !targets.isEmpty()) {
                return InvocationHandler.invokeDefault(proxy, method, args);
            }
            String operation = surface + "." + method.getName();
            if (LOGGED.add(iface.getName() + "#" + key(method))) {
                log.warn("Refused Access client call {}: {}", operation,
                        EmbeddedAccessUnsupportedException.PREFIX + operation);
            }
            throw new EmbeddedAccessUnsupportedException(operation);
        }
    }
}
