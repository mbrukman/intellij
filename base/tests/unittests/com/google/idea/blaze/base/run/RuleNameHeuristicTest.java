/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.run;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.model.primitives.Label;
import com.intellij.openapi.extensions.impl.ExtensionPointImpl;
import java.io.File;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TargetNameHeuristic}. */
@RunWith(JUnit4.class)
public class RuleNameHeuristicTest extends BlazeTestCase {

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    super.initTest(applicationServices, projectServices);

    ExtensionPointImpl<TestTargetHeuristic> ep =
        registerExtensionPoint(TestTargetHeuristic.EP_NAME, TestTargetHeuristic.class);
    ep.registerExtension(new TargetNameHeuristic());
  }

  @Test
  public void testPredicateMatchingName() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    TargetIdeInfo target =
        TargetIdeInfo.builder().setLabel("//foo:FooTest").setKind("java_test").build();
    assertThat(new TargetNameHeuristic().matchesSource(project, target, null, source, null))
        .isTrue();
  }

  @Test
  public void testPredicateMatchingNameAndPath() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    TargetIdeInfo target =
        TargetIdeInfo.builder().setLabel("//foo:foo/FooTest").setKind("java_test").build();
    assertThat(new TargetNameHeuristic().matchesSource(project, target, null, source, null))
        .isTrue();
  }

  @Test
  public void testPredicateNotMatchingForPartialOverlap() throws Exception {
    File source = new File("java/com/foo/BarFooTest.java");
    TargetIdeInfo target =
        TargetIdeInfo.builder().setLabel("//foo:FooTest").setKind("java_test").build();
    assertThat(new TargetNameHeuristic().matchesSource(project, target, null, source, null))
        .isFalse();
  }

  @Test
  public void testPredicateNotMatchingIncorrectPath() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    TargetIdeInfo target =
        TargetIdeInfo.builder().setLabel("//foo:bar/FooTest").setKind("java_test").build();
    assertThat(new TargetNameHeuristic().matchesSource(project, target, null, source, null))
        .isFalse();
  }

  @Test
  public void testPredicateDifferentName() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    TargetIdeInfo target =
        TargetIdeInfo.builder().setLabel("//foo:ForTest").setKind("java_test").build();
    assertThat(new TargetNameHeuristic().matchesSource(project, target, null, source, null))
        .isFalse();
  }

  @Test
  public void testFilterNoMatchesFallBackToFirstRule() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    Collection<TargetIdeInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder().setLabel("//foo:FirstTest").setKind("java_test").build(),
            TargetIdeInfo.builder().setLabel("//bar:OtherTest").setKind("java_test").build());
    Label match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(project, null, source, targets, null);
    assertThat(match).isEqualTo(Label.create("//foo:FirstTest"));
  }

  @Test
  public void testFilterOneMatch() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    Collection<TargetIdeInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder().setLabel("//bar:FirstTest").setKind("java_test").build(),
            TargetIdeInfo.builder().setLabel("//foo:FooTest").setKind("java_test").build());
    Label match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(project, null, source, targets, null);
    assertThat(match).isEqualTo(Label.create("//foo:FooTest"));
  }

  @Test
  public void testFilterChoosesFirstMatch() throws Exception {
    File source = new File("java/com/foo/FooTest.java");
    Collection<TargetIdeInfo> targets =
        ImmutableList.of(
            TargetIdeInfo.builder().setLabel("//bar:OtherTest").setKind("java_test").build(),
            TargetIdeInfo.builder().setLabel("//foo:FooTest").setKind("java_test").build(),
            TargetIdeInfo.builder().setLabel("//bar/foo:FooTest").setKind("java_test").build());
    Label match =
        TestTargetHeuristic.chooseTestTargetForSourceFile(project, null, source, targets, null);
    assertThat(match).isEqualTo(Label.create("//foo:FooTest"));
  }
}
