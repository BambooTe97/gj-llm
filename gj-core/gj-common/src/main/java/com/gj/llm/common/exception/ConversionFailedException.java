package com.gj.llm.common.exception;

import org.springframework.core.convert.ConversionException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

@ResponseStatus(value= HttpStatus.INTERNAL_SERVER_ERROR)
public class ConversionFailedException extends ConversionException {
    @Serial
    private static final long serialVersionUID = 4036231888649291064L;

    public ConversionFailedException(Class<?> sourceType, Class<?> targetType, Throwable cause) {
        super("Converting from type [" + sourceType.getName() + "] to type [" + targetType.getName() + "]", cause);
    }

    public ConversionFailedException(Class<?> sourceType, Class<?> targetType, Object source) {
        super("Converting from type [" + sourceType.getName() + "] value [" + source.toString() +"] to type [" + targetType.getName() + "]");
    }
}
