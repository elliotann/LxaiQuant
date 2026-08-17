package com.chain.ai.trade.order.validation.chain;

import com.chain.ai.trade.order.validation.OrderValidationHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单校验责任链管理器
 * 负责构建和管理校验处理器责任链
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderValidationChainManager {

    private final ApplicationContext applicationContext;

    /**
     * 构建订单校验责任链
     * @param isAddPosition 是否为补仓操作
     * @return 责任链的头节点，如果无可用处理器则返回null
     */
    public OrderValidationHandler buildValidationChain(boolean isAddPosition) {
        // 获取所有校验处理器
        List<OrderValidationHandler> allHandlers = getAllHandlers();

        // 按优先级排序
        List<OrderValidationHandler> sortedHandlers = allHandlers.stream()
            .sorted(Comparator.comparing(OrderValidationHandler::getPriority))
            .collect(Collectors.toList());

        // 过滤适用的处理器
        List<OrderValidationHandler> applicableHandlers = sortedHandlers.stream()
            .filter(handler -> isApplicable(handler, isAddPosition))
            .collect(Collectors.toList());

        if (applicableHandlers.isEmpty()) {
            log.warn("未找到适用的校验处理器");
            return null;
        }

        // 构建责任链
        for (int i = 0; i < applicableHandlers.size() - 1; i++) {
            applicableHandlers.get(i).setNextHandler(applicableHandlers.get(i + 1));
        }

        log.debug("构建校验责任链成功，共{}个处理器", applicableHandlers.size());
        return applicableHandlers.get(0);
    }

    /**
     * 获取所有校验处理器
     * @return 处理器列表
     */
    private List<OrderValidationHandler> getAllHandlers() {
        return List.of(applicationContext.getBeanNamesForType(OrderValidationHandler.class))
            .stream()
            .map(name -> (OrderValidationHandler) applicationContext.getBean(name))
            .collect(Collectors.toList());
    }

    /**
     * 判断处理器是否适用于当前场景
     * @param handler 校验处理器
     * @param isAddPosition 是否为补仓操作
     * @return 是否适用
     */
    private boolean isApplicable(OrderValidationHandler handler, boolean isAddPosition) {
        // 所有处理器都适用于基础校验
        // 后续可以根据处理器类型和场景进行更精细的控制
        return true;
    }
}