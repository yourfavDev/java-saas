package com.libraries.saas.services;

import com.libraries.auth.dto.LoginRequest;
import com.libraries.auth.dto.TokenResponse;
import com.libraries.auth.dto.UserInfoDto;
import com.libraries.auth.repository.UserRepository;
import io.sentry.Sentry;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Collections;
import java.util.List;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean login(LoginRequest creds, HttpSession session) {
        try {
            TokenResponse userToken = userRepository.login(creds);
            if (userToken == null) {
                return false;
            }

            UserInfoDto userInfo = userRepository.getUserInfo(userToken.accessToken());
            session.setAttribute("userId", userInfo.id());
            List<String> roles = userInfo.roles().orElse(Collections.emptyList());
            session.setAttribute("roles", roles);
            session.setAttribute("user", creds.username());
            session.setAttribute("token", userToken);

            log.info("User {} logged in successfully", creds.username());
            return true;

        } catch (HttpClientErrorException e) {
            return false;

        } catch (Exception e) {
            Sentry.captureException(e);
            log.error("Unexpected error during login for user {}", creds.username(), e);
            return false;
        }
    }
}
