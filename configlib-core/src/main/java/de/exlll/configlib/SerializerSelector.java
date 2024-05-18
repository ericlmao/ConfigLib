package de.exlll.configlib;

import de.exlll.configlib.Serializers.*;

import java.io.File;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static de.exlll.configlib.Validator.requireNonNull;

final class SerializerSelector {
    private static final Map<Class<?>, Serializer<?, ?>> DEFAULT_SERIALIZERS = Map.ofEntries(
            Map.entry(boolean.class, new BooleanSerializer()),
            Map.entry(Boolean.class, new BooleanSerializer()),
            Map.entry(byte.class, new NumberSerializer(byte.class)),
            Map.entry(Byte.class, new NumberSerializer(Byte.class)),
            Map.entry(short.class, new NumberSerializer(short.class)),
            Map.entry(Short.class, new NumberSerializer(Short.class)),
            Map.entry(int.class, new NumberSerializer(int.class)),
            Map.entry(Integer.class, new NumberSerializer(Integer.class)),
            Map.entry(long.class, new NumberSerializer(long.class)),
            Map.entry(Long.class, new NumberSerializer(Long.class)),
            Map.entry(float.class, new NumberSerializer(float.class)),
            Map.entry(Float.class, new NumberSerializer(Float.class)),
            Map.entry(double.class, new NumberSerializer(double.class)),
            Map.entry(Double.class, new NumberSerializer(Double.class)),
            Map.entry(char.class, new CharacterSerializer()),
            Map.entry(Character.class, new CharacterSerializer()),
            Map.entry(String.class, new StringSerializer()),
            Map.entry(BigInteger.class, new BigIntegerSerializer()),
            Map.entry(BigDecimal.class, new BigDecimalSerializer()),
            Map.entry(LocalDate.class, new LocalDateSerializer()),
            Map.entry(LocalTime.class, new LocalTimeSerializer()),
            Map.entry(LocalDateTime.class, new LocalDateTimeSerializer()),
            Map.entry(Instant.class, new InstantSerializer()),
            Map.entry(UUID.class, new UuidSerializer()),
            Map.entry(File.class, new FileSerializer()),
            Map.entry(Path.class, new PathSerializer()),
            Map.entry(URL.class, new UrlSerializer()),
            Map.entry(URI.class, new UriSerializer())
    );
    private final ConfigurationProperties properties;
    /**
     * Holds the last {@link #select}ed configuration element.
     */
    private ConfigurationElement<?> element;
    /**
     * The {@code currentNesting} is used to determine the nesting of a type and is incremented each
     * time the {@code selectForType} method is called. It is reset when {@code select} is called.
     * <p>
     * For example, for a field {@code List<Set<String>>}, the nesting of {@code List} would be 0,
     * the nesting of {@code Set} 1, and the nesting of {@code String} 2.
     */
    private int currentNesting = -1;

    public SerializerSelector(ConfigurationProperties properties) {
        this.properties = requireNonNull(properties, "configuration properties");
    }

    public Serializer<?, ?> select(ConfigurationElement<?> element) {
        this.element = element;
        this.currentNesting = -1;
        return selectForType(element.annotatedType());
    }

    private Serializer<?, ?> selectForType(AnnotatedType annotatedType) {
        this.currentNesting++;

        final Serializer<?, ?> custom = selectCustomSerializer(annotatedType);
        if (custom != null)
            return custom;

        final Type type = annotatedType.getType();
        if (type instanceof Class<?>) {
            return selectForClass(annotatedType);
        } else if (type instanceof ParameterizedType) {
            return selectForParameterizedType((AnnotatedParameterizedType) annotatedType);
        } else if (type instanceof WildcardType) {
            String msg = baseExceptionMessage(type) + "Wildcard types cannot be serialized.";
            throw new ConfigurationException(msg);
        } else if (type instanceof GenericArrayType) {
            String msg = baseExceptionMessage(type) + "Generic array types cannot be serialized.";
            throw new ConfigurationException(msg);
        } else if (type instanceof TypeVariable<?>) {
            String msg = baseExceptionMessage(type) + "Type variables cannot be serialized.";
            throw new ConfigurationException(msg);
        }
        // should not happen as we covered all possible types
        throw new ConfigurationException(baseExceptionMessage(type));
    }

    private Serializer<?, ?> selectCustomSerializer(AnnotatedType annotatedType) {
        return findConfigurationElementSerializer(annotatedType)
                .or(() -> findSerializerFactoryForType(annotatedType))
                .or(() -> findSerializerForType(annotatedType))
                .or(() -> findSerializerOnType(annotatedType))
                .or(() -> findMetaSerializerOnType(annotatedType))
                .or(() -> findSerializerByCondition(annotatedType))
                .orElse(null);
    }

    private Optional<Serializer<?, ?>> findConfigurationElementSerializer(AnnotatedType annotatedType) {
        // SerializeWith annotation on configuration elements
        final var annotation = element.annotation(SerializeWith.class);
        if ((annotation != null) && (currentNesting == annotation.nesting())) {
            return Optional.of(newSerializerFromAnnotation(annotatedType, annotation));
        }
        return Optional.empty();
    }

    private Optional<Serializer<?, ?>> findSerializerFactoryForType(AnnotatedType annotatedType) {
        // Serializer factory registered for Type via configurations properties
        if (annotatedType.getType() instanceof Class<?>) {
            Class<?> cls = (Class<?>) annotatedType.getType();
            if (!properties.getSerializerFactories().containsKey(cls)) return Optional.empty();

            final var context = new SerializerContextImpl(properties, element, annotatedType);
            final var factory = properties.getSerializerFactories().get(cls);
            final var serializer = factory.apply(context);
            if (serializer == null) {
                String msg = "Serializer factories must not return null.";
                throw new ConfigurationException(msg);
            }
            return Optional.of(serializer);
        }
        return Optional.empty();
    }

    private Optional<Serializer<?, ?>> findSerializerForType(AnnotatedType annotatedType) {
        // Serializer registered for Type via configurations properties
        if ((annotatedType.getType() instanceof Class<?>)) {
            Class<?> cls = (Class<?>) annotatedType.getType();
            if (!properties.getSerializers().containsKey(cls)) return Optional.empty();

            return Optional.of(properties.getSerializers().get(cls));
        }
        return Optional.empty();
    }

    private Optional<Serializer<?, ?>> findSerializerOnType(AnnotatedType annotatedType) {
        // SerializeWith annotation on type
        if (annotatedType.getType() instanceof Class<?>) {
            Class<?> cls = (Class<?>) annotatedType.getType();
            if (cls.getDeclaredAnnotation(SerializeWith.class) == null) return Optional.empty();

            final var annotation = cls.getDeclaredAnnotation(SerializeWith.class);
            return Optional.of(newSerializerFromAnnotation(annotatedType, annotation));
        }
        return Optional.empty();
    }

    private Optional<Serializer<?, ?>> findMetaSerializerOnType(AnnotatedType annotatedType) {
        // SerializeWith meta annotation on type
        if ((annotatedType.getType() instanceof Class<?>)) {
            Class<?> cls = (Class<?>) annotatedType.getType();

            for (final var meta : cls.getDeclaredAnnotations()) {
                final var metaType = meta.annotationType();
                final var annotation = metaType.getDeclaredAnnotation(SerializeWith.class);
                if (annotation != null)
                    return Optional.of(newSerializerFromAnnotation(annotatedType, annotation));
            }
        }
        return Optional.empty();
    }

    private Optional<Serializer<?, ?>> findSerializerByCondition(AnnotatedType annotatedType) {
        // Serializer registered for condition via configurations properties
        for (var entry : properties.getSerializersByCondition().entrySet()) {
            if (entry.getKey().test(annotatedType.getType()))
                return Optional.of(entry.getValue());
        }
        return Optional.empty();
    }

    private Serializer<?, ?> newSerializerFromAnnotation(
            AnnotatedType annotatedType,
            SerializeWith annotation
    ) {
        final var context = new SerializerContextImpl(properties, element, annotatedType);
        return Serializers.newCustomSerializer(annotation.serializer(), context);
    }

    private Serializer<?, ?> selectForClass(AnnotatedType annotatedType) {
        final Class<?> cls = (Class<?>) annotatedType.getType();
        if (DEFAULT_SERIALIZERS.containsKey(cls))
            return DEFAULT_SERIALIZERS.get(cls);
        if (Reflect.isEnumType(cls)) {
            // The following cast won't fail because we just checked that it's an enum.
            @SuppressWarnings("unchecked")
            final var enumType = (Class<? extends Enum<?>>) cls;
            return new Serializers.EnumSerializer(enumType);
        }
        if (Reflect.isArrayType(cls))
            return selectForArray((AnnotatedArrayType) annotatedType);
        if (Reflect.isConfigurationType(cls))
            return TypeSerializer.newSerializerFor(cls, properties);

        String msg = "Missing serializer for type " + cls + ".\n" +
                     "Either annotate the type with @Configuration, make it a Java record, " +
                     "or provide a custom serializer for it.";
        throw new ConfigurationException(msg);
    }

    private Serializer<?, ?> selectForArray(AnnotatedArrayType annotatedType) {
        final AnnotatedType annotatedElementType = annotatedType.getAnnotatedGenericComponentType();
        final Class<?> elementType = (Class<?>) annotatedElementType.getType();
        if (elementType == boolean.class) {
            return new PrimitiveBooleanArraySerializer();
        } else if (elementType == char.class) {
            return new PrimitiveCharacterArraySerializer();
        } else if (elementType == byte.class) {
            return new PrimitiveByteArraySerializer();
        } else if (elementType == short.class) {
            return new PrimitiveShortArraySerializer();
        } else if (elementType == int.class) {
            return new PrimitiveIntegerArraySerializer();
        } else if (elementType == long.class) {
            return new PrimitiveLongArraySerializer();
        } else if (elementType == float.class) {
            return new PrimitiveFloatArraySerializer();
        } else if (elementType == double.class) {
            return new PrimitiveDoubleArraySerializer();
        }
        var elementSerializer = selectForType(annotatedElementType);
        var inputNulls = properties.inputNulls();
        var outputNulls = properties.outputNulls();
        return new ArraySerializer<>(elementType, elementSerializer, outputNulls, inputNulls);
    }

    private Serializer<?, ?> selectForParameterizedType(AnnotatedParameterizedType annotatedType) {
        // the raw type returned by Java is always a class
        final var type = (ParameterizedType) annotatedType.getType();
        final var rawType = (Class<?>) type.getRawType();
        final var typeArgs = annotatedType.getAnnotatedActualTypeArguments();
        final var inputNulls = properties.inputNulls();
        final var outputNulls = properties.outputNulls();

        if (Reflect.isListType(rawType)) {
            var elementSerializer = selectForType(typeArgs[0]);
            return new ListSerializer<>(elementSerializer, outputNulls, inputNulls);
        } else if (Reflect.isSetType(rawType)) {
            var elementSerializer = selectForType(typeArgs[0]);
            return properties.serializeSetsAsLists()
                    ? new SetAsListSerializer<>(elementSerializer, outputNulls, inputNulls)
                    : new SetSerializer<>(elementSerializer, outputNulls, inputNulls);
        } else if (Reflect.isMapType(rawType)) {
            if (typeArgs[0].getType() instanceof Class<?>) {
                Class<?> cls = (Class<?>) typeArgs[0].getType();
                if (DEFAULT_SERIALIZERS.containsKey(cls) || Reflect.isEnumType(cls)) {
                    var keySerializer = selectForClass(typeArgs[0]);
                    var valSerializer = selectForType(typeArgs[1]);
                    return new MapSerializer<>(keySerializer, valSerializer, outputNulls, inputNulls);
                }
            }
            String msg = baseExceptionMessage(type) +
                         "Map keys can only be of simple or enum type.";
            throw new ConfigurationException(msg);
        }

        String msg = baseExceptionMessage(type) +
                     "Parameterized types other than lists, sets, and maps cannot be serialized.";
        throw new ConfigurationException(msg);
    }

    private String baseExceptionMessage(Type type) {
        return String.format("Cannot select serializer for type '%s'.\n", type);
    }
}
