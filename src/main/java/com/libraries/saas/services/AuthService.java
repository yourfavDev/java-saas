package com.libraries.saas.services;

import com.libraries.auth.dto.LoginRequest;
import com.libraries.auth.dto.TokenResponse;
import com.libraries.auth.dto.UserInfoDto;
import com.libraries.auth.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Collections;
import java.util.List;

@Service
public class AuthService {

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
            UserInfoDto userInfoDto = userRepository.getUserInfo(userToken.accessToken());
            session.setAttribute("userId", userInfoDto.id());
            List<String> roles = userInfoDto
                    .roles()                          // Optional<List<RoleDto>>
                    .orElse(Collections.emptyList());
            session.setAttribute("roles", roles);
            session.setAttribute("user", creds.username());
            session.setAttribute("token", userToken);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

}
