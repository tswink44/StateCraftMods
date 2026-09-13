package dev.statecraft.economy;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Stream;

final class EconomyEngineTest {
    @TestFactory
    Stream<DynamicTest> financialRegressionScenarios() {
        return Arrays.stream(EconomyRegressionScenarios.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 0)
                .sorted(Comparator.comparing(java.lang.reflect.Method::getName))
                .map(method -> DynamicTest.dynamicTest(method.getName(), () -> {
                    try { method.invoke(null); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                }));
    }

    @TestFactory
    Stream<DynamicTest> jsonAndPersistenceScenarios() {
        return Arrays.stream(EconomyDataScenarios.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()) && method.getParameterCount() == 0)
                .sorted(Comparator.comparing(java.lang.reflect.Method::getName))
                .map(method -> DynamicTest.dynamicTest(method.getName(), () -> {
                    try { method.invoke(null); }
                    catch (InvocationTargetException failure) { throw failure.getCause(); }
                }));
    }
}
