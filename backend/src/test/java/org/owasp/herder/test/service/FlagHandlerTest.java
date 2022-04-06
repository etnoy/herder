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
package org.owasp.herder.test.service;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.CryptoService;
import org.owasp.herder.exception.InvalidFlagStateException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.test.util.TestConstants;
import org.owasp.herder.user.UserEntity;
import org.owasp.herder.user.UserService;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("FlagHandler unit tests")
class FlagHandlerTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private FlagHandler flagHandler;

  @Mock private ModuleService moduleService;

  @Mock private UserService userService;

  @Mock private ConfigurationService configurationService;

  @Mock private CryptoService cryptoService;

  @Mock private FlagSubmissionRateLimiter flagSubmissionRateLimiter;

  @Mock private InvalidFlagRateLimiter invalidFlagRateLimiter;

  @Test
  void getDynamicFlag_DynamicFlag_ReturnsFlag() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    final byte[] mockedServerKey = {
      -118, 17, 4, -35, 17, -3, -94, 0, -72, -17, 65, -127, 12, 82, 9, 29
    };

    final byte[] mockedUserKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19
    };
    final byte[] mockedModuleKey = {
      -118, 9, -7, -35, 15, -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19
    };

    final byte[] mockedHmacOutput = {
      -128, 1, -7, -35, 15, -116, -94, 0, -32, -117, 115, -127, 12, 82, 97, 19
    };

    final byte[] mockedTotalKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19, -118, 9, -7, -35, 15,
      -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19, 102, 108, 97, 103
    };
    final String correctFlag = "flag{qaa7txiprsrabyelooaqyutbcm}";

    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
        .thenReturn(Mono.just(mockModule));

    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockedUserKey));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));
    when(mockModule.getKey()).thenReturn(mockedModuleKey);

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    StepVerifier.create(
            flagHandler.getDynamicFlag(
                TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_LOCATOR))
        .expectNext(correctFlag)
        .verifyComplete();

    verify(userService, times(1)).findKeyById(TestConstants.TEST_USER_ID);
    verify(configurationService, times(1)).getServerKey();
    verify(mockModule, times(1)).getKey();

    verify(configurationService, times(1)).getServerKey();

    verify(cryptoService, times(1)).hmac(mockedServerKey, mockedTotalKey);
  }

  @Test
  void getDynamicFlag_FlagIsStatic_ReturnsInvalidFlagStateException() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    final byte[] mockedUserKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19
    };
    final byte[] mockedServerKey = {
      -118, 9, -7, -35, 17, -116, -94, 0, -32, -117, 65, -127, 12, 82, 9, 29
    };

    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
        .thenReturn(Mono.just(mockModule));

    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockedUserKey));
    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));
    when(mockModule.isFlagStatic()).thenReturn(true);

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    StepVerifier.create(
            flagHandler.getDynamicFlag(
                TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_LOCATOR))
        .expectError(InvalidFlagStateException.class)
        .verify();
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    flagHandler =
        new FlagHandler(
            moduleService,
            userService,
            configurationService,
            cryptoService,
            flagSubmissionRateLimiter,
            invalidFlagRateLimiter);
  }

  @Test
  void verifyFlag_CorrectDynamicFlag_ReturnsTrue() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final UserEntity mockUser = mock(UserEntity.class);

    final byte[] mockedServerKey = {
      -118, 17, 4, -35, 17, -3, -94, 0, -72, -17, 65, -127, 12, 82, 9, 29
    };

    final byte[] mockedUserKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19
    };
    final byte[] mockedModuleKey = {
      -118, 9, -7, -35, 15, -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19
    };

    final byte[] mockedHmacOutput = {
      -128, 1, -7, -35, 15, -116, -94, 0, -32, -117, 115, -127, 12, 82, 97, 19
    };

    final byte[] mockedTotalKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19, -118, 9, -7, -35, 15,
      -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19, 102, 108, 97, 103
    };

    final String correctFlag = "flag{qaa7txiprsrabyelooaqyutbcm}";

    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getKey()).thenReturn(mockedModuleKey);

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));
    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
        .thenReturn(Mono.just(mockModule));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockedUserKey));

    // Bypass all rate limiters
    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    when(mockModule.getLocator()).thenReturn(TestConstants.TEST_MODULE_LOCATOR);

    StepVerifier.create(
            flagHandler.verifyFlag(
                TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, correctFlag))
        .expectNext(true)
        .verifyComplete();

    verify(moduleService, atLeast(1)).findById(TestConstants.TEST_MODULE_ID);
    verify(mockModule, atLeast(1)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, atLeast(1)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, atLeast(1)).findKeyById(TestConstants.TEST_USER_ID);
  }

  @Test
  void verifyFlag_CorrectDynamicFlagWithSpacesInTheMiddle_ReturnsFalse() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final UserEntity mockUser = mock(UserEntity.class);

    final byte[] mockedServerKey = {
      -118, 17, 4, -35, 17, -3, -94, 0, -72, -17, 65, -127, 12, 82, 9, 29
    };

    final byte[] mockedUserKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19
    };
    final byte[] mockedModuleKey = {
      -118, 9, -7, -35, 15, -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19
    };

    final byte[] mockedHmacOutput = {
      -128, 1, -7, -35, 15, -116, -94, 0, -32, -117, 115, -127, 12, 82, 97, 19
    };

    final byte[] mockedTotalKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19, -118, 9, -7, -35, 15,
      -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19, 102, 108, 97, 103
    };

    final String correctFlagWithSpacesInside = "flag{qaa7   txiprsr abyelooa qyutbcm}";

    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getKey()).thenReturn(mockedModuleKey);
    when(mockModule.getLocator()).thenReturn(TestConstants.TEST_MODULE_LOCATOR);

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));
    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
        .thenReturn(Mono.just(mockModule));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockedUserKey));

    // Bypass all rate limiters
    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(TestConstants.TEST_USER_ID)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(
                TestConstants.TEST_USER_ID,
                TestConstants.TEST_MODULE_ID,
                correctFlagWithSpacesInside))
        .expectNext(false)
        .verifyComplete();

    verify(moduleService, atLeast(1)).findById(TestConstants.TEST_MODULE_ID);
    verify(mockModule, atLeast(1)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, atLeast(1)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, atLeast(1)).findKeyById(TestConstants.TEST_USER_ID);
  }

  @Test
  void verifyFlag_CorrectLowerCaseStaticFlag_ReturnsTrue() {
    final String validStaticFlag = "validFlagWithUPPERCASEandlowercase";

    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final UserEntity mockUser = mock(UserEntity.class);

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(
                TestConstants.TEST_USER_ID,
                TestConstants.TEST_MODULE_ID,
                validStaticFlag.toLowerCase()))
        .expectNext(true)
        .verifyComplete();

    verify(moduleService, times(1)).findById(TestConstants.TEST_MODULE_ID);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_CorrectStaticFlag_ReturnsTrue() {
    final String validStaticFlag = "validFlag";

    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final UserEntity mockUser = mock(UserEntity.class);

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(
                TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, validStaticFlag))
        .expectNext(true)
        .verifyComplete();

    verify(moduleService, times(1)).findById(TestConstants.TEST_MODULE_ID);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_CorrectStaticFlagWithSpacesInTheMiddle_ReturnsFalse() {
    final String validStaticFlag = "validFlag";

    final String validStaticFlagWithSpaces = "valid   Flag";

    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final UserEntity mockUser = mock(UserEntity.class);

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(TestConstants.TEST_USER_ID)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(
                TestConstants.TEST_USER_ID,
                TestConstants.TEST_MODULE_ID,
                validStaticFlagWithSpaces))
        .expectNext(false)
        .verifyComplete();

    verify(moduleService, times(1)).findById(TestConstants.TEST_MODULE_ID);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_CorrectUpperCaseDynamicFlag_ReturnsTrue() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final UserEntity mockUser = mock(UserEntity.class);

    final byte[] mockedServerKey = {
      -118, 17, 4, -35, 17, -3, -94, 0, -72, -17, 65, -127, 12, 82, 9, 29
    };

    final byte[] mockedUserKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19
    };
    final byte[] mockedModuleKey = {
      -118, 9, -7, -35, 15, -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19
    };

    final byte[] mockedHmacOutput = {
      -128, 1, -7, -35, 15, -116, -94, 0, -32, -117, 115, -127, 12, 82, 97, 19
    };

    final byte[] mockedTotalKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19, -118, 9, -7, -35, 15,
      -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19, 102, 108, 97, 103
    };

    final String correctFlag = "flag{qaa7txiprsrabyelooaqyutbcm}";

    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getKey()).thenReturn(mockedModuleKey);
    when(mockModule.getLocator()).thenReturn(TestConstants.TEST_MODULE_LOCATOR);

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));
    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
        .thenReturn(Mono.just(mockModule));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockedUserKey));

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(
                TestConstants.TEST_USER_ID,
                TestConstants.TEST_MODULE_ID,
                correctFlag.toUpperCase()))
        .expectNext(true)
        .verifyComplete();

    verify(moduleService, atLeast(1)).findById(TestConstants.TEST_MODULE_ID);
    verify(mockModule, atLeast(1)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, atLeast(1)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, atLeast(1)).findKeyById(TestConstants.TEST_USER_ID);
  }

  @Test
  void verifyFlag_CorrectUpperCaseStaticFlag_ReturnsTrue() {
    final String mockModuleId = "module-id";
    final String validStaticFlag = "validFlagWithUPPERCASEandlowercase";
    final UserEntity mockUser = mock(UserEntity.class);

    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(moduleService.findById(mockModuleId)).thenReturn(Mono.just(mockModule));

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(
                TestConstants.TEST_USER_ID, mockModuleId, validStaticFlag.toUpperCase()))
        .expectNext(true)
        .verifyComplete();

    verify(moduleService, times(1)).findById(mockModuleId);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_EmptyDynamicFlag_ReturnsFalse() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final UserEntity mockUser = mock(UserEntity.class);

    final byte[] mockedServerKey = {
      -118, 17, 4, -35, 17, -3, -94, 0, -72, -17, 65, -127, 12, 82, 9, 29
    };

    final byte[] mockedUserKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19
    };
    final byte[] mockedModuleKey = {
      -118, 9, -7, -35, 15, -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19
    };

    final byte[] mockedHmacOutput = {
      -128, 1, -7, -35, 15, -116, -94, 0, -32, -117, 115, -127, 12, 82, 97, 19
    };

    final byte[] mockedTotalKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19, -118, 9, -7, -35, 15,
      -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19, 102, 108, 97, 103
    };

    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getKey()).thenReturn(mockedModuleKey);
    when(mockModule.getLocator()).thenReturn(TestConstants.TEST_MODULE_LOCATOR);

    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockedUserKey));

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));
    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
        .thenReturn(Mono.just(mockModule));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    // Bypass all rate limiters
    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(TestConstants.TEST_USER_ID)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, ""))
        .expectNext(false)
        .verifyComplete();

    verify(moduleService, times(1)).findById(TestConstants.TEST_MODULE_ID);

    // TODO: this is too many interactions, why 4?
    verify(mockModule, times(4)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, times(2)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, times(2)).findKeyById(TestConstants.TEST_USER_ID);
  }

  @Test
  void verifyFlag_EmptyStaticFlag_ReturnsFalse() {
    final String validStaticFlag = "validFlag";
    final UserEntity mockUser = mock(UserEntity.class);

    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(TestConstants.TEST_USER_ID)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, ""))
        .expectNext(false)
        .verifyComplete();

    verify(moduleService, times(1)).findById(TestConstants.TEST_MODULE_ID);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_WrongDynamicFlag_ReturnsFalse() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final UserEntity mockUser = mock(UserEntity.class);

    final byte[] mockedServerKey = {
      -118, 17, 4, -35, 17, -3, -94, 0, -72, -17, 65, -127, 12, 82, 9, 29
    };

    final byte[] mockedUserKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19
    };
    final byte[] mockedModuleKey = {
      -118, 9, -7, -35, 15, -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19
    };

    final byte[] mockedHmacOutput = {
      -128, 1, -7, -35, 15, -116, -94, 0, -32, -117, 115, -127, 12, 82, 97, 19
    };

    final byte[] mockedTotalKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19, -118, 9, -7, -35, 15,
      -116, -94, 0, -32, -117, 65, -127, 12, 82, 97, 19, 102, 108, 97, 103
    };

    when(mockModule.isFlagStatic()).thenReturn(false);
    when(mockModule.getKey()).thenReturn(mockedModuleKey);
    when(mockModule.getLocator()).thenReturn(TestConstants.TEST_MODULE_LOCATOR);

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));
    when(moduleService.findByLocator(TestConstants.TEST_MODULE_LOCATOR))
        .thenReturn(Mono.just(mockModule));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    when(userService.findKeyById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockedUserKey));

    // Bypass all rate limiters
    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(TestConstants.TEST_USER_ID)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(
                TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, "invalidFlag"))
        .expectNext(false)
        .verifyComplete();

    verify(moduleService, atLeast(1)).findById(TestConstants.TEST_MODULE_ID);
    verify(mockModule, atLeast(1)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, atLeast(1)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, atLeast(1)).findKeyById(TestConstants.TEST_USER_ID);
  }

  @Test
  void verifyFlag_WrongStaticFlag_ReturnsFalse() {
    final String validStaticFlag = "validFlag";

    final ModuleEntity mockModule = mock(ModuleEntity.class);
    final UserEntity mockUser = mock(UserEntity.class);

    when(moduleService.findById(TestConstants.TEST_MODULE_ID)).thenReturn(Mono.just(mockModule));

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    when(userService.getById(TestConstants.TEST_USER_ID)).thenReturn(Mono.just(mockUser));
    when(mockUser.getDisplayName()).thenReturn("MockUser");

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(TestConstants.TEST_USER_ID))
        .thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(TestConstants.TEST_USER_ID)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(
                TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_ID, "invalidFlag"))
        .expectNext(false)
        .verifyComplete();

    verify(moduleService, times(1)).findById(TestConstants.TEST_MODULE_ID);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }
}
