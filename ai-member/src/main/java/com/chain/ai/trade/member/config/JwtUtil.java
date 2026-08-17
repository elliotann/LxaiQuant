package com.chain.ai.trade.member.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey secretKey;

    @Getter
    private final long accessTokenExpiration;

    @Getter
    private final long refreshTokenExpiration;

    private final long rememberMeExpiration;

    public JwtUtil(
            @Value("${jwt.secret:}") String secret,
            @Value("${jwt.access-token-expiration:1800000}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration,
            @Value("${jwt.remember-me-expiration:2592000000}") long rememberMeExpiration) {
        if (!secret.isBlank()) {
            this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } else {
            this.secretKey = Keys.hmacShaKeyFor(
                    "changeit-changeit-changeit-changeit!".getBytes(StandardCharsets.UTF_8));
        }
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
        this.rememberMeExpiration = rememberMeExpiration;
    }

    public String generateAccessToken(String userId, String username, String role) {
        return generateAccessToken(userId, username, role, false);
    }

    public String generateAccessToken(String userId, String username, String role, boolean rememberMe) {
        long expiration = rememberMe ? rememberMeExpiration : accessTokenExpiration;
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
                .claim("role", role)
                .claim("rememberMe", rememberMe)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String userId) {
        return Jwts.builder()
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUserIdFromToken(String token) {
        return parseToken(token).getSubject();
    }

    public String getUsernameFromToken(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
