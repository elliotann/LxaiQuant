package com.chain.ai.trade.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.Date;

@Data
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@TableName("vdr_member_robot_config")
public class MemberRobotConfig  extends BaseEntity {

    public enum ConfigType{
        //智子平台
        PLATFORM,
        //大客户自营系统
        SELF_CUSTOMER,
        //小客户会员模式
        MEMBER
    }
    public enum Status{
        IN_ACTIVE,
        RUNNING,
        STOP,
        EXPIRE,
        DEL
    }
    public enum BuySide{
        SELL,
        BUY,
        BOTH
    }
    private String remark;

    private Status status;

    private String robotId;

    private String productId;

    private String memberId;

    private String memberName;

    /**
     * 买方向，SELL，只做空，BUY,只做多，BOTH，
     */
    private BuySide buySide;

    private BigDecimal buyAmount;

    private BigDecimal buyAmountRadio;

    //失效日期
    private Date expireDate;

    //停止原因
    private String stopReason;

    //能开总资产(USDT)，以质押金多少来算
    private BigDecimal canUseTotal;

    private String memberPlatform;

    private String thirdAccountId;

    @TableLogic
    private Boolean deleteFlag;
    private Long startTime;

    private Long endTime;

    private Boolean test;

    private ConfigType configType;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    public MemberRobotConfig(String robotId, String productId, String memberId, String memberPlatform, BigDecimal buyAmount, BigDecimal canUseTotal, String thirdAccountId) {
        Date date = new Date();
        this.setId(String.valueOf(System.currentTimeMillis()));
        this.setCreateTime(date);
        this.setUpdateTime(date);
        this.setDeleteFlag(Boolean.FALSE);

        this.robotId = robotId;
        this.productId = productId;
        this.memberId = memberId;
        this.memberPlatform = memberPlatform;
        this.buyAmount = buyAmount;
        this.canUseTotal = canUseTotal;
        this.thirdAccountId = thirdAccountId;
        this.status = Status.STOP;

    }
}