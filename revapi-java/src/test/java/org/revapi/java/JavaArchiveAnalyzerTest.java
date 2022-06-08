/*
 * Copyright 2014-2021 Lukas Krejci
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.revapi.java;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.revapi.java.ExpectedValues.dependingOnJavaVersion;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.revapi.API;
import org.revapi.Reference;
import org.revapi.TreeFilter;
import org.revapi.java.model.JavaElementForest;
import org.revapi.java.model.MethodElement;
import org.revapi.java.model.MethodParameterElement;
import org.revapi.java.model.TypeElement;
import org.revapi.java.spi.JavaElement;
import org.revapi.java.spi.JavaTypeElement;
import org.revapi.java.spi.UseSite;

/**
 * @author Lukas Krejci
 * 
 * @since 0.1
 */
public class JavaArchiveAnalyzerTest extends AbstractJavaElementAnalyzerTest {
    private JavaApiAnalyzer apiAnalyzer;

    @Before
    public void setup() {
        apiAnalyzer = new JavaApiAnalyzer();
    }

    @Test
    public void testSimple() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("test.jar", "misc/A.java", "misc/B.java", "misc/C.java",
                "misc/D.java", "misc/I.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(apiAnalyzer,
                new API(Arrays.asList(new ShrinkwrapArchive(archive.archive)), null), emptyList(),
                Executors.newSingleThreadExecutor(), null, false, null);

        try {
            JavaElementForest forest = analyzer.analyze(TreeFilter.matchAndDescend());

            Assert.assertEquals((int) dependingOnJavaVersion(8, 7, 9, 6), forest.getRoots().size());
        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testWithSupplementary() throws Exception {
        ArchiveAndCompilationPath compRes = createCompiledJar("a.jar", "v1/supplementary/a/A.java",
                "v1/supplementary/b/B.java", "v1/supplementary/a/C.java");

        JavaArchive api = ShrinkWrap.create(JavaArchive.class, "api.jar")
                .addAsResource(compRes.compilationPath.resolve("A.class").toFile(), "A.class");
        JavaArchive sup = ShrinkWrap.create(JavaArchive.class, "sup.jar")
                .addAsResource(compRes.compilationPath.resolve("B.class").toFile(), "B.class")
                .addAsResource(compRes.compilationPath.resolve("B$T$1.class").toFile(), "B$T$1.class")
                .addAsResource(compRes.compilationPath.resolve("B$T$1$TT$1.class").toFile(), "B$T$1$TT$1.class")
                .addAsResource(compRes.compilationPath.resolve("B$T$2.class").toFile(), "B$T$2.class")
                .addAsResource(compRes.compilationPath.resolve("C.class").toFile(), "C.class")
                .addAsResource(compRes.compilationPath.resolve("B$UsedByIgnoredClass.class").toFile(),
                        "B$UsedByIgnoredClass.class")
                .addAsResource(compRes.compilationPath.resolve("A$PrivateEnum.class").toFile(), "A$PrivateEnum.class");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(apiAnalyzer,
                new API(Arrays.asList(new ShrinkwrapArchive(api)), Arrays.asList(new ShrinkwrapArchive(sup))),
                emptyList(), Executors.newSingleThreadExecutor(), null, false, null);

        try {
            JavaElementForest forest = analyzer.analyze(TreeFilter.matchAndDescend());
            analyzer.prune(forest);

            Assert.assertEquals(3, forest.getRoots().size());

            Iterator<JavaElement> roots = forest.getRoots().iterator();

            TypeElement A = roots.next().as(TypeElement.class);
            TypeElement B_T$1 = roots.next().as(TypeElement.class);
            TypeElement B_T$2 = roots.next().as(TypeElement.class);

            Assert.assertEquals("A", A.getCanonicalName());
            Assert.assertEquals("A", A.getDeclaringElement().getQualifiedName().toString());
            Assert.assertEquals("B.T$1", B_T$1.getCanonicalName());
            Assert.assertEquals("B.T$1", B_T$1.getDeclaringElement().getQualifiedName().toString());
            Assert.assertEquals("B.T$2", B_T$2.getCanonicalName());
            Assert.assertEquals("B.T$2", B_T$2.getDeclaringElement().getQualifiedName().toString());
        } finally {
            deleteDir(compRes.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testPreventRecursionWhenConstructingInheritedMembers() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("a.jar", "misc/MemberInheritsOwner.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(apiAnalyzer,
                new API(Arrays.asList(new ShrinkwrapArchive(archive.archive)), null), emptyList(),
                Executors.newSingleThreadExecutor(), null, false, null);

        try {
            JavaElementForest forest = analyzer.analyze(TreeFilter.matchAndDescend());

            forest.getRoots();

            Assert.assertEquals((int) dependingOnJavaVersion(8, 2, 9, 1), forest.getRoots().size());

            Predicate<JavaElement> findMethod = c -> "method void MemberInheritsOwner::method()"
                    .equals(c.getFullHumanReadableString());
            Predicate<JavaElement> findMember1 = c -> "interface MemberInheritsOwner.Member1"
                    .equals(c.getFullHumanReadableString());
            Predicate<JavaElement> findMember2 = c -> "interface MemberInheritsOwner.Member2"
                    .equals(c.getFullHumanReadableString());

            JavaTypeElement root = forest.getRoots().stream().filter(r -> ((JavaTypeElement) r).getDeclaringElement()
                    .getQualifiedName().contentEquals("MemberInheritsOwner")).findFirst().get()
                    .as(JavaTypeElement.class);
            Assert.assertEquals(3 + 11, root.getChildren().size()); // 11 is the number of methods on java.lang.Object
            Assert.assertTrue(root.getChildren().stream().anyMatch(findMethod));
            Assert.assertTrue(root.getChildren().stream().anyMatch(findMember1));
            Assert.assertTrue(root.getChildren().stream().anyMatch(findMember2));

            Assert.assertEquals(1 + 11,
                    root.getChildren().stream().filter(findMember1).findFirst().get().getChildren().size());
            Assert.assertEquals(1 + 11,
                    root.getChildren().stream().filter(findMember2).findFirst().get().getChildren().size());

        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testTypeParametersDragIntoAPI() throws Exception {
        ArchiveAndCompilationPath compRes = createCompiledJar("a.jar", "misc/Generics.java",
                "misc/GenericsParams.java");

        JavaArchive api = ShrinkWrap.create(JavaArchive.class, "api.jar")
                .addAsResource(compRes.compilationPath.resolve("Generics.class").toFile(), "Generics.class");
        JavaArchive sup = ShrinkWrap.create(JavaArchive.class, "sup.jar")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams.class").toFile(), "GenericsParams.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$TypeParam.class").toFile(),
                        "GenericsParams$TypeParam.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$ExtendsBound.class").toFile(),
                        "GenericsParams$ExtendsBound.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$SuperBound.class").toFile(),
                        "GenericsParams$SuperBound.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$TypeVar.class").toFile(),
                        "GenericsParams$TypeVar.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$TypeVarIface.class").toFile(),
                        "GenericsParams$TypeVarIface.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$TypeVarImpl.class").toFile(),
                        "GenericsParams$TypeVarImpl.class")
                .addAsResource(compRes.compilationPath.resolve("GenericsParams$Unused.class").toFile(),
                        "GenericsParams$Unused.class");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(apiAnalyzer,
                new API(Arrays.asList(new ShrinkwrapArchive(api)), Arrays.asList(new ShrinkwrapArchive(sup))),
                emptyList(), Executors.newSingleThreadExecutor(), null, false, null);

        try {
            JavaElementForest forest = analyzer.analyze(TreeFilter.matchAndDescend());
            analyzer.prune(forest);

            Set<TypeElement> roots = forest.getRoots().stream().map(n -> n.as(TypeElement.class)).collect(toSet());

            Assert.assertEquals(7, roots.size());
            Assert.assertTrue(roots.stream().anyMatch(hasName(
                    "class Generics<T extends GenericsParams.TypeVar & GenericsParams.TypeVarIface, U extends Generics<GenericsParams.TypeVarImpl, ?>>")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.ExtendsBound")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.SuperBound")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.TypeParam")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.TypeVar")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("interface GenericsParams.TypeVarIface")));
            Assert.assertTrue(roots.stream().anyMatch(hasName("class GenericsParams.TypeVarImpl")));
            Assert.assertFalse(roots.stream().anyMatch(hasName("class GenericsParams.Unused")));
        } finally {
            deleteDir(compRes.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testInheritedMembersResetArchiveToThatOfInheritingClass() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("c.jar", "misc/C.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(apiAnalyzer,
                new API(Arrays.asList(new ShrinkwrapArchive(archive.archive)), null), emptyList(),
                Executors.newSingleThreadExecutor(), null, false, null);

        try {
            JavaElementForest forest = analyzer.analyze(TreeFilter.matchAndDescend());

            forest.getRoots();

            Assert.assertEquals((int) dependingOnJavaVersion(8, 2, 9, 1), forest.getRoots().size());

            JavaTypeElement C = forest.getRoots().stream()
                    .filter(r -> ((JavaTypeElement) r).getDeclaringElement().getQualifiedName().contentEquals("misc.C"))
                    .findFirst().get().as(JavaTypeElement.class);

            Assert.assertTrue(C.getChildren().stream().allMatch(e -> Objects.equals(e.getArchive(), C.getArchive())));
        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    public void testInterfaceOverloadingObjectMethodUsesItsDeclaration() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("i.jar", "misc/InterfaceOverloadingObjectMethods.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(apiAnalyzer,
                new API(singletonList(new ShrinkwrapArchive(archive.archive)), null), emptyList(),
                Executors.newSingleThreadExecutor(), null, false, null);

        try {
            JavaElementForest forest = analyzer.analyze(TreeFilter.matchAndDescend());

            forest.getRoots();

            Assert.assertEquals((int) dependingOnJavaVersion(8, 2, 9, 1), forest.getRoots().size());

            JavaTypeElement I = forest.getRoots().stream()
                    .filter(r -> ((JavaTypeElement) r).getDeclaringElement().getQualifiedName()
                            .contentEquals("InterfaceOverloadingObjectMethods"))
                    .findFirst().get().as(JavaTypeElement.class);

            Function<String, MethodElement> byName = name -> I.getChildren().stream().map(e -> (MethodElement) e)
                    .filter(m -> m.getDeclaringElement().getSimpleName().contentEquals(name)).findFirst().get();

            MethodElement hashCode = byName.apply("hashCode");
            MethodElement toString = byName.apply("toString");
            MethodElement clone = byName.apply("clone");

            assertTrue(hashCode.isInherited());
            assertFalse(toString.isInherited());
            assertFalse(clone.isInherited());

            Set<UseSite> useSites = ((TypeElement) I).getUseSites();
            assertEquals(1, useSites.size());

            UseSite use = useSites.iterator().next();

            assertEquals(use.getType(), UseSite.Type.RETURN_TYPE);
            assertSame(clone, use.getElement());
        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testAnnotatedMethodParametersCorrectlyReportedAsUseSites() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("i.jar", "misc/AnnotatedMethodParameter.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(apiAnalyzer,
                new API(singletonList(new ShrinkwrapArchive(archive.archive)), null), emptyList(),
                Executors.newSingleThreadExecutor(), null, false, null);
        try {
            JavaElementForest forest = analyzer.analyze(TreeFilter.matchAndDescend());
            analyzer.prune(forest);

            forest.getRoots();

            Assert.assertEquals(1, forest.getRoots().size());

            JavaTypeElement clazz = forest.getRoots().first().as(JavaTypeElement.class);
            MethodElement method = clazz.stream(MethodElement.class, false)
                    .filter(m -> m.getDeclaringElement().getSimpleName().contentEquals("method")).findFirst().get();
            MethodParameterElement param = (MethodParameterElement) method.getChildren().first();

            JavaTypeElement anno = clazz.stream(JavaTypeElement.class, false)
                    .filter(t -> t.getDeclaringElement().getSimpleName().contentEquals("Anno")).findFirst().get();

            Set<Reference<JavaElement>> useSites = anno.getReferencingElements();

            assertEquals(2, useSites.size());
            assertTrue(useSites.stream().anyMatch(site -> {
                if (UseSite.Type.ANNOTATES == site.getType()) {
                    assertSame(param, site.getElement());
                    return true;
                }
                return false;
            }));
            assertTrue(useSites.stream().anyMatch(site -> {
                if (UseSite.Type.CONTAINS == site.getType()) {
                    assertSame(clazz, site.getElement());
                    return true;
                }
                return false;
            }));
        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    @Test
    public void testProtectedMembersOfFinalClassNotInApi() throws Exception {
        ArchiveAndCompilationPath archive = createCompiledJar("i.jar", "misc/ProtectedMembersOfFinalClass.java");

        JavaArchiveAnalyzer analyzer = new JavaArchiveAnalyzer(apiAnalyzer,
                new API(singletonList(new ShrinkwrapArchive(archive.archive)), null), emptyList(),
                Executors.newSingleThreadExecutor(), null, false, null);

        try {
            JavaElementForest forest = analyzer.analyze(TreeFilter.matchAndDescend());
            analyzer.prune(forest);

            forest.getRoots();

            Assert.assertEquals(1, forest.getRoots().size());

            JavaTypeElement Root = forest.getRoots().iterator().next().as(JavaTypeElement.class);

            JavaTypeElement Inherited = Root.stream(JavaTypeElement.class, false)
                    .filter(c -> "Inherited".contentEquals(c.getDeclaringElement().getSimpleName())).findFirst().get();
            JavaTypeElement X = Inherited.stream(JavaTypeElement.class, false)
                    .filter(m -> "X".contentEquals(m.getDeclaringElement().getSimpleName())).findFirst().get();

            assertFalse(X.isInAPI());
            assertFalse(X.isInApiThroughUse());
        } finally {
            deleteDir(archive.compilationPath);
            analyzer.getCompilationValve().removeCompiledResults();
        }
    }

    private Predicate<TypeElement> hasName(String name) {
        return t -> name.equals(t.getFullHumanReadableString());
    }
}
