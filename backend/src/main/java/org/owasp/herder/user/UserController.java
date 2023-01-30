/*
 * Copyright Jonathan Jogenfors, jonathan@jogenfors.se
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
package org.owasp.herder.user;

import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.exception.UserNotFoundException;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@Slf4j
@Validated
@RequestMapping("/api/v1/")
public class UserController {

  private final UserService userService;

  @PostMapping(path = "user/delete/{userId}")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Mono<Void> deleteById(@PathVariable @ValidUserId final String userId) {
    log.debug("Deleting user with id " + userId);

    return userService.delete(userId);
  }

  @GetMapping(path = "users")
  @PreAuthorize("hasRole('ROLE_ADMIN')")
  public Flux<UserEntity> findAll() {
    return userService.findAllUsers();
  }

  @GetMapping(path = "solvers")
  @PreAuthorize("hasRole('ROLE_USER')")
  public Flux<SolverEntity> findAllSolvers() {
    return userService.findAllSolvers();
  }

  @GetMapping(path = "user/{userId}")
  @PreAuthorize("(hasRole('ROLE_USER') and #userId == authentication.principal) or hasRole('ROLE_ADMIN')")
  public Mono<UserEntity> findById(@PathVariable final String userId) {
    Mono<UserEntity> userMono;
    try {
      userMono = userService.findById(userId);
    } catch (ConstraintViolationException e) {
      return Mono.error(new UserNotFoundException(e.getMessage()));
    }
    return userMono.switchIfEmpty(Mono.error(new UserNotFoundException()));
  }
}
