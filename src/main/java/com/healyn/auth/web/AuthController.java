package com.healyn.auth.web;

import com.healyn.auth.service.DeviceSessionService;
import com.healyn.auth.service.IssuedSession;
import com.healyn.auth.service.LoginService;
import com.healyn.auth.service.PasswordResetService;
import com.healyn.auth.service.RegistrationService;
import com.healyn.common.error.ErrorCode;
import com.healyn.common.error.UnauthorizedException;
import com.healyn.common.error.UnprocessableException;
import com.healyn.patients.service.AddressData;
import com.healyn.patients.service.NewPatientProfile;
import com.healyn.patients.web.PatientDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final RegistrationService registration;
    private final LoginService login;
    private final DeviceSessionService sessions;
    private final PasswordResetService passwordReset;

    public AuthController(RegistrationService registration, LoginService login,
                          DeviceSessionService sessions, PasswordResetService passwordReset) {
        this.registration = registration;
        this.login = login;
        this.sessions = sessions;
        this.passwordReset = passwordReset;
    }

    @PostMapping("/register/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthDtos.ChallengeResponse registerStart(@Valid @RequestBody AuthDtos.RegisterStartRequest body) {
        if (!body.target().hasExactlyOne()) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE, "Provide exactly one of email or phone");
        }
        UUID id = registration.start(body.target().email(), body.target().phone());
        return new AuthDtos.ChallengeResponse(id);
    }

    @PostMapping("/register/complete")
    public AuthDtos.TokenResponse registerComplete(@Valid @RequestBody AuthDtos.RegisterCompleteRequest body,
                                                   HttpServletRequest http) {
        AuthDtos.PrimaryPatientProfileRequest p = body.profile();
        NewPatientProfile primaryProfile = new NewPatientProfile(
                p.fullName(), p.dateOfBirth(), p.sex(),
                null, null, null, null, null);
        PatientDtos.AddressRequest a = body.address();
        AddressData address = new AddressData(
                a.line1(), a.line2(), a.city(), a.state(), a.postalCode(), a.country());
        IssuedSession s = registration.complete(
                body.challengeId(), body.code(), body.password(),
                HttpClientInfo.enrich(body.device(), http),
                primaryProfile, address);
        return toToken(s);
    }

    @PostMapping("/login")
    public AuthDtos.TokenResponse login(@Valid @RequestBody AuthDtos.LoginRequest body, HttpServletRequest http) {
        LoginService.Result result = login.authenticate(
                body.emailOrPhone(), body.password(),
                HttpClientInfo.enrich(body.device(), http));
        return switch (result) {
            case LoginService.Result.Success s -> toToken(s.session());
            // A locked account is reported as plain invalid credentials (same 401, same body) so
            // the lockout state can't be used to confirm an account exists (audit H2). The lockout
            // is still enforced internally; the holder recovers via the lockout window or reset.
            case LoginService.Result.Locked l -> throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Invalid credentials");
            case LoginService.Result.Invalid i -> throw new UnauthorizedException(ErrorCode.UNAUTHORIZED, "Invalid credentials");
        };
    }

    @PostMapping("/refresh")
    public AuthDtos.TokenResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest body, HttpServletRequest http) {
        IssuedSession s = sessions.rotate(
                body.refreshToken(),
                new com.healyn.auth.service.DeviceMeta(null, null, null,
                        HttpClientInfo.clientIp(http), http.getHeader("User-Agent")));
        return toToken(s);
    }

    @PostMapping("/password-reset/start")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuthDtos.ChallengeResponse passwordResetStart(@Valid @RequestBody AuthDtos.PasswordResetStartRequest body) {
        if (!body.target().hasExactlyOne()) {
            throw new UnprocessableException(ErrorCode.UNPROCESSABLE, "Provide exactly one of email or phone");
        }
        UUID id = passwordReset.start(body.target().email(), body.target().phone());
        return new AuthDtos.ChallengeResponse(id);
    }

    @PostMapping("/password-reset/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void passwordResetComplete(@Valid @RequestBody AuthDtos.PasswordResetCompleteRequest body) {
        passwordReset.complete(body.challengeId(), body.code(), body.newPassword());
    }

    static AuthDtos.TokenResponse toToken(IssuedSession s) {
        return new AuthDtos.TokenResponse(
                s.sessionId(), s.accessToken(), s.accessTokenExpiresAt(),
                s.refreshToken(), s.refreshTokenExpiresAt());
    }
}
