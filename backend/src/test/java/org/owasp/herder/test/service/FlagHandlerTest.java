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
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.service.RateLimiter;
import org.owasp.herder.test.BaseTest;
import org.owasp.herder.test.util.BypassedRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserEntity;
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
    flagSubmissionRateLimiter = new BypassedRateLimiter();
    invalidFlagRateLimiter = new BypassedRateLimiter();
    // Set up the system under test
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

  // TODO: make this work with whitespace before and after flag
  static Stream<Arguments> dynamicFlags() {
    return Stream.of(
      arguments("flag{aqcqmbyibmfawdaqcf7q}", true),
      arguments("flag{asdfasdfasdfasdf}", false),
      arguments("herder{aqcqmbyibmfawdaqcf7q}", false),
      arguments("flag{aqcq  mbyib mfaw daqcf7q}", false),
      arguments("FLAG{AQCQMBYIBMFAWDAQCF7Q}", true),
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

    final UserEntity testUser = TestConstants.TEST_USER_ENTITY;

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(testUser));
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

  static final String staticTestFlag = "ValidStaticFlag";

  // TODO: make this work with whitespace before and after flag
  static Stream<Arguments> staticFlags() {
    return Stream.of(
      arguments(staticTestFlag, true),
      arguments(staticTestFlag.toUpperCase(), true),
      arguments(staticTestFlag.toLowerCase(), true),
      arguments("Valid Static Flag", false),
      arguments("wrongflag", false),
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

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(TestConstants.TEST_USER_ENTITY));
    when(moduleService.getById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(testModule));

    StepVerifier
      .create(flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, staticFlag))
      .expectNext(isValid)
      .verifyComplete();
  }
}
