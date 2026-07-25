package com.zx.consultant.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具类 (基于 JJWT 0.12.x 版本)
 */
public class JwtUtil {

    public static final String BEARER_PREFIX = "Bearer ";

    /**
     * 将字符串转换为 HMAC-SHA 算法所需的 SecretKey
     */
    private static SecretKey getSecretKey(String secretKey) {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT
     *
     * @param secretKey 密钥
     * @param ttlMillis 过期时间(毫秒)
     * @param claims    自定义载荷信息
     * @return Token 字符串
     */
    public static String createJWT(String secretKey, long ttlMillis, Map<String, Object> claims) {
        // 计算过期时间
        long expMillis = System.currentTimeMillis() + ttlMillis;
        Date exp = new Date(expMillis);

        String jwt = Jwts.builder()
                .claims(claims)           // 设置载荷
                .expiration(exp)          // 设置过期时间
                .signWith(getSecretKey(secretKey)) // 设置签名密钥
                .compact();
        return BEARER_PREFIX + jwt;
    }

    /**
     * 解析并校验 JWT
     *
     * @param secretKey 密钥
     * @param token     加密后的 Token 字符串
     * @return 解析出的载荷信息 (Claims)
     */
    public static Claims parseJWT(String secretKey, String token) {
        return Jwts.parser()
                .verifyWith(getSecretKey(secretKey)) // 设置校验密钥
                .build()
                .parseSignedClaims(token) // 解析并校验 Token
                .getPayload();            // 获取载荷内容
    }
}