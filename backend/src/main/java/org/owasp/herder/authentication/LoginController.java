/*
 * Copyright 2018-2022 Jonathan Jogenfors, jonathan@jogenfors.se
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.owasp.herder.authentication;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.owasp.herder.authentication.LoginResponse.LoginResponseBuilder;
import org.owasp.herder.crypto.WebTokenService;
import org.owasp.herder.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/")
@Validated
public class LoginController {
  private final UserService userService;

  private final WebTokenService webTokenService;

  private final PasswordEncoder passwordEncoder;

  private final ControllerAuthentication controllerAuthentication;

  @PostMapping(value = "/login")
  public Mono<ResponseEntity<LoginResponse>> login(@RequestBody @Valid PasswordLoginDto loginDto) {
    final LoginResponseBuilder loginResponseBuilder = LoginResponse.builder();
    return userService
        .authenticate(loginDto.getUserName(), loginDto.getPassword())
        .map(
            authResponse -> {
              final String accessToken =
                  webTokenService.generateToken(authResponse.getUserId(), authResponse.isAdmin());
              final LoginResponse loginResponse =
                  loginResponseBuilder
                      .accessToken(accessToken)
                      .displayName(authResponse.getDisplayName())
                      .build();
              return new ResponseEntity<>(loginResponse, HttpStatus.OK);
            })
        .onErrorResume(
            AuthenticationException.class,
            throwable -> {
              final LoginResponse loginResponse =
                  loginResponseBuilder.errorMessage(throwable.getMessage()).build();
              return Mono.just(new ResponseEntity<>(loginResponse, HttpStatus.UNAUTHORIZED));
            });
  }

  @PostMapping(path = "/impersonate")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Mono<ResponseEntity<LoginResponse>> impersonate(
      @Valid @RequestBody final ImpersonationDto impersonatedUserId) {
    final LoginResponseBuilder loginResponseBuilder = LoginResponse.builder();
    return controllerAuthentication
        .getUserId()
        .map(
            userId ->
                webTokenService.generateImpersonationToken(
                    userId, impersonatedUserId.getImpersonatedId(), false))
        .zipWith(userService.getById(impersonatedUserId.getImpersonatedId()))
        .map(
            tuple -> {
              final LoginResponse loginResponse =
                  loginResponseBuilder
                      .accessToken(tuple.getT1())
                      .displayName(tuple.getT2().getDisplayName())
                      .build();
              return new ResponseEntity<>(loginResponse, HttpStatus.OK);
            });
  }

  @PostMapping(path = "/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<Void> register(@Valid @RequestBody final PasswordRegistrationDto registrationDto) {
    return userService
        .createPasswordUser(
            registrationDto.getDisplayName(),
            registrationDto.getUserName(),
            passwordEncoder.encode(registrationDto.getPassword()))
        .then();
  }
}
