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
import org.owasp.herder.exception.InvalidFlagSubmissionRateLimitException;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.service.RateLimiter;
import org.owasp.herder.user.UserService;
import org.owasp.herder.validation.ValidFlag;
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

  private final RateLimiter flagSubmissionRateLimiter;

  private final RateLimiter invalidFlagRateLimiter;

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
        .getByLocator(moduleLocator)
        // Make sure that the flag isn't static
        .filter(foundModule -> !foundModule.isFlagStatic())
        .switchIfEmpty(Mono.error(new IllegalStateException("Cannot get dynamic flag if flag is static")))
        // Get module key and convert to bytes
        .map(ModuleEntity::getKey);

    final Mono<byte[]> userKey = userService.findKeyById(userId);

    final Mono<byte[]> serverKey = configurationService.getServerKey();

    return userKey
      .zipWith(moduleKey)
      .map(tuple -> Bytes.concat(prefix.getBytes(), tuple.getT1(), tuple.getT2()))
      .zipWith(serverKey)
      .map(tuple -> cryptoService.hmac(tuple.getT2(), tuple.getT1()))
      .map(BaseEncoding.base32().lowerCase().omitPadding()::encode);
  }

  public Mono<Boolean> verifyFlag(
    @ValidUserId final String userId,
    @ValidModuleId final String moduleId,
    @ValidFlag final String submittedFlag
  ) {
    log.trace("Verifying flag " + submittedFlag + " submitted by userId " + userId + " to moduleId " + moduleId);

    // Check the rate limiter for flag submissions
    Bucket submissionBucket = flagSubmissionRateLimiter.resolveBucket(userId);
    if (!submissionBucket.tryConsume(1)) {
      // limit is exceeded
      return Mono.error(new FlagSubmissionRateLimitException());
    }

    return moduleService
      .getById(moduleId)
      // Check if the flag is valid
      .flatMap(module -> {
        if (module.isFlagStatic()) {
          // Verifying a static flag
          return Mono.just(module.getStaticFlag().equalsIgnoreCase(submittedFlag.trim()));
        } else {
          // Verifying a dynamic flag
          return getDynamicFlag(userId, module.getLocator()).map(flag -> submittedFlag.trim().equalsIgnoreCase(flag));
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
  }
}
