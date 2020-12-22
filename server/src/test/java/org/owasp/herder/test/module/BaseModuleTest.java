/* 
 * Copyright 2018-2020 Jonathan Jogenfors, jonathan@jogenfors.se
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.InvalidFlagStateException;
import org.owasp.herder.module.BaseModule;
import org.owasp.herder.module.FlagHandler;
import org.owasp.herder.module.Module;
import org.owasp.herder.module.ModuleService;

import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("BaseModule unit test")
class BaseModuleTest {

  @Mock ModuleService moduleService;

  @Mock FlagHandler flagHandler;

  private class TestModule extends BaseModule {

    protected TestModule(
        String moduleName,
        ModuleService moduleService,
        FlagHandler flagHandler,
        String staticFlag) {
      super(moduleName, moduleService, flagHandler, staticFlag);
    }
  }

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  @Test
  void constructor_DynamicFlag_ReturnsNewBaseModule() {
    final Module mockModule = mock(Module.class);
    final String mockModuleName = "test-module";

    when(moduleService.create(mockModuleName)).thenReturn(Mono.just(mockModule));

    TestModule dynamicFlagModule = new TestModule(mockModuleName, moduleService, flagHandler, null);

    StepVerifier.create(dynamicFlagModule.getInit()).expectComplete().verify();

    assertThat(dynamicFlagModule.getModuleService()).isEqualTo(moduleService);
    assertThat(dynamicFlagModule.getFlagHandler()).isEqualTo(flagHandler);
  }

  @Test
  void constructor_StaticFlag_ReturnsNewBaseModule() {
    final Module mockModule = mock(Module.class);
    final String mockModuleName = "test-module";
    final String mockStaticFlag = "flag";

    when(moduleService.create(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(moduleService.setStaticFlag(mockModuleName, mockStaticFlag))
        .thenReturn(Mono.just(mockModule));

    TestModule staticFlagModule =
        new TestModule(mockModuleName, moduleService, flagHandler, mockStaticFlag);

    StepVerifier.create(staticFlagModule.getInit()).expectComplete().verify();

    assertThat(staticFlagModule.getModuleService()).isEqualTo(moduleService);
    assertThat(staticFlagModule.getFlagHandler()).isEqualTo(flagHandler);

    verify(moduleService).setStaticFlag(mockModuleName, mockStaticFlag);
  }

  @Test
  void getFlag_StaticFlagNoUserId_ReturnsFlag() {
    final Module mockModule = mock(Module.class);
    final String mockModuleName = "test-module";
    final String mockStaticFlag = "flag";

    when(moduleService.create(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(moduleService.setStaticFlag(mockModuleName, mockStaticFlag))
        .thenReturn(Mono.just(mockModule));

    TestModule staticFlagModule =
        new TestModule(mockModuleName, moduleService, flagHandler, mockStaticFlag);

    staticFlagModule.getInit().block();

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(mockStaticFlag);

    StepVerifier.create(staticFlagModule.getFlag())
        .expectNext(mockStaticFlag)
        .expectComplete()
        .verify();
  }

  @Test
  void getFlag_StaticFlagWithUserId_ReturnsFlag() {
    final Module mockModule = mock(Module.class);
    final String mockModuleName = "test-module";
    final String mockStaticFlag = "flag";
    final Long mockUserId = 45L;

    when(moduleService.create(mockModuleName)).thenReturn(Mono.just(mockModule));

    when(moduleService.setStaticFlag(mockModuleName, mockStaticFlag))
        .thenReturn(Mono.just(mockModule));

    TestModule staticFlagModule =
        new TestModule(mockModuleName, moduleService, flagHandler, mockStaticFlag);

    staticFlagModule.getInit().block();

    when(mockModule.isFlagStatic()).thenReturn(true);
    when(mockModule.getStaticFlag()).thenReturn(mockStaticFlag);

    StepVerifier.create(staticFlagModule.getFlag(mockUserId))
        .expectNext(mockStaticFlag)
        .expectComplete()
        .verify();
  }

  @Test
  void getFlag_DynamicFlagWithUserId_ReturnsFlag() {
    final Module mockModule = mock(Module.class);
    final String mockModuleName = "test-module";
    final String mockStaticFlag = null;
    final String mockDynamicFlag = "flag{123abc}";

    final Long mockUserId = 45L;

    when(moduleService.create(mockModuleName)).thenReturn(Mono.just(mockModule));

    TestModule dynamicFlagModule =
        new TestModule(mockModuleName, moduleService, flagHandler, mockStaticFlag);

    when(flagHandler.getDynamicFlag(mockUserId, mockModuleName))
        .thenReturn(Mono.just(mockDynamicFlag));

    dynamicFlagModule.getInit().block();

    when(mockModule.isFlagStatic()).thenReturn(false);

    StepVerifier.create(dynamicFlagModule.getFlag(mockUserId))
        .expectNext(mockDynamicFlag)
        .expectComplete()
        .verify();
  }

  @Test
  void getFlag_DynamicFlagWithoutUserId_ReturnsError() {
    final Module mockModule = mock(Module.class);
    final String mockModuleName = "test-module";
    final String mockStaticFlag = null;

    when(moduleService.create(mockModuleName)).thenReturn(Mono.just(mockModule));

    TestModule dynamicFlagModule =
        new TestModule(mockModuleName, moduleService, flagHandler, mockStaticFlag);

    dynamicFlagModule.getInit().block();

    when(mockModule.isFlagStatic()).thenReturn(false);

    StepVerifier.create(dynamicFlagModule.getFlag())
        .expectError(InvalidFlagStateException.class)
        .verify();
  }
}
