package com.cqcnihao.bigx.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.SignatureException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;


/**
 * JWT工具类
 * 用于生成、解析和验证JWT令牌
 */
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);

    // 密钥（建议从配置文件读取）
    @Value("${jwt.secret:springboot_jwt_demo_123456}")
    private String SECRET;

    // 过期时间（毫秒），默认1天
    @Value("${jwt.expiration:86400000}")
    private long EXPIRATION;

    // 令牌前缀
    @Value("${jwt.tokenPrefix:Bearer }")
    private String TOKEN_PREFIX;

    /**
     * 生成token
     * @param userId 用户ID
     * @return JWT令牌字符串
     */
    public String generateToken(Integer userId) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /**
     * 生成带自定义声明的token
     * @param userId 用户ID
     * @param claims 自定义声明
     * @return JWT令牌字符串
     */
    public String generateToken(Integer userId, Map<String, Object> claims) {
        Map<String, Object> allClaims = new HashMap<>();
        if (claims != null) {
            allClaims.putAll(claims);
        }
        
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .addClaims(allClaims)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    /**
     * 解析token
     * @param token JWT令牌字符串
     * @return Claims对象，如果token无效返回null
     */
    public Claims getClaims(String token) {
        try {
            // 移除可能的Bearer前缀
            if (token != null && token.startsWith(TOKEN_PREFIX)) {
                token = token.substring(TOKEN_PREFIX.length());
            }

            return Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            // 令牌过期
            return null;
        } catch (MalformedJwtException e) {
            // 令牌格式错误
            return null;
        } catch (SignatureException e) {
            // 签名验证失败
            return null;
        } catch (UnsupportedJwtException e) {
            // 不支持的令牌
            return null;
        } catch (IllegalArgumentException e) {
            // 参数错误
            return null;
        } catch (Exception e) {
            // 其他错误
            return null;
        }
    }

    /**
     * 判断token是否有效
     * @param token JWT令牌字符串
     * @return 是否有效
     */
    public boolean isValid(String token) {
        Claims claims = getClaims(token);
        return claims != null && !isExpired(token);
    }

    /**
     * 判断token是否过期
     * @param token JWT令牌字符串
     * @return 是否过期
     */
    public boolean isExpired(String token) {
        Claims claims = getClaims(token);
        return claims == null || claims.getExpiration().before(new Date());
    }

    /**
     * 获取用户ID
     * @param token JWT令牌字符串
     * @return 用户ID，如果token无效返回null
     */
    public Integer getUserId(String token) {
        Claims claims = getClaims(token);
        if (claims == null) return null;
        return Integer.valueOf(claims.getSubject());
    }

    /**
     * 获取token的签发时间
     * @param token JWT令牌字符串
     * @return 签发时间，如果token无效返回null
     */
    public Date getIssuedAt(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.getIssuedAt() : null;
    }

    /**
     * 获取token的过期时间
     * @param token JWT令牌字符串
     * @return 过期时间，如果token无效返回null
     */
    public Date getExpiration(String token) {
        Claims claims = getClaims(token);
        return claims != null ? claims.getExpiration() : null;
    }

    /**
     * 从token中获取自定义声明
     * @param token JWT令牌字符串
     * @param claimName 声明名称
     * @return 声明值，如果不存在或token无效返回null
     */
    public Object getClaim(String token, String claimName) {
        Claims claims = getClaims(token);
        return claims != null ? claims.get(claimName) : null;
    }

    /**
     * 刷新token
     * @param token 旧的JWT令牌字符串
     * @return 新的JWT令牌字符串，如果旧token无效返回null
     */
    public String refreshToken(String token) {
        Claims claims = getClaims(token);
        if (claims == null) return null;
        
        return Jwts.builder()
                .setSubject(claims.getSubject())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

}