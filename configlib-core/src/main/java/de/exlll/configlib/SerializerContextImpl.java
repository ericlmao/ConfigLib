package de.exlll.configlib;

import java.lang.reflect.AnnotatedType;

import static de.exlll.configlib.Validator.requireNonNull;

public class SerializerContextImpl implements SerializerContext {

    private final ConfigurationProperties properties;
    private final ConfigurationElement<?> element;
    private final AnnotatedType annotatedType;

    public SerializerContextImpl(ConfigurationProperties properties, ConfigurationElement<?> element, AnnotatedType annotatedType) {
        this.properties = requireNonNull(properties, "configuration properties");
        this.element = requireNonNull(element, "configuration element");
        this.annotatedType = requireNonNull(annotatedType, "annotated type");
    }

    public ConfigurationProperties properties() {
        return properties;
    }

    public ConfigurationElement<?> element() {
        return element;
    }

    public AnnotatedType annotatedType() {
        return annotatedType;
    }
}

