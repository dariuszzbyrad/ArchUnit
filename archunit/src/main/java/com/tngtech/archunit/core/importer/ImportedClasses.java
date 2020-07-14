/*
 * Copyright 2014-2020 TNG Technology Consulting GmbH
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
package com.tngtech.archunit.core.importer;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Sets;
import com.tngtech.archunit.base.Optional;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClassDescriptor;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.resolvers.ClassResolver;

import static com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT;
import static com.tngtech.archunit.core.domain.JavaModifier.FINAL;
import static com.tngtech.archunit.core.domain.JavaModifier.PUBLIC;

class ImportedClasses {
    private static final ImmutableSet<JavaModifier> PRIMITIVE_AND_ARRAY_TYPE_MODIFIERS =
            Sets.immutableEnumSet(PUBLIC, ABSTRACT, FINAL);

    private final ImmutableSortedMap<String, JavaClass> directlyImported;
    // We sort the key set by type name to ensure inner classes will always be later than all their outer classes
    // This is relevant in the completion process, e.g. for generic type parameters, where outer classes need to be processed first
    private final SortedMap<String, JavaClass> allClasses;

    private final ClassResolver resolver;

    ImportedClasses(Map<String, JavaClass> directlyImported, ClassResolver resolver) {
        this.directlyImported = ImmutableSortedMap.copyOf(directlyImported);
        this.allClasses = new TreeMap<>(this.directlyImported);
        this.resolver = resolver;
    }

    SortedMap<String, JavaClass> getDirectlyImported() {
        return directlyImported;
    }

    JavaClass getOrResolve(String typeName) {
        ensurePresent(typeName);
        return allClasses.get(typeName);
    }

    void ensurePresent(String typeName) {
        if (!contain(typeName)) {
            Optional<JavaClass> resolved = resolver.tryResolve(typeName);
            JavaClass newClass = resolved.isPresent() ? resolved.get() : simpleClassOf(typeName);
            allClasses.put(typeName, newClass);
        }
    }

    private boolean contain(String name) {
        return allClasses.containsKey(name);
    }

    SortedMap<String, JavaClass> getAll() {
        return ImmutableSortedMap.copyOf(allClasses);
    }

    ClassesByTypeName byTypeName() {
        return new ClassesByTypeName() {
            @Override
            public JavaClass get(String typeName) {
                return ImportedClasses.this.getOrResolve(typeName);
            }
        };
    }

    private static JavaClass simpleClassOf(String typeName) {
        JavaClassDescriptor descriptor = JavaClassDescriptor.From.name(typeName);
        DomainBuilders.JavaClassBuilder builder = new DomainBuilders.JavaClassBuilder().withDescriptor(descriptor);
        addModifiersIfPossible(builder, descriptor);
        return builder.build();
    }

    private static void addModifiersIfPossible(DomainBuilders.JavaClassBuilder builder, JavaClassDescriptor descriptor) {
        if (descriptor.isPrimitive() || descriptor.isArray()) {
            builder.withModifiers(PRIMITIVE_AND_ARRAY_TYPE_MODIFIERS);
        }
    }

}
