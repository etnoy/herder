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
package org.owasp.herder.test.module;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.owasp.herder.flag.FlagHandler;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.Module;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.scoring.ScoreService;
import org.owasp.herder.test.util.TestConstants;

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("BaseModule unit tests")
class BaseModuleTest {
  private class TestModule extends BaseModule {

    protected TestModule(
        final String moduleName,
        final ModuleService moduleService,
        final ScoreService scoreService,
        final FlagHandler flagHandler,
        final String staticFlag) {
      super(moduleName, moduleService, scoreService, flagHandler, staticFlag);
    }

    protected TestModule(
        final String moduleName,
        final ModuleService moduleService,
        final ScoreService scoreService,
        final FlagHandler flagHandler) {
      super(moduleName, moduleService, scoreService, flagHandler);
    }

    @Override
    public Mono<Void> initialize() {
      return Mono.empty();
    }
  }

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Mock ModuleService moduleService;

  @Mock ScoreService scoreService;

  @Mock FlagHandler flagHandler;

  @Mock Module mockModule;

  @Test
  @DisplayName("The constructor should create a dynamic flag module")
  void constructor_DynamicFlag_ReturnsNewBaseModule() {
    when(moduleService.create(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.just(mockModule));

    TestModule dynamicFlagModule =
        new TestModule(TestConstants.TEST_MODULE_NAME, moduleService, scoreService, flagHandler);

    StepVerifier.create(dynamicFlagModule.getInit()).expectComplete().verify();

    assertThat(dynamicFlagModule.getModuleService()).isEqualTo(moduleService);
    assertThat(dynamicFlagModule.getFlagHandler()).isEqualTo(flagHandler);
  }

  @Test
  @DisplayName("The constructor should create a static flag module")
  void constructor_StaticFlag_ReturnsNewBaseModule() {
    final String testStaticFlag = "flag";

    when(moduleService.create(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.just(mockModule));
    when(moduleService.setStaticFlag(TestConstants.TEST_MODULE_NAME, testStaticFlag))
        .thenReturn(Mono.just(mockModule));

    TestModule staticFlagModule =
        new TestModule(
            TestConstants.TEST_MODULE_NAME,
            moduleService,
            scoreService,
            flagHandler,
            testStaticFlag);

    StepVerifier.create(staticFlagModule.getInit()).expectComplete().verify();

    assertThat(staticFlagModule.getModuleService()).isEqualTo(moduleService);
    assertThat(staticFlagModule.getFlagHandler()).isEqualTo(flagHandler);

    verify(moduleService, times(1)).setStaticFlag(TestConstants.TEST_MODULE_NAME, testStaticFlag);
  }

  @Test
  @DisplayName("getFlag on a dynamic module should return error when called without userid")
  void getFlag_DynamicFlagWithoutUserId_ReturnsError() {

    when(moduleService.create(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.just(mockModule));

    TestModule dynamicFlagModule =
        new TestModule(TestConstants.TEST_MODULE_NAME, moduleService, scoreService, flagHandler);

    dynamicFlagModule.getInit().block();

    when(mockModule.isFlagStatic()).thenReturn(false);

    StepVerifier.create(dynamicFlagModule.getFlag())
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  @DisplayName("getFlag on a dynamic module should return dynamic flag")
  void getFlag_DynamicFlagWithUserId_ReturnsFlag() {
    final String testDynamicFlag = "flag{123abc}";

    when(moduleService.create(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.just(mockModule));

    TestModule dynamicFlagModule =
        new TestModule(TestConstants.TEST_MODULE_NAME, moduleService, scoreService, flagHandler);

    when(flagHandler.getDynamicFlag(TestConstants.TEST_USER_ID, TestConstants.TEST_MODULE_NAME))
        .thenReturn(Mono.just(testDynamicFlag));

    dynamicFlagModule.getInit().block();

    when(mockModule.isFlagStatic()).thenReturn(false);

    StepVerifier.create(dynamicFlagModule.getFlag(TestConstants.TEST_USER_ID))
        .expectNext(testDynamicFlag)
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("getFlag on a static module should return static flag when called without userid")
  void getFlag_StaticFlagNoUserId_ReturnsFlag() {
    final String testStaticFlag = "flag";

    when(moduleService.create(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.just(mockModule));

    when(moduleService.setStaticFlag(TestConstants.TEST_MODULE_NAME, testStaticFlag))
        .thenReturn(Mono.just(mockModule));

    TestModule staticFlagModule =
        new TestModule(
            TestConstants.TEST_MODULE_NAME,
            moduleService,
            scoreService,
            flagHandler,
            testStaticFlag);

    staticFlagModule.getInit().block();

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(testStaticFlag);

    StepVerifier.create(staticFlagModule.getFlag())
        .expectNext(testStaticFlag)
        .expectComplete()
        .verify();
  }

  @Test
  @DisplayName("getFlag on a static module should return static flag when called with userid")
  void getFlag_StaticFlagWithUserId_ReturnsFlag() {
    final String testStaticFlag = "flag";

    when(moduleService.create(TestConstants.TEST_MODULE_NAME)).thenReturn(Mono.just(mockModule));

    when(moduleService.setStaticFlag(TestConstants.TEST_MODULE_NAME, testStaticFlag))
        .thenReturn(Mono.just(mockModule));

    TestModule staticFlagModule =
        new TestModule(
            TestConstants.TEST_MODULE_NAME,
            moduleService,
            scoreService,
            flagHandler,
            testStaticFlag);

    staticFlagModule.getInit().block();

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(testStaticFlag);

    StepVerifier.create(staticFlagModule.getFlag(TestConstants.TEST_USER_ID))
        .expectNext(testStaticFlag)
        .expectComplete()
        .verify();
  }

  @BeforeEach
  private void setUp() {
    mockModule = mock(Module.class);
  }
}
