package de.exlll.configlib;

import java.util.HashMap;
import java.util.Map;

import static de.exlll.configlib.Validator.requireNonNull;

/**
 * A collection of values used to configure the serialization of configurations.
 */
class ConfigurationProperties {
    private final Map<Class<?>, Serializer<?, ?>> serializersByType;
    private final FieldFormatter formatter;
    private final FieldFilter filter;
    private final boolean outputNulls;
    private final boolean inputNulls;
    private final boolean serializeSetsAsLists;

    /**
     * Constructs a new instance of this class with values taken from the given builder.
     *
     * @param builder the builder used to initialize the fields of this class
     * @throws NullPointerException if the builder or any of its values is null
     */
    protected ConfigurationProperties(Builder<?> builder) {
        this.serializersByType = Map.copyOf(builder.serializersByType);
        this.formatter = requireNonNull(builder.formatter, "field formatter");
        this.filter = requireNonNull(builder.filter, "field filter");
        this.outputNulls = builder.outputNulls;
        this.inputNulls = builder.inputNulls;
        this.serializeSetsAsLists = builder.serializeSetsAsLists;
    }

    /**
     * Constructs a new {@code Builder} with default values.
     *
     * @return newly constructed {@code Builder}
     */
    public static Builder<?> newBuilder() {
        return new BuilderImpl();
    }

    /**
     * Creates a new builder and initializes it with values taken from this properties object.
     *
     * @return new builder
     */
    public Builder<?> toBuilder() {
        return new BuilderImpl(this);
    }

    private static final class BuilderImpl extends Builder<BuilderImpl> {
        private BuilderImpl() {}

        private BuilderImpl(ConfigurationProperties properties) {super(properties);}

        @Override
        protected BuilderImpl getThis() {return this;}

        @Override
        public ConfigurationProperties build() {return new ConfigurationProperties(this);}
    }

    /**
     * A builder class for constructing {@code ConfigurationProperties}.
     *
     * @param <B> the type of builder
     */
    public static abstract class Builder<B extends Builder<B>> {
        private final Map<Class<?>, Serializer<?, ?>> serializersByType = new HashMap<>();
        /* change setter JavaDoc if default values are changed */
        private FieldFormatter formatter = FieldFormatters.IDENTITY;
        private FieldFilter filter = FieldFilters.DEFAULT;
        private boolean outputNulls = false;
        private boolean inputNulls = false;
        private boolean serializeSetsAsLists = true;

        protected Builder() {}

        protected Builder(ConfigurationProperties properties) {
            this.serializersByType.putAll(properties.serializersByType);
            this.formatter = properties.formatter;
            this.filter = properties.filter;
            this.outputNulls = properties.outputNulls;
            this.inputNulls = properties.inputNulls;
            this.serializeSetsAsLists = properties.serializeSetsAsLists;
        }

        /**
         * Sets the field filter. The given filter is applied in addition to and
         * after the default filter.
         *
         * @param filter the filter
         * @return this builder
         * @throws NullPointerException if {@code filter} is null
         */
        public final B setFieldFilter(FieldFilter filter) {
            this.filter = requireNonNull(filter, "field filter");
            return getThis();
        }

        /**
         * Sets the field formatter.
         * <p>
         * The default value is a formatter that returns the name of the field.
         *
         * @param formatter the formatter
         * @return this builder
         * @throws NullPointerException if {@code formatter} is null
         */
        public final B setFieldFormatter(FieldFormatter formatter) {
            this.formatter = requireNonNull(formatter, "field formatter");
            return getThis();
        }

        /**
         * Adds a serializer for the given type. If this library already provides a serializer
         * for the given type (e.g. {@code BigInteger}, {@code LocalDate}, etc.) the serializer
         * added by this method takes precedence.
         *
         * @param serializedType the class of the type that is serialized
         * @param serializer     the serializer
         * @param <T>            the type that is serialized
         * @return this builder
         * @throws NullPointerException if any argument is null
         */
        public final <T> B addSerializer(Class<T> serializedType, Serializer<T, ?> serializer) {
            requireNonNull(serializedType, "serialized type");
            requireNonNull(serializer, "serializer");
            serializersByType.put(serializedType, serializer);
            return getThis();
        }

        /**
         * Sets whether fields or collection elements whose value is null should be output
         * while serializing the configuration.
         * <p>
         * The default value is {@code false}.
         *
         * @param outputNulls whether to output null values
         * @return this builder
         */
        public final B outputNulls(boolean outputNulls) {
            this.outputNulls = outputNulls;
            return getThis();
        }

        /**
         * Sets whether fields or collection elements should allow null values to bet set
         * while deserializing the configuration.
         * <p>
         * The default value is {@code false}.
         *
         * @param inputNulls whether to input null values
         * @return this builder
         */
        public final B inputNulls(boolean inputNulls) {
            this.inputNulls = inputNulls;
            return getThis();
        }

        /**
         * Sets whether sets should be serialized as lists.
         * <p>
         * The default value is {@code true}.
         *
         * @param serializeSetsAsLists whether to serialize sets as lists
         * @return this builder
         */
        final B serializeSetsAsLists(boolean serializeSetsAsLists) {
            this.serializeSetsAsLists = serializeSetsAsLists;
            return getThis();
        }

        /**
         * Builds a {@code ConfigurationProperties} instance.
         *
         * @return newly constructed {@code ConfigurationProperties}
         */
        public abstract ConfigurationProperties build();

        /**
         * Returns this builder.
         *
         * @return this builder
         */
        protected abstract B getThis();
    }

    /**
     * Returns the field filter used to filter the fields of a configuration.
     *
     * @return the field filter
     */
    public final FieldFilter getFieldFilter() {
        return filter;
    }

    /**
     * Returns the field formatter used to format the fields of a configuration.
     *
     * @return the formatter
     */
    public final FieldFormatter getFieldFormatter() {
        return formatter;
    }

    /**
     * Returns an unmodifiable map of serializers by type. The serializers returned by this
     * method take precedence over any default serializers provided by this library.
     *
     * @return serializers by type
     */
    public final Map<Class<?>, Serializer<?, ?>> getSerializers() {
        return serializersByType;
    }

    /**
     * Returns whether null values should be output.
     *
     * @return whether to output null values
     */
    public final boolean outputNulls() {
        return outputNulls;
    }

    /**
     * Returns whether null values should be allowed as input.
     *
     * @return whether to input null values
     */
    public final boolean inputNulls() {
        return inputNulls;
    }


    /**
     * Returns whether sets should be serialized as lists.
     *
     * @return whether to serialize sets as lists
     */
    final boolean serializeSetsAsLists() {
        return serializeSetsAsLists;
    }
}