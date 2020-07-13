package com.tngtech.archunit.core.domain;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.Test;

import static com.tngtech.archunit.testutil.Assertions.assertThatType;
import static org.assertj.core.api.Assertions.assertThat;

public class JavaTypeTest {

    @Test
    public void erased_non_generic_class_is_the_class_itself() {
        JavaType type = new ClassFileImporter().importClass(SimpleClass.class);

        assertThat(type.toErasure()).isEqualTo(type);
    }

    @Test
    public void erased_unbound_type_variable_is_java_lang_Object() {
        JavaType type = new ClassFileImporter().importClass(ClassWithUnboundTypeParameter.class).getTypeParameters().get(0);

        assertThatType(type.toErasure()).matches(Object.class);
    }

    @Test
    public void erased_type_variable_bound_by_single_class_is_this_class() {
        JavaType type = new ClassFileImporter().importClass(ClassWithBoundTypeParameterWithSingleClassBound.class).getTypeParameters().get(0);

        assertThatType(type.toErasure()).matches(Serializable.class);
    }

    @Test
    public void erased_type_variable_bound_by_single_generic_class_is_the_erasure_of_this_class() {
        JavaType type = new ClassFileImporter().importClass(ClassWithBoundTypeParameterWithSingleGenericClassBound.class).getTypeParameters().get(0);

        assertThatType(type.toErasure()).matches(List.class);
    }

    @Test
    public void erased_type_variable_bound_by_multiple_generic_classes_and_interfaces_is_the_erasure_of_the_leftmost_bound() {
        JavaType type = new ClassFileImporter().importClass(ClassWithBoundTypeParameterWithMultipleGenericClassAndInterfaceBounds.class).getTypeParameters().get(0);

        assertThatType(type.toErasure()).matches(HashMap.class);
    }

    @Test
    public void erased_type_parameter_of_concrete_array_type_is_array_type() {
        List<JavaTypeVariable> typeParameters = new ClassFileImporter().importClass(ClassWithBoundTypeParameterWithSingleGenericArrayBound.class).getTypeParameters();

        assertThatType(getTypeArgumentOfFirstBound(typeParameters.get(0)).toErasure()).matches(Object[].class);
        assertThatType(getTypeArgumentOfFirstBound(typeParameters.get(1)).toErasure()).matches(String[][].class);
        assertThatType(getTypeArgumentOfFirstBound(typeParameters.get(2)).toErasure()).matches(List[][][].class);
    }

    private static JavaType getTypeArgumentOfFirstBound(JavaTypeVariable typeParameter) {
        JavaParameterizedType firstBound = (JavaParameterizedType) typeParameter.getBounds().get(0);
        return firstBound.getActualTypeArguments().get(0);
    }

    private static class SimpleClass {
    }

    @SuppressWarnings("unused")
    private static class ClassWithUnboundTypeParameter<T> {
    }

    @SuppressWarnings("unused")
    private static class ClassWithBoundTypeParameterWithSingleClassBound<T extends Serializable> {
    }

    @SuppressWarnings("unused")
    private static class ClassWithBoundTypeParameterWithSingleGenericClassBound<T extends List<String>> {
    }

    @SuppressWarnings("unused")
    private static class ClassWithBoundTypeParameterWithSingleGenericArrayBound<T extends List<Object[]>, U extends List<String[][]>, V extends List<List<?>[][][]>> {
    }

    @SuppressWarnings("unused")
    private static class ClassWithBoundTypeParameterWithMultipleGenericClassAndInterfaceBounds<T extends HashMap<String, String> & Iterable<String> & Serializable> {
    }
}
