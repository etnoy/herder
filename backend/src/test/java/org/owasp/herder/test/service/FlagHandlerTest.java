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
package org.owasp.herder.test.service;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.CryptoService;
import org.owasp.herder.exception.FlagSubmissionRateLimitException;
import org.owasp.herder.exception.InvalidFlagSubmissionRateLimitException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.service.RateLimiter;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.InfiniteCapacityRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.test.util.ZeroCapacityRateLimiter;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagHandler unit tests")
class FlagHandlerTest extends BaseTest {

  private FlagHandler flagHandler;

  @Mock
  private ModuleService moduleService;

  @Mock
  private UserService userService;

  @Mock
  private ConfigurationService configurationService;

  @Mock
  private CryptoService cryptoService;

  @Mock
  private RateLimiter flagSubmissionRateLimiter;

  @Mock
  private RateLimiter invalidFlagRateLimiter;

  @Test
  @DisplayName("Can get dynamic flag")
  void getDynamicFlag_DynamicFlag_ReturnsFlag() {
    final ModuleEntity testModule = TestConstants.TEST_MODULE_ENTITY
      .withFlagStatic(false)
      .withKey(TestConstants.TEST_BYTE_ARRAY3);

    when(moduleService.getByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(testModule));

    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(TestConstants.TEST_BYTE_ARRAY2));

    when(configurationService.getServerKey()).thenReturn(Mono.just(TestConstants.TEST_BYTE_ARRAY));
    when(cryptoService.hmac(eq(TestConstants.TEST_BYTE_ARRAY), any())).thenReturn(TestConstants.TEST_BYTE_ARRAY4);

    StepVerifier
      .create(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_LOCATOR))
      .expectNext("flag{aqcqmbyibmfawdaqcf7q}")
      .verifyComplete();
  }

  @Test
  @DisplayName("Can error when getting dynamic flag of static module")
  void getDynamicFlag_FlagIsStatic_ReturnsInvalidFlagStateException() {
    when(moduleService.getByLocator(TestConstants.TEST_MODULE_LOCATOR))
      .thenReturn(Mono.just(TestConstants.TEST_MODULE_ENTITY.withFlagStatic(true)));
    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(TestConstants.TEST_BYTE_ARRAY2));

    when(configurationService.getServerKey()).thenReturn(Mono.just(TestConstants.TEST_BYTE_ARRAY));

    StepVerifier
      .create(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_LOCATOR))
      .expectError(IllegalStateException.class)
      .verify();
  }

  @BeforeEach
  void setup() {
    setFlagSubmissionRateLimiter(new InfiniteCapacityRateLimiter());
    setInvalidFlagRateLimiter(new InfiniteCapacityRateLimiter());
  }

  void setFlagSubmissionRateLimiter(final RateLimiter rateLimiter) {
    flagSubmissionRateLimiter = rateLimiter;
    flagHandler =
      new FlagHandler(
        moduleService,
        userService,
        configurationService,
        cryptoService,
        flagSubmissionRateLimiter,
        invalidFlagRateLimiter
      );
  }

  void setInvalidFlagRateLimiter(final RateLimiter rateLimiter) {
    invalidFlagRateLimiter = rateLimiter;
    flagHandler =
      new FlagHandler(
        moduleService,
        userService,
        configurationService,
        cryptoService,
        flagSubmissionRateLimiter,
        invalidFlagRateLimiter
      );
  }

  static Stream<Arguments> dynamicFlags() {
    return Stream.of(
      // The valid dynamic flag
      arguments("flag{aqcqmbyibmfawdaqcf7q}", true),
      // The wrong flag
      arguments("blargh", false),
      // The wrong flag, but with the right decorator
      arguments("flag{asdfasdfasdfasdf}", false),
      // Correct flag, but with a space at the end
      arguments("flag{aqcqmbyibmfawdaqcf7q} ", true),
      // Correct flag, but with a space at the beginning end
      arguments(" flag{aqcqmbyibmfawdaqcf7q}", true),
      // Correct flag, but with a space at the beginning and end
      arguments(" flag{aqcqmbyibmfawdaqcf7q} ", true),
      // Correct flag, but with a tab at the end
      arguments("flag{aqcqmbyibmfawdaqcf7q}\t", true),
      // Correct flag, but the wrong decorator
      arguments("herder{aqcqmbyibmfawdaqcf7q}", false),
      // Correct flag, but with spaces in the middle
      arguments("flag{aqcq  mbyib mfaw daqcf7q}", false),
      // Correct flag in uppercase
      arguments("FLAG{AQCQMBYIBMFAWDAQCF7Q}", true),
      // Empty flag
      arguments("", false)
    );
  }

  @ParameterizedTest
  @MethodSource("dynamicFlags")
  @DisplayName("Can verify a dynamic flag")
  void verifyFlag_DynamicFlag_CorrectValidation(final String dynamicFlag, final boolean isValid) {
    final ModuleEntity testModule = TestConstants.TEST_MODULE_ENTITY
      .withFlagStatic(false)
      .withKey(TestConstants.TEST_BYTE_ARRAY3);

    when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(testModule));
    when(moduleService.getByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(testModule));
    when(configurationService.getServerKey()).thenReturn(Mono.just(TestConstants.TEST_BYTE_ARRAY));
    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(TestConstants.TEST_BYTE_ARRAY2));
    when(cryptoService.hmac(eq(TestConstants.TEST_BYTE_ARRAY), any())).thenReturn(TestConstants.TEST_BYTE_ARRAY4);

    StepVerifier
      .create(flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, dynamicFlag))
      .expectNext(isValid)
      .verifyComplete();
  }

  @ParameterizedTest
  @MethodSource("dynamicFlags")
  @DisplayName("Can error when rate limited on flag submission")
  void verifyFlag_FlagSubmissionRateLimited_RateLimitException(final String dynamicFlag, final boolean isValid) {
    setFlagSubmissionRateLimiter(new ZeroCapacityRateLimiter());

    flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID).tryConsume(1);

    StepVerifier
      .create(flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, dynamicFlag))
      .expectError(FlagSubmissionRateLimitException.class)
      .verify();
  }

  @Test
  @DisplayName("Can error when rate limited on flag submission")
  void verifyFlag_InvalidFlagRateLimited_RateLimitException() {
    setInvalidFlagRateLimiter(new ZeroCapacityRateLimiter());

    invalidFlagRateLimiter.resolveBucket(TestConstants.TEST_USER_ID).tryConsume(1);

    final ModuleEntity testModule = TestConstants.TEST_MODULE_ENTITY
      .withFlagStatic(false)
      .withKey(TestConstants.TEST_BYTE_ARRAY3);

    when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(testModule));
    when(moduleService.getByLocator(TestConstants.TEST_MODULE_LOCATOR)).thenReturn(Mono.just(testModule));
    when(configurationService.getServerKey()).thenReturn(Mono.just(TestConstants.TEST_BYTE_ARRAY));
    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(TestConstants.TEST_BYTE_ARRAY2));
    when(cryptoService.hmac(eq(TestConstants.TEST_BYTE_ARRAY), any())).thenReturn(TestConstants.TEST_BYTE_ARRAY4);

    StepVerifier
      .create(flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, ""))
      .expectError(InvalidFlagSubmissionRateLimitException.class)
      .verify();
  }

  static final String staticTestFlag = "ValidStaticFlag";

  static Stream<Arguments> staticFlags() {
    return Stream.of(
      // The correct flag
      arguments(staticTestFlag, true),
      // The correct flag in uppercase
      arguments(staticTestFlag.toUpperCase(), true),
      // The correct flag in lowercase
      arguments(staticTestFlag.toLowerCase(), true),
      // The correct flag with a space at the beginning
      arguments(" " + staticTestFlag, true),
      // The correct flag with a space at the end
      arguments(staticTestFlag + " ", true),
      // The correct flag with spaces at the beginning and end
      arguments(" " + staticTestFlag + " ", true),
      // The correct flag with spaces in the middle
      arguments("Valid Static Flag", false),
      // The wrong flag
      arguments("wrongflag", false),
      // The correct flag with a tab at the end
      arguments(staticTestFlag + "\t", true),
      // An empty flag
      arguments("", false)
    );
  }

  @ParameterizedTest
  @MethodSource("staticFlags")
  @DisplayName("Can verify static flags")
  void verifyFlag_StaticFlag_CorrectValidation(final String staticFlag, final boolean isValid) {
    final ModuleEntity testModule = TestConstants.TEST_MODULE_ENTITY
      .withFlagStatic(true)
      .withStaticFlag(staticTestFlag);

    when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(testModule));

    StepVerifier
      .create(flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, staticFlag))
      .expectNext(isValid)
      .verifyComplete();
  }
}
