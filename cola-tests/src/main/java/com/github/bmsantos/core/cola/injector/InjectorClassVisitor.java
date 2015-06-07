/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.bmsantos.core.cola.injector;

import static com.github.bmsantos.core.cola.filters.FilterProcessor.filtrate;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.RETURN;
import gherkin.deps.com.google.gson.Gson;
import gherkin.formatter.model.Step;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;

import com.github.bmsantos.core.cola.filters.Filter;
import com.github.bmsantos.core.cola.filters.ReportFilter;
import com.github.bmsantos.core.cola.filters.TagFilter;
import com.github.bmsantos.core.cola.formatter.FeatureDetails;
import com.github.bmsantos.core.cola.formatter.FeatureFormatter;
import com.github.bmsantos.core.cola.formatter.ProjectionValues;
import com.github.bmsantos.core.cola.formatter.ReportDetails;
import com.github.bmsantos.core.cola.formatter.ScenarioDetails;

public class InjectorClassVisitor extends ClassVisitor {

    private final ClassWriter cw;
    private String stories;
    private List<FeatureDetails> features;
    private final List<Filter> filters = Arrays.<Filter> asList(new TagFilter(), new ReportFilter());

    public InjectorClassVisitor(final int api, final ClassWriter cw, final List<FeatureDetails> features) {
        super(api, cw);
        this.cw = cw;
        this.features = features;
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String desc, final String signature,
        final Object value) {
        if ((features == null || features.isEmpty()) && name.equals("stories")) {
            stories = (String) value;
            features = new ArrayList<>();
            features.add(FeatureFormatter.parse(stories, "/from/junit/stories/field"));
        }
        return super.visitField(access, name, desc, signature, value);
    }

    @Override
    public void visitEnd() {
        for (final FeatureDetails feature : filtrate(features).using(filters)) {
            injectTestMethod(feature);
        }
        super.visitEnd();
    }

    private void injectTestMethod(final FeatureDetails featureDetails) {

        // process scenarios
        for (final ScenarioDetails scenarioDetails : featureDetails.getScenarios()) {

            // project background into scenario story
            final List<Step> steps = new ArrayList<>(featureDetails.getBackgroundSteps());
            steps.addAll(scenarioDetails.getSteps());
            if (steps.isEmpty()) {
                continue;
            }

            final String story = buildStory(steps);
            final String featureName = featureDetails.getFeature().getName();
            final String scenarioName = scenarioDetails.getScenario().getName();
            String values = "";

            final String reports = serializeReports(featureDetails.getReports(), scenarioDetails.getReports());

            if (featureDetails.ignore() || scenarioDetails.ignore()) {
                injectIgnoreMethod(featureName, scenarioName);
            } else if (scenarioDetails.hasProjectionValues()) {
                final ProjectionValues projectionValues = scenarioDetails.getProjectionValues();
                for (int i = 0; i < projectionValues.size(); i++) {
                    values = projectionValues.doRowProjection(i);
                    injectTestMethod(featureName, scenarioName + " - projection " + i, story, values, reports);
                }
            } else {
                injectTestMethod(featureName, scenarioName, story, values, reports);
            }
        }
    }

    private void injectTestMethod(final String feature, final String scenario, final String story,
        final String projectionValues, final String reports) {

        final MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, feature + " : " + scenario, "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitLdcInsn(feature);
        mv.visitLdcInsn(scenario);
        mv.visitLdcInsn(story);
        mv.visitLdcInsn(projectionValues);
        mv.visitLdcInsn(reports);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESTATIC, "com/github/bmsantos/core/cola/story/processor/StoryProcessor", "process",
            "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V",
            false);
        mv.visitInsn(RETURN);
        mv.visitAnnotation("Lorg/junit/Test;", true);
        mv.visitEnd();
        mv.visitMaxs(0, 0);
    }

    private void injectIgnoreMethod(final String feature, final String scenario) {
        final MethodVisitor mv =
            cw.visitMethod(ACC_PUBLIC, feature + " : " + scenario + " (@ignored)", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitLdcInsn(feature);
        mv.visitLdcInsn(scenario);
        mv.visitMethodInsn(INVOKESTATIC, "com/github/bmsantos/core/cola/story/processor/StoryProcessor", "ignore",
            "(Ljava/lang/String;Ljava/lang/String;)V", false);
        mv.visitInsn(RETURN);
        mv.visitAnnotation("Lorg/junit/Test;", true);
        mv.visitEnd();
        mv.visitMaxs(0, 0);
    }

    private String buildStory(final List<Step> steps) {
        String story = "";
        for (final Step step : steps) {
            story += step.getKeyword() + step.getName() + "\n";
        }
        return story;
    }

    @SafeVarargs
    private final String serializeReports(final List<ReportDetails>... list) {
        final List<ReportDetails> result = new ArrayList<>();
        for (final List<ReportDetails> reports : list) {
            result.addAll(reports);
        }
        return result.isEmpty() ? "" : new Gson().toJson(result);
    }
}