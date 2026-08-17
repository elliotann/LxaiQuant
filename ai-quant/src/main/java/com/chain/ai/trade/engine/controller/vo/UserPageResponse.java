package com.chain.ai.trade.engine.controller.vo;

import com.chain.ai.trade.engine.controller.dto.UserManagementDTO;
import lombok.Data;

import java.util.List;

/**
 * 用户分页响应VO
 */
@Data
public class UserPageResponse {
    private List<UserManagementDTO> users;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
    private Integer pages;
}
