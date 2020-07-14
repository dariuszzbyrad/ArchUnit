package com.tngtech.archunit.core.domain;

import java.io.File;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.base.Predicate;
import com.tngtech.archunit.core.domain.JavaPackage.ClassVisitor;
import com.tngtech.archunit.core.domain.JavaPackage.PackageVisitor;
import com.tngtech.archunit.core.domain.packageexamples.annotated.PackageLevelAnnotation;
import com.tngtech.archunit.core.domain.packageexamples.first.First1;
import com.tngtech.archunit.core.domain.packageexamples.first.First2;
import com.tngtech.archunit.core.domain.packageexamples.second.ClassDependingOnOtherSecondClass;
import com.tngtech.archunit.core.domain.packageexamples.second.Second1;
import com.tngtech.archunit.core.domain.packageexamples.second.sub.SecondSub1;
import com.tngtech.archunit.core.domain.packageexamples.third.sub.ThirdSub1;
import com.tngtech.archunit.core.domain.packageexamples.unrelated.AnyClass;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.assertj.core.api.ThrowableAssert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.tngtech.archunit.core.domain.JavaClass.Functions.GET_SIMPLE_NAME;
import static com.tngtech.archunit.core.domain.JavaPackage.Functions.GET_CLASSES;
import static com.tngtech.archunit.core.domain.JavaPackage.Functions.GET_RELATIVE_NAME;
import static com.tngtech.archunit.core.domain.JavaPackage.Functions.GET_SUB_PACKAGES;
import static com.tngtech.archunit.testutil.Assertions.assertThat;
import static com.tngtech.archunit.testutil.Assertions.assertThatDependencies;
import static com.tngtech.archunit.testutil.Assertions.assertThatPackages;
import static com.tngtech.archunit.testutil.Assertions.assertThatType;
import static com.tngtech.archunit.testutil.Assertions.assertThatTypes;
import static java.util.regex.Pattern.quote;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class JavaPackageTest {
    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void creates_default_package() {
        JavaPackage defaultPackage = importDefaultPackage();

        assertThat(defaultPackage.getName()).isEmpty();
        assertThat(defaultPackage.getRelativeName()).isEmpty();
        assertThat(defaultPackage.containsPackage("any")).isFalse();
        assertThat(defaultPackage.containsClass(Object.class)).isFalse();
        assertThat(defaultPackage.containsClassWithFullyQualifiedName("some.SomeClass")).isFalse();
        assertThat(defaultPackage.containsClassWithSimpleName("SomeClass")).isFalse();
    }

    @Test
    public void rejects_retrieving_non_existing_subpackages() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("does not contain");
        thrown.expectMessage("some.pkg");

        importDefaultPackage().getPackage("some.pkg");
    }

    @Test
    public void rejects_retrieving_non_existing_classes_by_class_object() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("does not contain");
        thrown.expectMessage(Object.class.getName());

        importDefaultPackage().getClass(Object.class);
    }

    @Test
    public void rejects_retrieving_non_existing_classes_by_fully_qualified_name() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("does not contain");
        thrown.expectMessage(Object.class.getName());

        importDefaultPackage().getClassWithFullyQualifiedName(Object.class.getName());
    }

    @Test
    public void rejects_retrieving_non_existing_classes_by_simple_name() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("does not contain");
        thrown.expectMessage(Object.class.getSimpleName());

        importDefaultPackage().getClassWithSimpleName(Object.class.getSimpleName());
    }

    @Test
    public void creates_single_package() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, String.class);

        assertThat(defaultPackage.containsPackage("java.lang"))
                .as("default package contains 'java.lang'").isTrue();

        JavaPackage javaPackage = defaultPackage.getPackage("java.lang");

        assertThat(javaPackage.getName()).isEqualTo("java.lang");
        assertThat(javaPackage.getDescription()).isEqualTo("Package <java.lang>");
        assertThat(javaPackage.getRelativeName()).isEqualTo("lang");
        assertThatTypes(javaPackage.getClasses()).contain(Object.class, String.class);
    }

    @Test
    public void keeps_packages_unique() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class);

        JavaPackage javaLang = defaultPackage.getPackage("java.lang");
        assertThat(javaLang).isSameAs(javaLang.getClass(Object.class).getPackage());
    }

    @Test
    public void retrieves_class_by_class_object() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, String.class);

        assertThat(defaultPackage.getPackage("java").containsClass(Object.class))
                .as("package 'java' contains java.lang.Object").isFalse();

        JavaPackage javaPackage = defaultPackage.getPackage("java.lang");

        assertThat(javaPackage.containsClass(Object.class))
                .as("java.lang.Object is reported contained by class object").isTrue();
        assertThat(javaPackage.getClass(Object.class).isEquivalentTo(Object.class))
                .as("java.lang.Object is returned by class object").isTrue();
    }

    @Test
    public void retrieves_class_by_fully_qualified_name() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, String.class);

        assertThat(defaultPackage.getPackage("java").containsClassWithFullyQualifiedName(Object.class.getName()))
                .as("package 'java' contains java.lang.Object").isFalse();

        JavaPackage javaPackage = defaultPackage.getPackage("java.lang");

        assertThat(javaPackage.containsClassWithFullyQualifiedName(Object.class.getName()))
                .as("java.lang.Object is reported contained by fully qualified name").isTrue();
        assertThat(javaPackage.getClassWithFullyQualifiedName(Object.class.getName()).isEquivalentTo(Object.class))
                .as("java.lang.Object is returned by fully qualified name").isTrue();
    }

    @Test
    public void retrieves_class_by_simple_class_name() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, String.class);

        assertThat(defaultPackage.getPackage("java").containsClassWithSimpleName(Object.class.getSimpleName()))
                .as("package 'java' contains java.lang.Object").isFalse();

        JavaPackage javaPackage = defaultPackage.getPackage("java.lang");

        assertThat(javaPackage.containsClassWithSimpleName(Object.class.getSimpleName()))
                .as("java.lang.Object is reported contained by simple name").isTrue();
        assertThat(javaPackage.getClassWithSimpleName(Object.class.getSimpleName()).isEquivalentTo(Object.class))
                .as("java.lang.Object is returned by simple name").isTrue();
    }

    @Test
    public void creates_empty_middle_packages() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class);

        assertThat(defaultPackage.containsPackage("java")).as("default package contains 'java'").isTrue();

        JavaPackage java = defaultPackage.getPackage("java");
        assertThat(java.containsPackage("lang")).isTrue();
        assertThat(java.getPackage("lang").getName()).isEqualTo("java.lang");
    }

    @Test
    public void creates_parent_packages() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class);
        assertThat(defaultPackage.getParent()).as("parent of default package").isAbsent();

        JavaPackage javaLang = defaultPackage.getPackage("java.lang");

        JavaPackage java = javaLang.getParent().get();
        assertThat(java.getName()).isEqualTo("java");
        assertThat(java.containsPackage("lang")).as("package 'java' contains 'lang'").isTrue();
    }

    @Test
    public void iterates_sub_packages() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, Collection.class, File.class, Security.class);

        JavaPackage java = defaultPackage.getPackage("java");

        assertThatPackages(java.getSubPackages()).containRelativeNames("lang", "util", "io", "security");
        assertThatPackages(java.getSubPackages()).containNames("java.lang", "java.util", "java.io", "java.security");
    }

    @Test
    public void iterates_all_classes() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, String.class, Annotation.class, Field.class, Security.class);

        JavaPackage javaLang = defaultPackage.getPackage("java.lang");

        assertThatTypes(javaLang.getAllClasses()).contain(Object.class, String.class, Annotation.class, Field.class);
    }

    @Test
    public void iterates_all_sub_packages() {
        JavaPackage defaultPackage = importDefaultPackage(
                Object.class, Annotation.class, Collection.class, BlockingQueue.class, Security.class, getClass());

        JavaPackage java = defaultPackage.getPackage("java");

        assertThatPackages(java.getAllSubPackages()).containPackagesOf(
                Object.class, Annotation.class, Collection.class, BlockingQueue.class, Security.class);
    }

    @Test
    public void visits_classes() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, String.class, File.class, Serializable.class, Security.class);

        final List<JavaClass> visitedClasses = new ArrayList<>();
        defaultPackage.accept(startsWith("S"), new ClassVisitor() {
            @Override
            public void visit(JavaClass javaClass) {
                visitedClasses.add(javaClass);
            }
        });

        assertThatTypes(visitedClasses).contain(String.class, Serializable.class, Security.class);
        for (JavaClass visitedClass : visitedClasses) {
            assertThat(visitedClass.getSimpleName()).startsWith("S");
        }
    }

    @Test
    public void visits_packages() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, Annotation.class, File.class, Security.class);

        final List<JavaPackage> visitedPackages = new ArrayList<>();
        defaultPackage.accept(nameContains(".lang"), new PackageVisitor() {
            @Override
            public void visit(JavaPackage javaPackage) {
                visitedPackages.add(javaPackage);
            }
        });

        assertThatPackages(visitedPackages).containPackagesOf(Object.class, Annotation.class);
        for (JavaPackage visitedPackage : visitedPackages) {
            assertThat(visitedPackage.getName()).contains(".lang");
        }
    }

    @Test
    public void has_class_dependencies_to_other_packages() {
        JavaPackage examplePackage = importPackage("packageexamples");

        assertThatDependencies(examplePackage.getPackage("second").getClassDependenciesFromSelf())
                .contain(Second1.class, First2.class)
                .contain(SecondSub1.class, ThirdSub1.class)
                .contain(SecondSub1.class, First1.class);

        assertThatDependencies(examplePackage.getPackage("third").getClassDependenciesFromSelf())
                .contain(ThirdSub1.class, First1.class);

        assertThatDependencies(examplePackage.getPackage("second").getClassDependenciesFromSelf())
                .doesNotContain(ClassDependingOnOtherSecondClass.class, Second1.class);

        assertThatDependencies(examplePackage.getPackage("unrelated").getClassDependenciesFromSelf())
                .containOnly(AnyClass.class, Object.class);
    }

    @Test
    public void has_class_dependencies_from_other_packages() {
        JavaPackage examplePackage = importPackage("packageexamples");

        assertThatDependencies(examplePackage.getPackage("first").getClassDependenciesToSelf())
                .contain(Second1.class, First2.class)
                .contain(ThirdSub1.class, First1.class)
                .contain(SecondSub1.class, First1.class);

        assertThatDependencies(examplePackage.getPackage("third").getClassDependenciesToSelf())
                .contain(SecondSub1.class, ThirdSub1.class);

        assertThatDependencies(examplePackage.getPackage("second").getClassDependenciesToSelf())
                .doesNotContain(ClassDependingOnOtherSecondClass.class, Second1.class);

        assertThatDependencies(examplePackage.getPackage("unrelated").getClassDependenciesToSelf())
                .isEmpty();
    }

    @Test
    public void has_package_dependencies_to_other_packages() {
        JavaPackage examplePackage = importPackage("packageexamples");

        assertThat(examplePackage.getPackage("second").getPackageDependenciesFromSelf())
                .containsOnly(
                        getRoot(examplePackage).getPackage("java.lang"),
                        examplePackage.getPackage("first"),
                        examplePackage.getPackage("third.sub"));

        assertThat(examplePackage.getPackage("third").getPackageDependenciesFromSelf())
                .containsOnly(examplePackage.getPackage("first"));

        assertThatPackages(examplePackage.getPackage("unrelated").getPackageDependenciesFromSelf())
                .containOnlyNames("java.lang");
    }

    @Test
    public void has_package_dependencies_from_other_packages() {
        JavaPackage examplePackage = importPackage("packageexamples");

        assertThat(examplePackage.getPackage("first").getPackageDependenciesToSelf())
                .containsOnly(
                        examplePackage.getPackage("second"),
                        examplePackage.getPackage("second.sub"),
                        examplePackage.getPackage("third.sub"));

        assertThat(examplePackage.getPackage("third").getPackageDependenciesToSelf())
                .containsOnly(examplePackage.getPackage("second.sub"));

        assertThat(examplePackage.getPackage("second").getPackageDependenciesToSelf())
                .doesNotContain(examplePackage.getPackage("second"));

        assertThat(examplePackage.getPackage("unrelated").getPackageDependenciesToSelf())
                .isEmpty();
    }

    @Test
    public void test_getPackageInfo() {
        JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        final JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        assertThat(annotatedPackage.getPackageInfo()).isNotNull();

        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                nonAnnotatedPackage.getPackageInfo();
            }
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(nonAnnotatedPackage.getDescription() + " does not contain a package-info.java");
    }

    @Test
    public void test_tryGetPackageInfo() {
        JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        assertThat(annotatedPackage.tryGetPackageInfo()).isPresent();
        assertThat(nonAnnotatedPackage.tryGetPackageInfo()).isAbsent();
    }

    @Test
    public void test_getAnnotations() {
        JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        JavaAnnotation<JavaPackage> annotation = getOnlyElement(annotatedPackage.getAnnotations());
        assertThatType(annotation.getRawType()).matches(PackageLevelAnnotation.class);
        assertThat(annotation.getOwner()).isEqualTo(annotatedPackage);

        assertThat(nonAnnotatedPackage.getAnnotations()).isEmpty();
    }

    @Test
    public void test_getAnnotationOfType_type() {
        final JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        final JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        assertThat(annotatedPackage.getAnnotationOfType(PackageLevelAnnotation.class)).isInstanceOf(PackageLevelAnnotation.class);

        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                annotatedPackage.getAnnotationOfType(Deprecated.class);
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(annotatedPackage.getDescription() + " is not annotated with @" + Deprecated.class.getName());

        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                nonAnnotatedPackage.getAnnotationOfType(Deprecated.class);
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(nonAnnotatedPackage.getDescription() + " is not annotated with @" + Deprecated.class.getName());
    }

    @Test
    public void test_getAnnotationOfType_typeName() {
        final JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        final JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        assertThatType(annotatedPackage.getAnnotationOfType(PackageLevelAnnotation.class.getName())
                .getRawType()).matches(PackageLevelAnnotation.class);

        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                annotatedPackage.getAnnotationOfType("not.There");
            }
        }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(annotatedPackage.getDescription() + " is not annotated with @not.There");

        assertThatThrownBy(new ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                nonAnnotatedPackage.getAnnotationOfType("not.There");
            }
        }).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(nonAnnotatedPackage.getDescription() + " is not annotated with @not.There");
    }

    @Test
    public void test_tryGetAnnotationOfType_type() {
        JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        assertThat(annotatedPackage.tryGetAnnotationOfType(PackageLevelAnnotation.class)).isPresent();
        assertThat(annotatedPackage.tryGetAnnotationOfType(Deprecated.class)).isAbsent();

        assertThat(nonAnnotatedPackage.tryGetAnnotationOfType(Deprecated.class)).isAbsent();
    }

    @Test
    public void test_tryGetAnnotationOfType_typeName() {
        JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        assertThat(annotatedPackage.tryGetAnnotationOfType(PackageLevelAnnotation.class.getName())).isPresent();
        assertThat(annotatedPackage.tryGetAnnotationOfType(Deprecated.class.getName())).isAbsent();

        assertThat(nonAnnotatedPackage.tryGetAnnotationOfType(Deprecated.class.getName())).isAbsent();
    }

    @Test
    public void test_isAnnotatedWith_type() {
        JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        assertThat(annotatedPackage.isAnnotatedWith(PackageLevelAnnotation.class)).isTrue();
        assertThat(annotatedPackage.isAnnotatedWith(Deprecated.class)).isFalse();

        assertThat(nonAnnotatedPackage.isAnnotatedWith(Deprecated.class)).isFalse();
    }

    @Test
    public void test_isAnnotatedWith_typeName() {
        JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        assertThat(annotatedPackage.isAnnotatedWith(PackageLevelAnnotation.class.getName())).isTrue();
        assertThat(annotatedPackage.isAnnotatedWith(Deprecated.class.getName())).isFalse();

        assertThat(nonAnnotatedPackage.isAnnotatedWith(Deprecated.class.getName())).isFalse();
    }

    @Test
    public void test_isAnnotatedWith_predicate() {
        JavaPackage annotatedPackage = importPackage("packageexamples.annotated");
        JavaPackage nonAnnotatedPackage = importPackage("packageexamples");

        assertThat(annotatedPackage.isAnnotatedWith(DescribedPredicate.<JavaAnnotation<?>>alwaysTrue())).isTrue();
        assertThat(annotatedPackage.isAnnotatedWith(DescribedPredicate.<JavaAnnotation<?>>alwaysFalse())).isFalse();

        assertThat(nonAnnotatedPackage.isAnnotatedWith(DescribedPredicate.<JavaAnnotation<?>>alwaysTrue())).isFalse();
        assertThat(nonAnnotatedPackage.isAnnotatedWith(DescribedPredicate.<JavaAnnotation<?>>alwaysFalse())).isFalse();
    }

    @Test
    public void function_GET_RELATIVE_NAME() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class);

        String name = GET_RELATIVE_NAME.apply(defaultPackage.getPackage("java.lang"));

        assertThat(name).isEqualTo("lang");
    }

    @Test
    public void function_GET_CLASSES() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, String.class, Collection.class);

        Iterable<JavaClass> classes = GET_CLASSES.apply(defaultPackage.getPackage("java.lang"));

        assertThatTypes(classes).contain(Object.class, String.class);
        for (JavaClass javaClass : classes) {
            assertThat(javaClass.getPackageName()).startsWith("java.lang");
        }
    }

    @Test
    public void function_GET_SUB_PACKAGES() {
        JavaPackage defaultPackage = importDefaultPackage(Object.class, Annotation.class, Field.class, Collection.class);

        Iterable<JavaPackage> packages = GET_SUB_PACKAGES.apply(defaultPackage.getPackage("java.lang"));

        assertThatPackages(packages).containPackagesOf(Annotation.class, Field.class);
    }

    private JavaPackage getRoot(JavaPackage javaPackage) {
        JavaPackage result = javaPackage;
        while (result.getParent().isPresent()) {
            result = result.getParent().get();
        }
        return result;
    }

    private Predicate<? super JavaPackage> nameContains(String infix) {
        return HasName.Predicates.nameMatching(".*" + quote(infix) + ".*");
    }

    private DescribedPredicate<JavaClass> startsWith(final String prefix) {
        return GET_SIMPLE_NAME.is(new DescribedPredicate<String>("starts with '%s'", prefix) {
            @Override
            public boolean apply(String input) {
                return input.startsWith(prefix);
            }
        });
    }

    private JavaPackage importDefaultPackage(Class<?>... classes) {
        return new ClassFileImporter().importClasses(classes).getDefaultPackage();
    }

    private JavaPackage importPackage(String subPackageName) {
        String packageName = getClass().getPackage().getName() + "." + subPackageName;
        JavaClasses classes = new ClassFileImporter().importPackages(packageName);
        return classes.getPackage(packageName);
    }
}
