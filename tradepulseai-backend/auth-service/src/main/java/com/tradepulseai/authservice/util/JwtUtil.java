package com.tradepulseai.authservice.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    private final Key secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret){
        byte[] keyBytes = Base64.getDecoder().decode(secret.getBytes(StandardCharsets.UTF_8));

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, String role, Long userId){
        return Jwts.builder()
                .subject(email)
                .claim("role",role)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hours
                .signWith(secretKey)
                .compact();
    }

    public void validateToken(String token){
        try{
            parseClaims(token);

        } catch (SignatureException e) {
            throw new JwtException("Invalid JWT signature");
        } catch(JwtException e){
            throw new JwtException("Invalid JWT");
        }
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        Object raw = parseClaims(token).get("userId");
        if (raw instanceof Integer value) {
            return value.longValue();
        }
        if (raw instanceof Long value) {
            return value;
        }
        if (raw instanceof String value) {
            return Long.parseLong(value);
        }
        throw new JwtException("Invalid JWT userId claim");
    }
}
