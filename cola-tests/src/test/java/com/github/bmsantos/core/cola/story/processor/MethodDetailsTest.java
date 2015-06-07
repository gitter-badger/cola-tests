package com.github.bmsantos.core.cola.story.processor;

import static java.util.Arrays.asList;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;

import com.github.bmsantos.core.cola.story.annotations.Group;
import com.github.bmsantos.core.cola.story.annotations.Projection;
import com.github.bmsantos.core.cola.story.processor.MethodDetails;

public class MethodDetailsTest {

    private static final String STEP = "Given <pints> beers per <alcoholics> developers";

    private static final String STEP_GROUPS = "Given 50 beers per 12 developers";

    private static final String ANNOTATION_VALUE = "Given (\\d+) beers per (\\d+) developers";

    private MethodDetails uut;

    private Method method;

    private Method methodGroups;

    @Before
    public void setUp() throws Exception {
        method = this.getClass().getMethod("given", new Class<?>[] { String.class, String.class });
        methodGroups = this.getClass().getMethod("givenGroups", new Class<?>[] { Integer.class, Integer.class });
    }

    @Test
    public void shouldStoreMethod() {
        // Given
        uut = MethodDetails.build(method, null, null, null);

        // When
        final Method result = uut.getMethod();

        // Then
        assertThat(result, equalTo(method));
    }

    @Test
    public void shouldPrepareStepProjections() {
        // Given
        uut = MethodDetails.build(method, STEP, null, null);

        // When
        final List<String> result = uut.getProjections();

        // Then
        assertThat(result, Matchers.contains("pints", "alcoholics"));
    }

    @Test
    public void shouldPrepareProjectionArguments() {
        // Given
        final Map<String, String> projectionValues = new HashMap<>();
        projectionValues.put("pints", "100");
        projectionValues.put("alcoholics", "25");

        uut = MethodDetails.build(method, STEP, projectionValues, null);

        // When
        final Object[] result = uut.getArguments();

        // Then
        assertThat(asList(result), Matchers.contains((Object) "100", (Object) "25"));
    }

    @Test
    public void shouldPrepareGroupArguments() {
        // Given
        uut = MethodDetails.build(methodGroups, STEP_GROUPS, null, ANNOTATION_VALUE);

        // When
        final Object[] result = uut.getArguments();

        // Then
        assertThat(asList(result), Matchers.contains((Object) 50, (Object) 12));
    }

    public void given(@Projection("pints") final String pints, @Projection("alcoholics") final String alcolholics) {
    }

    public void givenGroups(@Group(1) final Integer pints, @Group(2) final Integer alcolholics) {
    }
}