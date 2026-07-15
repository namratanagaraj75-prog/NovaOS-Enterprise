package com.novaos.api.config;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.cloud.FirestoreClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/api/auth/")
                || "/api/health".equals(path)
                || "/error".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = authHeader.substring(7).trim();
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(idToken);
            DocumentSnapshot document = FirestoreClient.getFirestore()
                    .collection("users")
                    .document(decodedToken.getUid())
                    .get()
                    .get();

            if (!document.exists()) {
                logger.warn("Filter: Firebase user {} has no NovaOS user document", decodedToken.getUid());
                filterChain.doFilter(request, response);
                return;
            }
            Boolean active = document.getBoolean("active");
            String normalizedRole = normalizeRole(document.getString("role"));
            if (active == null || !active || normalizedRole.isBlank()) {
                logger.warn("Filter: Firebase user {} is inactive or roleless", decodedToken.getUid());
                filterChain.doFilter(request, response);
                return;
            }

            SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + normalizedRole);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    decodedToken.getUid(), null, Collections.singletonList(authority)
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            logger.warn("Filter: Firebase ID token validation failed: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String normalizeRole(String roleStr) {
        String r = (roleStr != null ? roleStr : "").toUpperCase().trim().replaceAll("[\\s-]+", "_");
        if (r.equals("CEO")) return "CEO";
        if (r.equals("SUPER_ADMIN")) return "SUPER_ADMIN";
        if (r.equals("HR") || r.equals("HR_ADMIN")) return "HR_ADMIN";
        if (r.equals("MANAGER") || r.equals("HIRING_MANAGER")) return "HIRING_MANAGER";
        if (r.equals("FINANCE")) return "FINANCE";
        if (r.equals("LEGAL")) return "LEGAL";
        return "";
    }
}