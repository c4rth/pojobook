package org.pojobook.generator.context;

import org.pojobook.generator.FieldNameTracker;
import org.pojobook.generator.common.ConditionNameMethodGenerator;
import org.pojobook.generator.common.FieldValidator;
import org.pojobook.generator.common.GetterSetterGenerator;
import org.pojobook.generator.common.ObjectMethodsGenerator;
import org.pojobook.generator.common.TypeResolver;
import org.pojobook.generator.embedded.ConstructorGenerator;
import org.pojobook.generator.embedded.DeserializationMethodsGenerator;
import org.pojobook.generator.embedded.OffsetCalculator;
import org.pojobook.generator.embedded.SerializationMethodsGenerator;

/**
 * Central context managing all generator helpers and shared state.
 * Implements Dependency Injection pattern for clean composition.
 */
public class GeneratorContext {

    // Shared state
    private final FieldNameTracker fieldNameTracker;
    
    // Common helpers (lazy-initialized)
    private volatile TypeResolver typeResolver;
    private volatile FieldValidator fieldValidator;
    private volatile GetterSetterGenerator getterSetterGenerator;
    private volatile ConditionNameMethodGenerator conditionNameMethodGenerator;
    private volatile ObjectMethodsGenerator objectMethodsGenerator;
    
    // Embedded helpers (lazy-initialized)
    private volatile OffsetCalculator offsetCalculator;
    private volatile ConstructorGenerator constructorGenerator;
    private volatile SerializationMethodsGenerator serializationGenerator;
    private volatile DeserializationMethodsGenerator deserializationGenerator;

    public GeneratorContext() {
        this.fieldNameTracker = new FieldNameTracker();
    }

    // Shared state accessors
    public FieldNameTracker getFieldNameTracker() {
        return fieldNameTracker;
    }

    // Common helpers - lazy initialization with double-checked locking
    public TypeResolver getTypeResolver() {
        if (typeResolver == null) {
            synchronized (this) {
                if (typeResolver == null) {
                    typeResolver = createTypeResolver();
                }
            }
        }
        return typeResolver;
    }

    public FieldValidator getFieldValidator() {
        if (fieldValidator == null) {
            synchronized (this) {
                if (fieldValidator == null) {
                    fieldValidator = createFieldValidator();
                }
            }
        }
        return fieldValidator;
    }

    public GetterSetterGenerator getGetterSetterGenerator() {
        if (getterSetterGenerator == null) {
            synchronized (this) {
                if (getterSetterGenerator == null) {
                    getterSetterGenerator = createGetterSetterGenerator();
                }
            }
        }
        return getterSetterGenerator;
    }

    public ConditionNameMethodGenerator getConditionNameMethodGenerator() {
        if (conditionNameMethodGenerator == null) {
            synchronized (this) {
                if (conditionNameMethodGenerator == null) {
                    conditionNameMethodGenerator = createConditionNameMethodGenerator();
                }
            }
        }
        return conditionNameMethodGenerator;
    }

    public ObjectMethodsGenerator getObjectMethodsGenerator() {
        if (objectMethodsGenerator == null) {
            synchronized (this) {
                if (objectMethodsGenerator == null) {
                    objectMethodsGenerator = createObjectMethodsGenerator();
                }
            }
        }
        return objectMethodsGenerator;
    }

    // Embedded helpers - lazy initialization
    public OffsetCalculator getOffsetCalculator() {
        if (offsetCalculator == null) {
            synchronized (this) {
                if (offsetCalculator == null) {
                    offsetCalculator = createOffsetCalculator();
                }
            }
        }
        return offsetCalculator;
    }

    public ConstructorGenerator getConstructorGenerator() {
        if (constructorGenerator == null) {
            synchronized (this) {
                if (constructorGenerator == null) {
                    constructorGenerator = createConstructorGenerator();
                }
            }
        }
        return constructorGenerator;
    }

    public SerializationMethodsGenerator getSerializationGenerator() {
        if (serializationGenerator == null) {
            synchronized (this) {
                if (serializationGenerator == null) {
                    serializationGenerator = createSerializationGenerator();
                }
            }
        }
        return serializationGenerator;
    }

    public DeserializationMethodsGenerator getDeserializationGenerator(Object parentGenerator) {
        if (deserializationGenerator == null) {
            synchronized (this) {
                if (deserializationGenerator == null) {
                    deserializationGenerator = createDeserializationGenerator(parentGenerator);
                }
            }
        }
        return deserializationGenerator;
    }

    // Factory methods - can be overridden for customization
    protected TypeResolver createTypeResolver() {
        return new TypeResolver();
    }

    protected FieldValidator createFieldValidator() {
        return new FieldValidator();
    }

    protected GetterSetterGenerator createGetterSetterGenerator() {
        return new GetterSetterGenerator(getFieldValidator());
    }

    protected ConditionNameMethodGenerator createConditionNameMethodGenerator() {
        return new ConditionNameMethodGenerator(getTypeResolver());
    }

    protected ObjectMethodsGenerator createObjectMethodsGenerator() {
        return new ObjectMethodsGenerator(getTypeResolver());
    }

    protected OffsetCalculator createOffsetCalculator() {
        return new OffsetCalculator(fieldNameTracker);
    }

    protected ConstructorGenerator createConstructorGenerator() {
        return new ConstructorGenerator(fieldNameTracker);
    }

    protected SerializationMethodsGenerator createSerializationGenerator() {
        return new SerializationMethodsGenerator(fieldNameTracker, getOffsetCalculator());
    }

    protected DeserializationMethodsGenerator createDeserializationGenerator(Object parentGenerator) {
        return new DeserializationMethodsGenerator(
            fieldNameTracker, 
            getOffsetCalculator(), 
            (org.pojobook.generator.AbstractPojoGenerator) parentGenerator
        );
    }

    /**
     * Reset all state for a new generation cycle.
     */
    public void reset() {
        fieldNameTracker.reset();
        if (offsetCalculator != null) {
            offsetCalculator.clearCache();
        }
        if (deserializationGenerator != null) {
            deserializationGenerator.resetTempVarCounter();
        }
    }
}

