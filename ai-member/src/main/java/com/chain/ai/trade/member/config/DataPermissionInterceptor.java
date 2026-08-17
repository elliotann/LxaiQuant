package com.chain.ai.trade.member.config;

import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.chain.ai.trade.member.annotation.DataScope;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.SQLException;

@Component
public class DataPermissionInterceptor implements InnerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DataPermissionInterceptor.class);

    @Override
    public void beforeQuery(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds,
                            ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
        if (InterceptorIgnoreHelper.willIgnoreDataPermission(ms.getId())) {
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return;
        }

        String role = (String) auth.getDetails();
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }

        String userId = (String) auth.getPrincipal();
        if (!StringUtils.hasText(userId)) {
            return;
        }

        String msId = ms.getId();
        String mapperClassName = msId.substring(0, msId.lastIndexOf('.'));
        try {
            Class<?> mapperClass = Class.forName(mapperClassName);
            DataScope dataScope = mapperClass.getAnnotation(DataScope.class);
            if (dataScope == null) {
                return;
            }

            String field = dataScope.field();
            String newSql = appendDataFilter(boundSql.getSql(), field, userId);
            PluginUtils.mpBoundSql(boundSql).sql(newSql);
        } catch (ClassNotFoundException e) {
            log.debug("Mapper class not found: {}", mapperClassName);
        } catch (JSQLParserException e) {
            log.warn("Failed to parse SQL for data permission: {}", e.getMessage());
        }
    }

    private String appendDataFilter(String sql, String field, String userId) throws JSQLParserException {
        Select select = (Select) CCJSqlParserUtil.parse(sql);
        PlainSelect plainSelect = select.getPlainSelect();
        if (plainSelect == null) {
            return sql;
        }

        Expression where = plainSelect.getWhere();
        EqualsTo eq = new EqualsTo();
        eq.setLeftExpression(new Column(field));
        eq.setRightExpression(new StringValue(userId));

        if (where == null) {
            plainSelect.setWhere(eq);
        } else {
            plainSelect.setWhere(new AndExpression(eq, where));
        }

        return select.toString();
    }
}
