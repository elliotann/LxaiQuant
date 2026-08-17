package com.chain.ai.trade.common.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 统一API响应格式
 * 
 * 合并了原 ai-quant 中两套 ApiResponse 的功能：
 * - controller.dto.ApiResponse: code, message, data, timestamp
 * - entity.vo.ApiResponse: success, message, data, code, timestamp
 * 
 * 统一后的响应体包含所有字段，兼容两种使用方式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否成功（兼容原 entity.vo.ApiResponse）
     */
    private Boolean success;

    /**
     * 响应代码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 时间戳（毫秒）
     */
    private Long timestamp;

    /**
     * 创建成功响应（仅数据）
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(200)
                .message("操作成功")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建成功响应（带消息和数据）
     */
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code(200)
                .message(message)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建失败响应（仅消息，默认 code 500）
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(500)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 创建失败响应（带代码和消息）
     */
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(code)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 从异常创建错误响应
     */
    public static <T> ApiResponse<T> ofException(Exception e) {
        return ApiResponse.<T>builder()
                .success(false)
                .code(500)
                .message(e.getMessage() != null ? e.getMessage() : "操作失败")
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 从异常创建错误响应（带自定义消息前缀）
     */
    public static <T> ApiResponse<T> ofException(String prefix, Exception e) {
        String message = prefix + (e.getMessage() != null ? ": " + e.getMessage() : "");
        return ApiResponse.<T>builder()
                .success(false)
                .code(500)
                .message(message)
                .data(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}

