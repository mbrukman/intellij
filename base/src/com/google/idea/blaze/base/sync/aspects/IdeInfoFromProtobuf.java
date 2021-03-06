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

package com.google.idea.blaze.base.sync.aspects;

import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.idea.blaze.base.ideinfo.AndroidIdeInfo;
import com.google.idea.blaze.base.ideinfo.AndroidSdkIdeInfo;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.ideinfo.CIdeInfo;
import com.google.idea.blaze.base.ideinfo.CToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.Dependency;
import com.google.idea.blaze.base.ideinfo.Dependency.DependencyType;
import com.google.idea.blaze.base.ideinfo.JavaIdeInfo;
import com.google.idea.blaze.base.ideinfo.JavaToolchainIdeInfo;
import com.google.idea.blaze.base.ideinfo.LibraryArtifact;
import com.google.idea.blaze.base.ideinfo.ProtoLibraryLegacyInfo;
import com.google.idea.blaze.base.ideinfo.PyIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetIdeInfo;
import com.google.idea.blaze.base.ideinfo.TargetKey;
import com.google.idea.blaze.base.ideinfo.TestIdeInfo;
import com.google.idea.blaze.base.model.primitives.ExecutionRootPath;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.repackaged.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

/** Conversion functions from new aspect-style Bazel IDE info to ASWB internal classes. */
public class IdeInfoFromProtobuf {

  @Nullable
  public static TargetIdeInfo makeTargetIdeInfo(IntellijIdeInfo.TargetIdeInfo message) {
    Kind kind = getKind(message);
    if (kind == null) {
      return null;
    }
    TargetKey key = getKey(message);
    ArtifactLocation buildFile = getBuildFile(message);

    final Collection<Dependency> dependencies;
    if (message.getDepsCount() > 0) {
      dependencies =
          message.getDepsList().stream().map(IdeInfoFromProtobuf::makeDependency).collect(toList());
    } else {
      dependencies =
          Lists.newArrayListWithCapacity(
              message.getDependenciesCount() + message.getRuntimeDepsCount());
      dependencies.addAll(
          makeDependencyListFromLabelList(
              message.getDependenciesList(), DependencyType.COMPILE_TIME));
      dependencies.addAll(
          makeDependencyListFromLabelList(message.getRuntimeDepsList(), DependencyType.RUNTIME));
    }

    Collection<String> tags = ImmutableList.copyOf(message.getTagsList());

    Collection<ArtifactLocation> sources = Lists.newArrayList();
    CIdeInfo cIdeInfo = null;
    if (message.hasCIdeInfo()) {
      cIdeInfo = makeCIdeInfo(message.getCIdeInfo());
      sources.addAll(cIdeInfo.sources);
    }
    CToolchainIdeInfo cToolchainIdeInfo = null;
    if (message.hasCToolchainIdeInfo()) {
      cToolchainIdeInfo = makeCToolchainIdeInfo(message.getCToolchainIdeInfo());
    }
    JavaIdeInfo javaIdeInfo = null;
    if (message.hasJavaIdeInfo()) {
      javaIdeInfo = makeJavaIdeInfo(message.getJavaIdeInfo());
      Collection<ArtifactLocation> javaSources =
          makeArtifactLocationList(message.getJavaIdeInfo().getSourcesList());
      sources.addAll(javaSources);
    }
    AndroidIdeInfo androidIdeInfo = null;
    if (message.hasAndroidIdeInfo()) {
      androidIdeInfo = makeAndroidIdeInfo(message.getAndroidIdeInfo());
    }
    AndroidSdkIdeInfo androidSdkIdeInfo = null;
    if (message.hasAndroidSdkIdeInfo()) {
      androidSdkIdeInfo = makeAndroidSdkIdeInfo(message.getAndroidSdkIdeInfo());
    }
    PyIdeInfo pyIdeInfo = null;
    if (message.hasPyIdeInfo()) {
      pyIdeInfo = makePyIdeInfo(message.getPyIdeInfo());
      sources.addAll(pyIdeInfo.sources);
    }
    TestIdeInfo testIdeInfo = null;
    if (message.hasTestInfo()) {
      testIdeInfo = makeTestIdeInfo(message.getTestInfo());
    }
    ProtoLibraryLegacyInfo protoLibraryLegacyInfo = null;
    if (message.hasProtoLibraryLegacyJavaIdeInfo()) {
      protoLibraryLegacyInfo =
          makeProtoLibraryLegacyInfo(message.getProtoLibraryLegacyJavaIdeInfo());
    }
    JavaToolchainIdeInfo javaToolchainIdeInfo = null;
    if (message.hasJavaToolchainIdeInfo()) {
      javaToolchainIdeInfo = makeJavaToolchainIdeInfo(message.getJavaToolchainIdeInfo());
    }

    return new TargetIdeInfo(
        key,
        kind,
        buildFile,
        dependencies,
        tags,
        sources,
        cIdeInfo,
        cToolchainIdeInfo,
        javaIdeInfo,
        androidIdeInfo,
        androidSdkIdeInfo,
        pyIdeInfo,
        testIdeInfo,
        protoLibraryLegacyInfo,
        javaToolchainIdeInfo);
  }

  private static Collection<Dependency> makeDependencyListFromLabelList(
      List<String> dependencyList, Dependency.DependencyType dependencyType) {
    return dependencyList
        .stream()
        .map(dep -> new Dependency(TargetKey.forPlainTarget(Label.create(dep)), dependencyType))
        .collect(toList());
  }

  private static TargetKey makeTargetKey(IntellijIdeInfo.TargetKey key) {
    return TargetKey.forGeneralTarget(Label.create(key.getLabel()), key.getAspectIdsList());
  }

  private static Dependency makeDependency(IntellijIdeInfo.Dependency dep) {
    return new Dependency(
        makeTargetKey(dep.getTarget()), makeDependencyType(dep.getDependencyType()));
  }

  private static Dependency.DependencyType makeDependencyType(
      IntellijIdeInfo.Dependency.DependencyType dependencyType) {
    switch (dependencyType) {
      case COMPILE_TIME:
        return DependencyType.COMPILE_TIME;
      case RUNTIME:
        return DependencyType.RUNTIME;
      default:
        return DependencyType.COMPILE_TIME;
    }
  }

  @Nullable
  private static ArtifactLocation getBuildFile(IntellijIdeInfo.TargetIdeInfo message) {
    if (message.hasBuildFileArtifactLocation()) {
      return makeArtifactLocation(message.getBuildFileArtifactLocation());
    }
    return null;
  }

  private static CIdeInfo makeCIdeInfo(IntellijIdeInfo.CIdeInfo cIdeInfo) {
    List<ArtifactLocation> sources = makeArtifactLocationList(cIdeInfo.getSourceList());
    List<ExecutionRootPath> transitiveIncludeDirectories =
        makeExecutionRootPathList(cIdeInfo.getTransitiveIncludeDirectoryList());
    List<ExecutionRootPath> transitiveQuoteIncludeDirectories =
        makeExecutionRootPathList(cIdeInfo.getTransitiveQuoteIncludeDirectoryList());
    List<ExecutionRootPath> transitiveSystemIncludeDirectories =
        makeExecutionRootPathList(cIdeInfo.getTransitiveSystemIncludeDirectoryList());
    List<String> coptDefines;
    List<ExecutionRootPath> coptIncludeDirectories;
    if (cIdeInfo.getTargetCoptList().isEmpty()) {
      coptDefines = ImmutableList.of();
      coptIncludeDirectories = ImmutableList.of();
    } else {
      UnfilteredCompilerOptions compilerOptions =
          UnfilteredCompilerOptions.builder()
              .registerSingleOrSplitOption("-D")
              .registerSingleOrSplitOption("-I")
              .build(cIdeInfo.getTargetCoptList());
      coptDefines = compilerOptions.getExtractedOptionValues("-D");
      coptIncludeDirectories =
          makeExecutionRootPathList(compilerOptions.getExtractedOptionValues("-I"));
    }

    CIdeInfo.Builder builder =
        CIdeInfo.builder()
            .addSources(sources)
            .addLocalDefines(coptDefines)
            .addLocalIncludeDirectories(coptIncludeDirectories)
            .addTransitiveIncludeDirectories(transitiveIncludeDirectories)
            .addTransitiveQuoteIncludeDirectories(transitiveQuoteIncludeDirectories)
            .addTransitiveDefines(cIdeInfo.getTransitiveDefineList())
            .addTransitiveSystemIncludeDirectories(transitiveSystemIncludeDirectories);

    return builder.build();
  }

  private static List<ExecutionRootPath> makeExecutionRootPathList(Iterable<String> relativePaths) {
    List<ExecutionRootPath> workspacePaths = Lists.newArrayList();
    for (String relativePath : relativePaths) {
      workspacePaths.add(new ExecutionRootPath(relativePath));
    }
    return workspacePaths;
  }

  private static CToolchainIdeInfo makeCToolchainIdeInfo(
      IntellijIdeInfo.CToolchainIdeInfo cToolchainIdeInfo) {
    Collection<ExecutionRootPath> builtInIncludeDirectories =
        makeExecutionRootPathList(cToolchainIdeInfo.getBuiltInIncludeDirectoryList());
    ExecutionRootPath cppExecutable = new ExecutionRootPath(cToolchainIdeInfo.getCppExecutable());
    ExecutionRootPath preprocessorExecutable =
        new ExecutionRootPath(cToolchainIdeInfo.getPreprocessorExecutable());

    UnfilteredCompilerOptions compilerOptions =
        UnfilteredCompilerOptions.builder()
            .registerSingleOrSplitOption("-isystem")
            .build(cToolchainIdeInfo.getUnfilteredCompilerOptionList());

    CToolchainIdeInfo.Builder builder =
        CToolchainIdeInfo.builder()
            .addBaseCompilerOptions(cToolchainIdeInfo.getBaseCompilerOptionList())
            .addCCompilerOptions(cToolchainIdeInfo.getCOptionList())
            .addCppCompilerOptions(cToolchainIdeInfo.getCppOptionList())
            .addLinkOptions(cToolchainIdeInfo.getLinkOptionList())
            .addBuiltInIncludeDirectories(builtInIncludeDirectories)
            .setCppExecutable(cppExecutable)
            .setPreprocessorExecutable(preprocessorExecutable)
            .setTargetName(cToolchainIdeInfo.getTargetName())
            .addUnfilteredCompilerOptions(compilerOptions.getUninterpretedOptions())
            .addUnfilteredToolchainSystemIncludes(
                makeExecutionRootPathList(compilerOptions.getExtractedOptionValues("-isystem")));

    return builder.build();
  }

  private static JavaIdeInfo makeJavaIdeInfo(IntellijIdeInfo.JavaIdeInfo javaIdeInfo) {
    return new JavaIdeInfo(
        makeLibraryArtifactList(javaIdeInfo.getJarsList()),
        makeLibraryArtifactList(javaIdeInfo.getGeneratedJarsList()),
        javaIdeInfo.hasFilteredGenJar()
            ? makeLibraryArtifact(javaIdeInfo.getFilteredGenJar())
            : null,
        javaIdeInfo.hasPackageManifest()
            ? makeArtifactLocation(javaIdeInfo.getPackageManifest())
            : null,
        javaIdeInfo.hasJdeps() ? makeArtifactLocation(javaIdeInfo.getJdeps()) : null,
        Strings.emptyToNull(javaIdeInfo.getMainClass()));
  }

  private static AndroidIdeInfo makeAndroidIdeInfo(IntellijIdeInfo.AndroidIdeInfo androidIdeInfo) {
    return new AndroidIdeInfo(
        makeArtifactLocationList(androidIdeInfo.getResourcesList()),
        androidIdeInfo.getJavaPackage(),
        androidIdeInfo.getGenerateResourceClass(),
        androidIdeInfo.hasManifest() ? makeArtifactLocation(androidIdeInfo.getManifest()) : null,
        androidIdeInfo.hasIdlJar() ? makeLibraryArtifact(androidIdeInfo.getIdlJar()) : null,
        androidIdeInfo.hasResourceJar()
            ? makeLibraryArtifact(androidIdeInfo.getResourceJar())
            : null,
        androidIdeInfo.getHasIdlSources(),
        !Strings.isNullOrEmpty(androidIdeInfo.getLegacyResources())
            ? Label.create(androidIdeInfo.getLegacyResources())
            : null);
  }

  private static AndroidSdkIdeInfo makeAndroidSdkIdeInfo(
      IntellijIdeInfo.AndroidSdkIdeInfo androidSdkIdeInfo) {
    return new AndroidSdkIdeInfo(makeArtifactLocation(androidSdkIdeInfo.getAndroidJar()));
  }

  private static PyIdeInfo makePyIdeInfo(IntellijIdeInfo.PyIdeInfo info) {
    return PyIdeInfo.builder().addSources(makeArtifactLocationList(info.getSourcesList())).build();
  }

  private static TestIdeInfo makeTestIdeInfo(IntellijIdeInfo.TestInfo testInfo) {
    String size = testInfo.getSize();
    TestIdeInfo.TestSize testSize = TestIdeInfo.DEFAULT_RULE_TEST_SIZE;
    if (!Strings.isNullOrEmpty(size)) {
      switch (size) {
        case "small":
          testSize = TestIdeInfo.TestSize.SMALL;
          break;
        case "medium":
          testSize = TestIdeInfo.TestSize.MEDIUM;
          break;
        case "large":
          testSize = TestIdeInfo.TestSize.LARGE;
          break;
        case "enormous":
          testSize = TestIdeInfo.TestSize.ENORMOUS;
          break;
        default:
          break;
      }
    }
    return new TestIdeInfo(testSize);
  }

  private static ProtoLibraryLegacyInfo makeProtoLibraryLegacyInfo(
      IntellijIdeInfo.ProtoLibraryLegacyJavaIdeInfo protoLibraryLegacyJavaIdeInfo) {
    final ProtoLibraryLegacyInfo.ApiFlavor apiFlavor;
    if (protoLibraryLegacyJavaIdeInfo.getApiVersion() == 1) {
      apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.VERSION_1;
    } else {
      switch (protoLibraryLegacyJavaIdeInfo.getApiFlavor()) {
        case MUTABLE:
          apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.MUTABLE;
          break;
        case IMMUTABLE:
          apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.IMMUTABLE;
          break;
        case BOTH:
          apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.BOTH;
          break;
        default:
          apiFlavor = ProtoLibraryLegacyInfo.ApiFlavor.NONE;
          break;
      }
    }
    return new ProtoLibraryLegacyInfo(
        apiFlavor,
        makeLibraryArtifactList(protoLibraryLegacyJavaIdeInfo.getJars1List()),
        makeLibraryArtifactList(protoLibraryLegacyJavaIdeInfo.getJarsMutableList()),
        makeLibraryArtifactList(protoLibraryLegacyJavaIdeInfo.getJarsImmutableList()));
  }

  private static JavaToolchainIdeInfo makeJavaToolchainIdeInfo(
      IntellijIdeInfo.JavaToolchainIdeInfo javaToolchainIdeInfo) {
    return new JavaToolchainIdeInfo(
        javaToolchainIdeInfo.getSourceVersion(), javaToolchainIdeInfo.getTargetVersion());
  }

  private static Collection<LibraryArtifact> makeLibraryArtifactList(
      List<IntellijIdeInfo.LibraryArtifact> jarsList) {
    ImmutableList.Builder<LibraryArtifact> builder = ImmutableList.builder();
    for (IntellijIdeInfo.LibraryArtifact libraryArtifact : jarsList) {
      LibraryArtifact lib = makeLibraryArtifact(libraryArtifact);
      if (lib != null) {
        builder.add(lib);
      }
    }
    return builder.build();
  }

  @Nullable
  private static LibraryArtifact makeLibraryArtifact(
      IntellijIdeInfo.LibraryArtifact libraryArtifact) {
    ArtifactLocation classJar =
        libraryArtifact.hasJar() ? makeArtifactLocation(libraryArtifact.getJar()) : null;
    ArtifactLocation iJar =
        libraryArtifact.hasInterfaceJar()
            ? makeArtifactLocation(libraryArtifact.getInterfaceJar())
            : null;
    ArtifactLocation sourceJar =
        libraryArtifact.hasSourceJar()
            ? makeArtifactLocation(libraryArtifact.getSourceJar())
            : null;
    if (iJar == null && classJar == null) {
      // Failed to find ArtifactLocation file --
      // presumably because it was removed from file system since blaze build
      return null;
    }
    return new LibraryArtifact(iJar, classJar, sourceJar);
  }

  private static List<ArtifactLocation> makeArtifactLocationList(
      List<IntellijIdeInfo.ArtifactLocation> sourcesList) {
    ImmutableList.Builder<ArtifactLocation> builder = ImmutableList.builder();
    for (IntellijIdeInfo.ArtifactLocation pbArtifactLocation : sourcesList) {
      ArtifactLocation loc = makeArtifactLocation(pbArtifactLocation);
      if (loc != null) {
        builder.add(loc);
      }
    }
    return builder.build();
  }

  @VisibleForTesting
  @Nullable
  public static ArtifactLocation makeArtifactLocation(
      @Nullable IntellijIdeInfo.ArtifactLocation location) {
    if (location == null) {
      return null;
    }
    String relativePath = location.getRelativePath();
    String rootExecutionPathFragment = location.getRootExecutionPathFragment();
    if (!location.getIsNewExternalVersion() && location.getIsExternal()) {
      // fix up incorrect paths created with older aspect version
      // Note: bazel always uses the '/' separator here, even on windows.
      List<String> components = StringUtil.split(relativePath, "/");
      if (components.size() > 2) {
        relativePath = Joiner.on('/').join(components.subList(2, components.size()));
        String prefix = components.get(0) + "/" + components.get(1);
        rootExecutionPathFragment =
            rootExecutionPathFragment.isEmpty() ? prefix : rootExecutionPathFragment + "/" + prefix;
      }
    }
    return ArtifactLocation.builder()
        .setRootExecutionPathFragment(rootExecutionPathFragment)
        .setRelativePath(relativePath)
        .setIsSource(location.getIsSource())
        .setIsExternal(location.getIsExternal())
        .build();
  }

  @Nullable
  static Kind getKind(IntellijIdeInfo.TargetIdeInfo message) {
    String kindString = message.getKindString();
    if (!Strings.isNullOrEmpty(kindString)) {
      return Kind.fromString(kindString);
    }
    return null;
  }

  static TargetKey getKey(IntellijIdeInfo.TargetIdeInfo message) {
    return message.hasKey()
        ? makeTargetKey(message.getKey())
        : TargetKey.forPlainTarget(Label.create(message.getLabel()));
  }
}
