package com.samsenpro.aiassistant.auth;

import com.samsenpro.aiassistant.common.exception.ApiException;
import com.samsenpro.aiassistant.common.exception.ErrorCode;
import com.samsenpro.aiassistant.common.security.AuthenticatedUser;
import com.samsenpro.aiassistant.common.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final Clock clock;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager, JwtService jwtService, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsername(request.username())) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN);
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_TAKEN);
        }
        User user;
        try {
            user = userRepository.saveAndFlush(new User(request.username(), email,
                    passwordEncoder.encode(request.password()), clock.instant()));
        } catch (DataIntegrityViolationException ex) {
            // Dos registros simultáneos con el mismo username/email: la restricción UNIQUE decide
            throw new ApiException(ErrorCode.USERNAME_TAKEN, "The username or email is already registered");
        }
        log.info("User registered: id={}", user.getId());
        return toResponse(AuthenticatedUser.from(user), user.getEmail());
    }

    public AuthResponse login(LoginRequest request) {
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
            AuthenticatedUser user = (AuthenticatedUser) authentication.getPrincipal();
            String email = userRepository.findById(user.id()).map(User::getEmail).orElse(null);
            return toResponse(user, email);
        } catch (AuthenticationException ex) {
            // Mismo error para usuario inexistente y contraseña incorrecta
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
    }

    private AuthResponse toResponse(AuthenticatedUser user, String email) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", jwtService.expirationSeconds(),
                new AuthResponse.UserView(user.id(), user.username(), email));
    }
}
