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
package org.owasp.herder.flag;

import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Bytes;
import io.github.bucket4j.Bucket;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.crypto.CryptoService;
import org.owasp.herder.exception.FlagSubmissionRateLimitException;
import org.owasp.herder.exception.InvalidFlagStateException;
import org.owasp.herder.exception.InvalidFlagSubmissionRateLimitException;
import org.owasp.herder.exception.ModuleNotFoundException;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import org.owasp.herder.validation.ValidModuleId;
import org.owasp.herder.validation.ValidModuleLocator;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Validated
@Service
public class FlagHandler {

  private static final String DYNAMIC_FLAG_FORMAT = "flag{%s}";

  private final ModuleService moduleService;

  private final UserService userService;

  private final ConfigurationService configurationService;

  private final CryptoService cryptoService;

  private final FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  private final InvalidFlagRateLimiter invalidFlagRateLimiter;

  public Mono<String> getDynamicFlag(@ValidUserId final String userId, @ValidModuleLocator final String moduleLocator) {
    return getSaltedHmac(userId, moduleLocator, "flag").map(flag -> String.format(DYNAMIC_FLAG_FORMAT, flag));
  }

  public Mono<String> getSaltedHmac(
    @ValidUserId final String userId,
    @ValidModuleLocator final String moduleLocator,
    @NotNull @NotEmpty final String prefix
  ) {
    final Mono<byte[]> moduleKey =
      // Find the module in the repo
      moduleService
        .findByLocator(moduleLocator)
        // Return error if module wasn't found
        .switchIfEmpty(Mono.error(new ModuleNotFoundException("Could not find module with locator " + moduleLocator)))
        // Make sure that the flag isn't static
        .filter(foundModule -> !foundModule.isFlagStatic())
        .switchIfEmpty(Mono.error(new InvalidFlagStateException("Cannot get dynamic flag if flag is static")))
        // Get module key and convert to bytes
        .map(ModuleEntity::getKey);

    final Mono<byte[]> userKey = userService.findKeyById(userId);

    final Mono<byte[]> serverKey = configurationService.getServerKey();

    return userKey
      .zipWith(moduleKey)
      .map(tuple -> Bytes.concat(tuple.getT1(), tuple.getT2(), prefix.getBytes()))
      .zipWith(serverKey)
      .map(tuple -> cryptoService.hmac(tuple.getT2(), tuple.getT1()))
      .map(BaseEncoding.base32().lowerCase().omitPadding()::encode);
  }

  public Mono<Boolean> verifyFlag(
    @ValidUserId final String userId,
    @ValidModuleId final String moduleId,
    @NotEmpty @NotNull final String submittedFlag
  ) {
    log.trace("Verifying flag " + submittedFlag + " submitted by userId " + userId + " to moduleId " + moduleId);

    // Check the rate limiter for flag submissions
    Bucket submissionBucket = flagSubmissionRateLimiter.resolveBucket(userId);
    if (!submissionBucket.tryConsume(1)) {
      // limit is exceeded
      return Mono.error(new FlagSubmissionRateLimitException());
    }

    // Get the module from the repository
    final Mono<ModuleEntity> moduleMono = moduleService.findById(moduleId);

    final Mono<Boolean> isValid = moduleMono
      // If the module wasn't found, return exception
      .switchIfEmpty(Mono.error(new ModuleNotFoundException("Module id " + moduleId + " not found")))
      // Check if the flag is valid
      .flatMap(module -> {
        if (module.isFlagStatic()) {
          // Verifying a static flag
          return Mono.just(module.getStaticFlag().equalsIgnoreCase(submittedFlag));
        } else {
          // Verifying a dynamic flag
          return getDynamicFlag(userId, module.getLocator()).map(submittedFlag::equalsIgnoreCase);
        }
      })
      .flatMap(validationResult -> {
        // Check the rate limiter if the flag was invalid
        if (!Boolean.TRUE.equals(validationResult)) {
          // flag is invalid
          Bucket invalidFlagBucket = invalidFlagRateLimiter.resolveBucket(userId);
          if (!invalidFlagBucket.tryConsume(1)) {
            // limit is exceeded
            return Mono.error(new InvalidFlagSubmissionRateLimitException());
          }
        }
        return Mono.just(validationResult);
      });

    // Do some logging. First, check if error occurred and then print logs
    final Mono<String> validText = isValid
      .onErrorReturn(false)
      .map(validFlag -> Boolean.TRUE.equals(validFlag) ? "valid" : "invalid");

    Mono
      .zip(userService.getById(userId).map(UserEntity::getDisplayName), validText, moduleMono.map(ModuleEntity::getId))
      .map(tuple ->
        "User " +
        tuple.getT1() +
        " submitted " +
        tuple.getT2() +
        " flag " +
        submittedFlag +
        " to module " +
        tuple.getT3()
      )
      .subscribe(log::debug);

    return isValid;
  }
}
