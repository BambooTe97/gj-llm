package com.gj.llm.common.util;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ClassUtils {
    /**
     * Map with primitive wrapper type as key and corresponding primitive type as
     * value, for example: Integer.class -> int.class.
     */
    private static final Map<Class<?>, Class<?>> primitiveWrapperTypeMap = new IdentityHashMap<Class<?>, Class<?>>(8);

    /**
     * Map with primitive type as key and corresponding wrapper type as value, for
     * example: int.class -> Integer.class.
     */
    private static final Map<Class<?>, Class<?>> primitiveTypeToWrapperMap = new IdentityHashMap<Class<?>, Class<?>>(8);

    // initialize
    static {
        primitiveWrapperTypeMap.put(Boolean.class, boolean.class);
        primitiveWrapperTypeMap.put(Byte.class, byte.class);
        primitiveWrapperTypeMap.put(Character.class, char.class);
        primitiveWrapperTypeMap.put(Double.class, double.class);
        primitiveWrapperTypeMap.put(Float.class, float.class);
        primitiveWrapperTypeMap.put(Integer.class, int.class);
        primitiveWrapperTypeMap.put(Long.class, long.class);
        primitiveWrapperTypeMap.put(Short.class, short.class);

        primitiveWrapperTypeMap.forEach((w, p) -> primitiveTypeToWrapperMap.put(p, w));
    }

    /**
     * Check whether the specified class is a primitive type
     */
    public static boolean isPrimitiveType(Class<?> clazz) {
        return primitiveTypeToWrapperMap.containsKey(clazz);
    }

    /**
     * Get wrapper type of the specified primitive type
     */
    public static Class<?> getWrapperType(Class<?> primitiveType) {
        return primitiveTypeToWrapperMap.get(primitiveType);
    }

    /**
     * Get primitive default value
     */
    public static Object getPrimitiveDefaultValue(Class<?> primitiveType) {
        if (Boolean.TYPE == primitiveType) {
            return false;
        } else if (Byte.TYPE == primitiveType) {
            return '0';
        } else if (Character.TYPE == primitiveType) {
            return '0';
        } else if (Short.TYPE == primitiveType) {
            return 0;
        } else if (Integer.TYPE == primitiveType) {
            return 0;
        } else if (Float.TYPE == primitiveType) {
            return 0f;
        } else if (Double.TYPE == primitiveType) {
            return 0d;
        } else if (Long.TYPE == primitiveType) {
            return 0L;
        } else {
            return null;
        }
    }
}
