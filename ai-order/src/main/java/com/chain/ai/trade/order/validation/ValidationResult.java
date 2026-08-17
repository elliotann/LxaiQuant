package com.chain.ai.trade.order.validation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 校验结果
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private boolean valid;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> contextData;

    public static ValidationResult success() {
        return new ValidationResult(true, null, null, new HashMap<>());
    }

    public static ValidationResult failure(String errorCode, String message) {
        return new ValidationResult(false, errorCode, message, new HashMap<>());
    }

    public ValidationResult addContext(String key, Object value) {
        if (this.contextData == null) {
            this.contextData = new HashMap<>();
        }
        this.contextData.put(key, value);
        return this;
    }
}