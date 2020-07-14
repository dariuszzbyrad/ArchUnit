package com.tngtech.archunit.testutil.assertion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import com.tngtech.archunit.core.domain.JavaCodeUnit;

import static com.tngtech.archunit.testutil.Assertions.assertThat;
import static com.tngtech.archunit.testutil.Assertions.assertThatType;

public class JavaCodeUnitAssertion<T extends JavaCodeUnit, SELF extends JavaCodeUnitAssertion<T, SELF>>
        extends JavaMemberAssertion<T, SELF> {

    public JavaCodeUnitAssertion(T javaMember, Class<SELF> selfType) {
        super(javaMember, selfType);
    }

    public void isEquivalentTo(Method method) {
        super.isEquivalentTo(method);
        assertThat(actual.getRawParameterTypes()).matches(method.getParameterTypes());
        assertThatType(actual.getRawReturnType()).matches(method.getReturnType());
    }

    public void isEquivalentTo(Constructor<?> constructor) {
        super.isEquivalentTo(constructor);
        assertThat(actual.getRawParameterTypes()).matches(constructor.getParameterTypes());
        assertThatType(actual.getRawReturnType()).matches(void.class);
    }
}
