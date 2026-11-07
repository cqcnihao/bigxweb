package com.cqcnihao.bigx.common.datascope;

import cn.dev33.satoken.stp.StpUtil;
import com.cqcnihao.bigx.common.annotation.DataScope;
import com.cqcnihao.bigx.common.annotation.DataScopeType;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;
import net.sf.jsqlparser.statement.select.SetOperationList;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据权限拦截器：基于 JSqlParser AST 改写 SELECT 的 WHERE 条件。
 * 旧实现为字符串拼接，存在 ORDER BY 后追加语法错误、表名误当别名、OR 条件无括号越权三个缺陷。
 */
@Slf4j
@Component
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class DataScopeInterceptor implements Interceptor {

    /** mappedStatement id -> 注解解析结果缓存，避免每次 prepare 做反射 */
    private static final ConcurrentHashMap<String, Optional<DataScope>> ANNOTATION_CACHE = new ConcurrentHashMap<>();

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");

        DataScope dataScope = findDataScope(mappedStatement);
        if (dataScope == null) {
            return invocation.proceed();
        }

        BoundSql boundSql = statementHandler.getBoundSql();
        String originalSql = boundSql.getSql();

        Expression condition = buildDataScopeCondition(dataScope);
        if (condition == null) {
            return invocation.proceed();
        }

        Statement statement = CCJSqlParserUtil.parse(originalSql);
        if (!(statement instanceof Select select)) {
            // 只处理 SELECT；对增删改误标 @DataScope 时不动 SQL，避免破坏语义
            log.warn("[DataScope] @DataScope 仅支持 SELECT 语句，已跳过: {}", mappedStatement.getId());
            return invocation.proceed();
        }
        applyCondition(select, condition);

        String newSql = statement.toString();
        metaObject.setValue("delegate.boundSql.sql", newSql);

        log.debug("[DataScope] original: {}", originalSql);
        log.debug("[DataScope] modified:  {}", newSql);

        return invocation.proceed();
    }

    /** 将权限条件以 AND 追加到 SELECT 的 WHERE（条件整体加括号，OR 不会外溢）；UNION 各分支分别处理 */
    private void applyCondition(Select select, Expression condition) {
        if (select instanceof PlainSelect plainSelect) {
            Expression where = plainSelect.getWhere();
            if (where == null) {
                plainSelect.setWhere(condition);
            } else {
                Parenthesis parenthesized = new Parenthesis();
                parenthesized.setExpression(condition);
                plainSelect.setWhere(new AndExpression(where, parenthesized));
            }
        } else if (select instanceof SetOperationList setOperationList) {
            for (Select sub : setOperationList.getSelects()) {
                applyCondition(sub, condition);
            }
        }
    }

    /** 方法注解优先，其次类注解；结果缓存 */
    private DataScope findDataScope(MappedStatement ms) {
        return ANNOTATION_CACHE.computeIfAbsent(ms.getId(), id -> {
            try {
                int lastDot = id.lastIndexOf('.');
                Class<?> clazz = Class.forName(id.substring(0, lastDot));
                String methodName = id.substring(lastDot + 1);
                for (var method : clazz.getMethods()) {
                    if (method.getName().equals(methodName)) {
                        DataScope methodScope = method.getAnnotation(DataScope.class);
                        if (methodScope != null) {
                            return Optional.of(methodScope);
                        }
                    }
                }
                return Optional.ofNullable(clazz.getAnnotation(DataScope.class));
            } catch (ClassNotFoundException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /**
     * 构建权限条件表达式；返回 null 表示不限制（ALL 或 admin 非 CUSTOM）。
     * 未登录或构建异常时返回 1=0，宁可查不到也不越权。
     */
    private Expression buildDataScopeCondition(DataScope dataScope) {
        try {
            if (!StpUtil.isLogin()) {
                return denyAll();
            }

            DataScopeType type = dataScope.value();
            if (type != DataScopeType.CUSTOM && StpUtil.hasRole("admin")) {
                return null;
            }

            Column column = new Column(dataScope.column());
            Long userId = StpUtil.getLoginIdAsLong();
            switch (type) {
                case ALL:
                    return null;
                case USER_ID:
                    return new EqualsTo(column, new LongValue(userId));
                case DEPT_ID:
                case CUSTOM:
                default:
                    Long deptId = getCurrentUserDeptId();
                    if (deptId == null) {
                        return new EqualsTo(column, new LongValue(userId));
                    }
                    return new OrExpression(
                            new EqualsTo(column, new LongValue(deptId)),
                            new EqualsTo(column, new LongValue(userId)));
            }
        } catch (Exception e) {
            log.warn("[DataScope] 构建数据权限条件失败: {}", e.getMessage());
            return denyAll();
        }
    }

    private Expression denyAll() {
        return new EqualsTo(new LongValue(1), new LongValue(0));
    }

    private Long getCurrentUserDeptId() {
        try {
            Object deptId = StpUtil.getSession().get("deptId");
            return deptId != null ? Long.parseLong(deptId.toString()) : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }
}
