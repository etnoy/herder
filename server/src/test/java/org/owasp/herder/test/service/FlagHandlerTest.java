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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.CryptoService;
import org.owasp.herder.exception.InvalidFlagStateException;
import org.owasp.herder.exception.InvalidUserIdException;
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.ModuleEntity;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.service.ConfigurationService;
import org.owasp.herder.service.FlagSubmissionRateLimiter;
import org.owasp.herder.service.InvalidFlagRateLimiter;
import org.owasp.herder.user.UserService;

import io.github.bucket4j.Bucket;
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
  void getDynamicFlag_FlagIsStatic_ReturnsInvalidFlagStateException() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    final String mockModuleName = "module-id";
    final long mockUserId = 7;

    final byte[] mockedUserKey = {
      -108, 101, -7, -35, 17, -16, -94, 0, -32, -117, 65, -127, 22, 62, 9, 19
    };
    final byte[] mockedServerKey = {
      -118, 9, -7, -35, 17, -116, -94, 0, -32, -117, 65, -127, 12, 82, 9, 29
    };

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(userService.findKeyById(mockUserId)).thenReturn(Mono.just(mockedUserKey));
    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));
    when(mockModule.isFlagStatic()).thenReturn(true);

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    StepVerifier.create(flagHandler.getDynamicFlag(mockUserId, mockModuleName))
        .expectError(InvalidFlagStateException.class)
        .verify();
  }

  @Test
  void getDynamicFlag_DynamicFlag_ReturnsFlag() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    final String mockModuleName = "module-id";
    final long mockUserId = 785;

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

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(userService.findKeyById(mockUserId)).thenReturn(Mono.just(mockedUserKey));
    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));
    when(mockModule.getKey()).thenReturn(mockedModuleKey);

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    StepVerifier.create(flagHandler.getDynamicFlag(mockUserId, mockModuleName))
        .expectNext(correctFlag)
        .expectComplete()
        .verify();

    verify(moduleService, times(1)).findByName(mockModuleName);

    verify(userService, times(1)).findKeyById(mockUserId);
    verify(configurationService, times(1)).getServerKey();
    verify(mockModule, times(1)).getKey();

    verify(configurationService, times(1)).getServerKey();

    verify(cryptoService, times(1)).hmac(mockedServerKey, mockedTotalKey);
  }

  @Test
  void getDynamicFlag_NegativeUserId_ReturnsInvalidUserIdException() {
    StepVerifier.create(flagHandler.getDynamicFlag(-1, "id"))
        .expectError(InvalidUserIdException.class)
        .verify();
    StepVerifier.create(flagHandler.getDynamicFlag(-1000, "id"))
        .expectError(InvalidUserIdException.class)
        .verify();
  }

  @Test
  void getDynamicFlag_ZeroUserId_ReturnsInvalidUserIdException() {
    StepVerifier.create(flagHandler.getDynamicFlag(0, "id"))
        .expectError(InvalidUserIdException.class)
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

    final long mockUserId = 158;
    final String mockModuleName = "module-id";
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

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    when(userService.findKeyById(mockUserId)).thenReturn(Mono.just(mockedUserKey));

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(flagHandler.verifyFlag(mockUserId, mockModuleName, correctFlag))
        .expectNext(true)
        .expectComplete()
        .verify();

    verify(moduleService, atLeast(1)).findByName(mockModuleName);
    verify(mockModule, atLeast(1)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, atLeast(1)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, atLeast(1)).findKeyById(mockUserId);
  }

  @Test
  void verifyFlag_CorrectStaticFlag_ReturnsTrue() {
    final long mockUserId = 225;
    final String mockModuleName = "module-id";
    final String validStaticFlag = "validFlag";

    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(flagHandler.verifyFlag(mockUserId, mockModuleName, validStaticFlag))
        .expectNext(true)
        .expectComplete()
        .verify();

    verify(moduleService, times(1)).findByName(mockModuleName);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_CorrectLowerCaseStaticFlag_ReturnsTrue() {
    final long mockUserId = 594;
    final String mockModuleName = "module-id";
    final String validStaticFlag = "validFlagWithUPPERCASEandlowercase";

    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(mockUserId, mockModuleName, validStaticFlag.toLowerCase()))
        .expectNext(true)
        .expectComplete()
        .verify();

    verify(moduleService, times(1)).findByName(mockModuleName);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_CorrectUpperCaseStaticFlag_ReturnsTrue() {
    final long mockUserId = 594;
    final String mockModuleName = "module-id";
    final String validStaticFlag = "validFlagWithUPPERCASEandlowercase";

    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(mockUserId, mockModuleName, validStaticFlag.toUpperCase()))
        .expectNext(true)
        .expectComplete()
        .verify();

    verify(moduleService, times(1)).findByName(mockModuleName);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_EmptyDynamicFlag_ReturnsFalse() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    final long mockUserId = 193;
    final String mockModuleName = "module-id";
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
    when(userService.findKeyById(mockUserId)).thenReturn(Mono.just(mockedUserKey));

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    StepVerifier.create(flagHandler.verifyFlag(mockUserId, mockModuleName, ""))
        // We expect this to return false
        .expectNext(false)
        .expectComplete()
        .verify();

    verify(moduleService, atLeast(1)).findByName(mockModuleName);

    // TODO: this is too many interactions, why 4?
    verify(mockModule, times(4)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, times(2)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, times(2)).findKeyById(mockUserId);
  }

  @Test
  void verifyFlag_EmptyStaticFlag_ReturnsFalse() {
    final long mockUserId = 709;
    final String mockModuleName = "module-id";
    final String validStaticFlag = "validFlag";

    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(flagHandler.verifyFlag(mockUserId, mockModuleName, ""))
        .expectNext(false)
        .expectComplete()
        .verify();

    verify(moduleService, times(1)).findByName(mockModuleName);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_NullDynamicFlag_ReturnsFalse() {
    final long mockUserId = 756;
    final String mockModuleName = "module-id";

    StepVerifier.create(flagHandler.verifyFlag(mockUserId, mockModuleName, null))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void verifyFlag_NullStaticFlag_ReturnsFalse() {
    final long mockUserId = 487;
    final String mockModuleName = "module-id";

    StepVerifier.create(flagHandler.verifyFlag(mockUserId, mockModuleName, null))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void verifyFlag_WrongDynamicFlag_ReturnsFalse() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    final long mockUserId = 193;
    final String mockModuleName = "module-id";
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

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    when(userService.findKeyById(mockUserId)).thenReturn(Mono.just(mockedUserKey));

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(flagHandler.verifyFlag(mockUserId, mockModuleName, "invalidFlag"))
        //
        .expectNext(false)
        //
        .expectComplete()
        .verify();

    verify(moduleService, atLeast(1)).findByName(mockModuleName);
    verify(mockModule, atLeast(1)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, atLeast(1)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, atLeast(1)).findKeyById(mockUserId);
  }

  @Test
  void verifyFlag_WrongStaticFlag_ReturnsFalse() {
    final long mockUserId = 709;
    final String mockModuleName = "module-id";
    final String validStaticFlag = "validFlag";

    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(flagHandler.verifyFlag(mockUserId, mockModuleName, "invalidFlag"))
        .expectNext(false)
        .expectComplete()
        .verify();

    verify(moduleService, times(1)).findByName(mockModuleName);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }

  @Test
  void verifyFlag_CorrectUpperCaseDynamicFlag_ReturnsTrue() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    final long mockUserId = 158;
    final String mockModuleName = "module-id";
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

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    when(userService.findKeyById(mockUserId)).thenReturn(Mono.just(mockedUserKey));

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(mockUserId, mockModuleName, correctFlag.toUpperCase()))
        .expectNext(true)
        .expectComplete()
        .verify();

    verify(moduleService, atLeast(1)).findByName(mockModuleName);
    verify(mockModule, atLeast(1)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, atLeast(1)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, atLeast(1)).findKeyById(mockUserId);
  }

  @Test
  void verifyFlag_CorrectDynamicFlagWithSpacesInTheMiddle_ReturnsFalse() {
    final ModuleEntity mockModule = mock(ModuleEntity.class);

    final long mockUserId = 73;
    final String mockModuleName = "module-id";
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

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(configurationService.getServerKey()).thenReturn(Mono.just(mockedServerKey));

    when(cryptoService.hmac(mockedServerKey, mockedTotalKey)).thenReturn(mockedHmacOutput);

    when(userService.findKeyById(mockUserId)).thenReturn(Mono.just(mockedUserKey));

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(mockUserId, mockModuleName, correctFlagWithSpacesInside))
        .expectNext(false)
        .expectComplete()
        .verify();

    verify(moduleService, atLeast(1)).findByName(mockModuleName);
    verify(mockModule, atLeast(1)).isFlagStatic();
    verify(mockModule, times(2)).getKey();
    verify(configurationService, atLeast(1)).getServerKey();
    verify(cryptoService, atLeast(1)).hmac(mockedServerKey, mockedTotalKey);
    verify(userService, atLeast(1)).findKeyById(mockUserId);
  }

  @Test
  void verifyFlag_CorrectStaticFlagWithSpacesInTheMiddle_ReturnsFalse() {
    final long mockUserId = 255;
    final String mockModuleName = "module-id";
    final String validStaticFlag = "validFlag";

    final String validStaticFlagWithSpaces = "valid   Flag";

    final ModuleEntity mockModule = mock(ModuleEntity.class);

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(userService.findDisplayNameById(mockUserId)).thenReturn(Mono.just("MockUser"));

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(validStaticFlag);

    final Bucket mockBucket = mock(Bucket.class);
    when(flagSubmissionRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(invalidFlagRateLimiter.resolveBucket(mockUserId)).thenReturn(mockBucket);
    when(mockBucket.tryConsume(1)).thenReturn(true);

    StepVerifier.create(
            flagHandler.verifyFlag(mockUserId, mockModuleName, validStaticFlagWithSpaces))
        .expectNext(false)
        .expectComplete()
        .verify();

    verify(moduleService, times(1)).findByName(mockModuleName);

    verify(mockModule, times(2)).isFlagStatic();
    verify(mockModule, times(2)).getStaticFlag();
  }
}
