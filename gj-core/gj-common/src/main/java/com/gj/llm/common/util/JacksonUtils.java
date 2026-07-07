package com.gj.llm.common.util;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.gj.llm.common.exception.UtilException;
import com.gj.llm.common.spring.SpringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public abstract class JacksonUtils {
    private static final Map<String, JsonFormat> JSON_FORMAT_CONTAINER = new HashMap<>();

    private static ObjectMapper objectMapper = null;
    private static ObjectMapper reducedObjectMapper = null;

    public static JsonMapper enhanceObjectMapper(JsonMapper mapper) {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Double.class, new NumberSerializer());
        module.addSerializer(double.class, new NumberSerializer());
        module.addSerializer(Long.class, new NumberSerializer());
        module.addSerializer(long.class, new NumberSerializer());
        module.addSerializer(Integer.class, new NumberSerializer());
        module.addSerializer(int.class, new NumberSerializer());
        module.addSerializer(Date.class, new DateSerializer());
        module.addDeserializer(Date.class, new DateDeserializer());
        module.addSerializer(LocalDate.class, new LocalDateSerializer());
        module.addDeserializer(LocalDate.class, new LocalDateDeserializer());
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer());
        module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer());

        return mapper.rebuild()
                .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .addModule(module)
                .build();
    }

    public static <T> String toReducedJson(T item) {
        try {
            return getReducedObjectMapper().writeValueAsString(item);
        } catch (JacksonException je) {
            throw new UtilException(je);
        }
    }

    public static <T> String toJson(T item) {
        try {
            return getObjectMapper().writeValueAsString(item);
        } catch (JacksonException je) {
            throw new UtilException(je);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) {
        try {
            return getObjectMapper().readValue(json, type);
        } catch (JacksonException je) {
            throw new UtilException(je);
        }
    }

    public static <T> List<T> listFromJson(String json, Class<T> type) {
        try {
            JavaType javaType = getObjectMapper().getTypeFactory().constructParametricType(ArrayList.class, type);

            return getObjectMapper().readValue(json, javaType);
        } catch (JacksonException je) {
            throw new UtilException(je);
        }
    }

    public static <T> T fromJson(String json, String fieldName, Class<T> type) {
        try {
            JsonNode node = getObjectMapper().readTree(json);

            JsonNode fieldNode = node.get(fieldName);

            if (fieldNode.isTextual()) {
                return fromJson(node.get(fieldName).asText(), type);
            }

            return fromJson(fieldNode.toString(), type);
        } catch (JacksonException je) {
            throw new UtilException(je);
        }
    }

    public static <T> List<T> listFromJson(String json, String fieldName, Class<T> type) {
        try {
            JsonNode node = getObjectMapper().readTree(json);

            return listFromJson(node.get(fieldName).toString(), type);
        } catch (JacksonException je) {
            throw new UtilException(je);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> Map<String, Object> toMap(T obj) {
        if (obj == null) {
            return null;
        }
        return getObjectMapper().convertValue(obj, Map.class);
    }

    public static <T> List<Map<String, Object>> toMapList(List<T> list) {
        if (list == null) {
            return null;
        }
        List<Map<String, Object>> result = new ArrayList<>(list.size());
        for (T item : list) {
            result.add(toMap(item));
        }
        return result;
    }

    public static <T> T fromMap(Map<String, Object> map, Class<T> type) {
        if (map == null) {
            return null;
        }
        return getObjectMapper().convertValue(map, type);
    }

    public static <T> List<T> fromMapList(List<Map<String, Object>> mapList, Class<T> type) {
        if (mapList == null) {
            return null;
        }
        List<T> result = new ArrayList<>(mapList.size());
        for (Map<String, Object> map : mapList) {
            result.add(fromMap(map, type));
        }
        return result;
    }

    //-----------------------------------------------------------------
    // 私有方法
    //-----------------------------------------------------------------
    private static class DateSerializer extends ValueSerializer<Date> {
        @Override
        public void serialize(Date value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value != null) {
                JsonFormat jsonFormat = getJsonFormat(gen);

                if (jsonFormat == null || jsonFormat.pattern() == null) {
                    gen.writeString(DateUtils.formatDateTime(value));
                } else {
                    gen.writeString(new SimpleDateFormat(jsonFormat.pattern()).format(value));
                }
            }
        }
    }

    private static class DateDeserializer extends ValueDeserializer<Date> {
        @Override
        public Date deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            return DateUtils.parseDate(p.getText());
        }
    }

    private static class LocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value != null) {
                JsonFormat jsonFormat = getJsonFormat(gen);

                if (jsonFormat == null || jsonFormat.pattern() == null) {
                    gen.writeString(DateUtils.formatLocalDateTime(value));
                } else {
                    gen.writeString(DateUtils.formatLocalDateTime(value, DateTimeFormatter.ofPattern(jsonFormat.pattern())));
                }
            }
        }
    }

    private static class LocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            return DateUtils.parseLocalDateTime(p.getText());
        }
    }

    private static class LocalDateSerializer extends ValueSerializer<LocalDate> {
        @Override
        public void serialize(LocalDate value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value != null) {
                JsonFormat jsonFormat = getJsonFormat(gen);

                if (jsonFormat == null || jsonFormat.pattern() == null) {
                    gen.writeString(DateUtils.formatLocalDate(value));
                } else {
                    gen.writeString(DateUtils.formatLocalDate(value, DateTimeFormatter.ofPattern(jsonFormat.pattern())));
                }
            }
        }
    }

    private static class LocalDateDeserializer extends ValueDeserializer<LocalDate> {
        @Override
        public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            return DateUtils.parseLocalDate(p.getText());
        }
    }

    private static class NumberSerializer extends ValueSerializer<Number> {
        @Override
        public void serialize(Number value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value != null) {
                gen.writeNumber(NumberUtils.formatNumber(value));
            }
        }
    }

    private static class ReducedStringSerializer extends ValueSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value != null) {
                if (value.length() > 128) {
                    gen.writeString(value.subSequence(0, 125) + "...(more)");
                } else {
                    gen.writeString(value);
                }
            }
        }
    }

    private static ObjectMapper getObjectMapper() {
        if (objectMapper == null) {
            synchronized (JacksonUtils.class) {
                if (objectMapper == null) {
                    objectMapper = SpringUtils.getBean(ObjectMapper.class);
                }
            }
        }

        return objectMapper;
    }

    private static ObjectMapper getReducedObjectMapper() {
        if (reducedObjectMapper == null) {
            synchronized (JacksonUtils.class) {
                if (reducedObjectMapper == null) {
                    JsonMapper builder = SpringUtils.getBean(JsonMapper.class);
                    reducedObjectMapper = enhanceObjectMapper(builder).rebuild()
                            .addModule(new SimpleModule().addSerializer(String.class, new ReducedStringSerializer()))
                            .build();
                }
            }
        }

        return reducedObjectMapper;
    }

    private static JsonFormat getJsonFormat(JsonGenerator gen) {
        String currentName = gen.streamWriteContext().currentName();
        if (currentName == null) {
            return null;
        }
        String cacheKey = gen.currentValue().getClass() + ":" + currentName;

        if (JSON_FORMAT_CONTAINER.containsKey(cacheKey)) {
            return JSON_FORMAT_CONTAINER.get(cacheKey);
        } else {
            Field field = ReflectUtils.getAccessibleField(gen.currentValue().getClass(), currentName);

            if (field == null) {
                JSON_FORMAT_CONTAINER.put(cacheKey, null);

                return null;
            } else {
                JsonFormat jsonFormat = ReflectUtils.getAccessibleField(gen.currentValue().getClass(), currentName).getAnnotation(JsonFormat.class);
                JSON_FORMAT_CONTAINER.put(cacheKey, jsonFormat);

                return jsonFormat;
            }
        }
    }
}
