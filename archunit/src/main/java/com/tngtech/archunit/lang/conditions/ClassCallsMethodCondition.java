package com.tngtech.archunit.lang.conditions;

import java.util.Collection;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.JavaCall;
import com.tngtech.archunit.core.JavaClass;

class ClassCallsMethodCondition extends AnyAttributeMatchesCondition<JavaCall<?>> {
    public ClassCallsMethodCondition(DescribedPredicate<? super JavaCall<?>> predicate) {
        super(new MethodCallCondition(predicate));
    }

    @Override
    Collection<JavaCall<?>> relevantAttributes(JavaClass item) {
        return item.getCallsFromSelf();
    }
}
