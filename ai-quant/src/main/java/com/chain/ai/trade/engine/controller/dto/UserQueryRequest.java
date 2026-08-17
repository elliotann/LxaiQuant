package com.chain.ai.trade.engine.controller.dto;

import lombok.Data;

/**
 * 用户查询请求DTO
 */
@Data
public class UserQueryRequest {
    private String username;
    private String email;
    private String role;
    private String status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
