package com.chain.ai.trade.order.validation.chain;

import com.chain.ai.trade.order.validation.OrderValidationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 校验处理器工厂
 * 使用工厂模式动态创建和管理校验处理器
 */
@Component
@RequiredArgsConstructor
public class ValidationHandlerFactory {

    private final ApplicationContext applicationContext;

    /**
     * 根据处理器类创建处理器实例
     * @param handlerClass 处理器类
     * @return 处理器实例
     */
    public OrderValidationHandler createHandler(Class<? extends OrderValidationHandler> handlerClass) {
        return applicationContext.getBean(handlerClass);
    }

    /**
     * 根据处理器名称创建处理器实例
     * @param handlerName 处理器名称
     * @return 处理器实例
     * @throws IllegalArgumentException 如果找不到指定名称的处理器
     */
    public OrderValidationHandler createHandler(String handlerName) {
        // 获取所有OrderValidationHandler类型的bean
        String[] beanNames = applicationContext.getBeanNamesForType(OrderValidationHandler.class);

        for (String beanName : beanNames) {
            OrderValidationHandler handler = (OrderValidationHandler) applicationContext.getBean(beanName);
            if (handlerName.equals(handler.getHandlerName())) {
                return handler;
            }
        }

        throw new IllegalArgumentException("未知的校验处理器: " + handlerName);
    }
}