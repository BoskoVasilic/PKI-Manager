package com.tim12.pk_infrastructure.security;

import com.tim12.pk_infrastructure.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.access-secret}")
    private String accessSecret;

    @Value("${jwt.refresh-secret}")
    private String refreshSecret;

    @Value("${jwt.access-token-expiration-ms}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    private SecretKey accessKey() {
        return Keys.hmacShaKeyFor(accessSecret.getBytes());
    }

    private SecretKey refreshKey() {
        return Keys.hmacShaKeyFor(refreshSecret.getBytes());
    }

    public String generateAccessToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("userId", user.getId())
                .claim("organization", user.getOrganization())
                .claim("twoFaEnabled", user.isTwoFactorEnabled())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpirationMs))
                .signWith(accessKey())
                .compact();
    }

    public String generatePreAuthToken(User user) {
        // Pre-auth is short-lived and access-scoped, so access key is fine here
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("twoFaRequired", true)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 5 * 60 * 1000L))
                .signWith(accessKey())
                .compact();
    }

    public String generateRefreshToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("tokenType", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpirationMs))
                .signWith(refreshKey())
                .compact();
    }

    // Used only on /auth/refresh endpoint
    public Claims extractRefreshClaims(String token) {
        return Jwts.parser()
                .verifyWith(refreshKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Used for access + pre-auth tokens everywhere else
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(accessKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractEmailFromRefreshToken(String token) {
        return extractRefreshClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            return extractAllClaims(token).getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token) {
        try {
            Claims claims = extractRefreshClaims(token);
            return claims.getExpiration().after(new Date())
                    && "refresh".equals(claims.get("tokenType", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTwoFaRequired(String token) {
        try {
            Boolean val = extractAllClaims(token).get("twoFaRequired", Boolean.class);
            return Boolean.TRUE.equals(val);
        } catch (Exception e) {
            return false;
        }
    }
}