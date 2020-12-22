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
package org.owasp.herder.test.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.exception.InvalidFlagException;
import org.owasp.herder.module.Module;
import org.owasp.herder.module.ModuleRepository;
import org.owasp.herder.module.ModuleService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleService unit test")
class ModuleServiceTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private ModuleService moduleService;

  @Mock private ModuleRepository moduleRepository;

  @Mock private KeyService keyService;

  @Test
  void count_NoArgument_ReturnsCount() {
    final long mockedModuleCount = 75L;

    when(moduleRepository.count()).thenReturn(Mono.just(mockedModuleCount));

    StepVerifier.create(moduleService.count())
        .expectNext(mockedModuleCount)
        .expectComplete()
        .verify();
    verify(moduleRepository).count();
  }

  @Test
  void create_NullModuleName_ThrowsException() {
    final String moduleName = null;

    when(moduleRepository.findByName(moduleName)).thenReturn(Mono.empty());

    StepVerifier.create(moduleService.create(moduleName))
        .expectError(NullPointerException.class)
        .verify();

    verify(moduleRepository).findByName(null);
  }

  @Test
  void create_NewModuleName_Succeeds() {
    final String moduleName = "test-module";

    final byte[] randomBytes = {120, 56, 111};
    when(keyService.generateRandomBytes(16)).thenReturn(randomBytes);

    when(moduleRepository.findByName(moduleName)).thenReturn(Mono.empty());

    when(moduleRepository.save(any(Module.class)))
        .thenAnswer(user -> Mono.just(user.getArgument(0, Module.class)));

    StepVerifier.create(moduleService.create(moduleName))
        .assertNext(module -> assertThat(module.getName()).hasToString("test-module"))
        .expectComplete()
        .verify();

    ArgumentCaptor<Module> argument = ArgumentCaptor.forClass(Module.class);

    verify(moduleRepository).save(argument.capture());
    verify(moduleRepository).save(any(Module.class));
    assertThat(argument.getValue().getName()).isEqualTo(moduleName);
  }

  @Test
  void create_ModuleNameExists_ReturnsExistingModule() {
    final String moduleName = "test-module";
    final Module mockModule = mock(Module.class);

    when(moduleRepository.findByName(moduleName)).thenReturn(Mono.just(mockModule));

    StepVerifier.create(moduleService.create(moduleName))
        .assertNext(module -> assertThat(module).isEqualTo(mockModule))
        .expectComplete()
        .verify();
  }

  @Test
  void findAll_ModulesExist_ReturnsModules() {
    final Module mockModule1 = mock(Module.class);
    final Module mockModule2 = mock(Module.class);
    final Module mockModule3 = mock(Module.class);

    when(moduleRepository.findAll()).thenReturn(Flux.just(mockModule1, mockModule2, mockModule3));

    StepVerifier.create(moduleService.findAll())
        .expectNext(mockModule1)
        .expectNext(mockModule2)
        .expectNext(mockModule3)
        .expectComplete()
        .verify();

    verify(moduleRepository).findAll();
  }

  @Test
  void findAll_NoModulesExist_ReturnsEmpty() {
    when(moduleRepository.findAll()).thenReturn(Flux.empty());
    StepVerifier.create(moduleService.findAll()).expectComplete().verify();
    verify(moduleRepository).findAll();
  }

  @Test
  void findAllOpen_NoModulesExist_ReturnsEmpty() {
    when(moduleRepository.findAllOpen()).thenReturn(Flux.empty());
    StepVerifier.create(moduleService.findAllOpen()).expectComplete().verify();
    verify(moduleRepository).findAllOpen();
  }

  @Test
  void findAllOpen_OpenModulesExist_ReturnsOpenModules() {
    final Module mockModule1 = mock(Module.class);
    final Module mockModule2 = mock(Module.class);
    final Module mockModule3 = mock(Module.class);

    when(moduleRepository.findAllOpen())
        .thenReturn(Flux.just(mockModule1, mockModule2, mockModule3));

    StepVerifier.create(moduleService.findAllOpen())
        .expectNext(mockModule1)
        .expectNext(mockModule2)
        .expectNext(mockModule3)
        .expectComplete()
        .verify();

    verify(moduleRepository).findAllOpen();
  }

  @Test
  void findByName_ModuleNameExists_ReturnsInvalidModuleNameException() {
    final Module mockModule = mock(Module.class);
    final String mockModuleName = "mock-module";

    when(moduleRepository.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));
    StepVerifier.create(moduleService.findByName(mockModuleName))
        .expectNext(mockModule)
        .expectComplete()
        .verify();
    verify(moduleRepository).findByName(mockModuleName);
  }

  @Test
  void findById_NonExistentModuleName_ReturnsEmpty() {
    final String mockModuleName = "mock-module";
    when(moduleRepository.findByName(mockModuleName)).thenReturn(Mono.empty());
    StepVerifier.create(moduleService.findByName(mockModuleName)).expectComplete().verify();
    verify(moduleRepository).findByName(mockModuleName);
  }

  @Test
  void setDynamicFlag_FlagPreviouslySet_ReturnPreviousFlag() {
    final byte[] newFlag = {-118, 17, 4, -35, 17, -3, -94, 0, -72, -17, 65, -127, 12, 82, 9, 29};

    final Module mockModuleWithStaticFlag = mock(Module.class);
    final Module mockModuleWithDynamicFlag = mock(Module.class);

    final String mockModuleName = "id";

    when(moduleRepository.findByName(mockModuleName))
        .thenReturn(Mono.just(mockModuleWithStaticFlag));

    when(mockModuleWithStaticFlag.withFlagStatic(false)).thenReturn(mockModuleWithDynamicFlag);

    when(mockModuleWithDynamicFlag.getKey()).thenReturn(newFlag);

    when(moduleRepository.save(mockModuleWithDynamicFlag))
        .thenReturn(Mono.just(mockModuleWithDynamicFlag));

    StepVerifier.create(moduleService.setDynamicFlag(mockModuleName))
        .assertNext(
            module -> {
              assertThat(module.getKey()).isEqualTo(newFlag);
            })
        .expectComplete()
        .verify();

    verify(moduleRepository).save(any(Module.class));
    verify(keyService, never()).generateRandomString(any(Integer.class));

    ArgumentCaptor<Module> argument = ArgumentCaptor.forClass(Module.class);
    verify(moduleRepository).save(argument.capture());
    assertThat(argument.getValue().getKey()).isEqualTo(newFlag);
  }

  @Test
  void setDynamicFlag_StaticFlagIsSet_SetsDynamicFlag() {
    final Module mockModuleWithStaticFlag = mock(Module.class);
    final Module mockModuleWithDynamicFlag = mock(Module.class);

    final String mockModuleName = "id";

    when(moduleRepository.findByName(mockModuleName))
        .thenReturn(Mono.just(mockModuleWithStaticFlag));

    when(mockModuleWithStaticFlag.withFlagStatic(false)).thenReturn(mockModuleWithDynamicFlag);

    when(mockModuleWithDynamicFlag.isFlagStatic()).thenReturn(false);

    when(moduleRepository.save(mockModuleWithDynamicFlag))
        .thenReturn(Mono.just(mockModuleWithDynamicFlag));

    StepVerifier.create(moduleService.setDynamicFlag(mockModuleName))
        .assertNext(
            module -> {
              assertThat(module.isFlagStatic()).isFalse();
            })
        .expectComplete()
        .verify();

    verify(mockModuleWithStaticFlag).withFlagStatic(false);
    verify(moduleRepository).save(any(Module.class));
  }

  @Test
  void setStaticFlag_EmptyStaticFlag_ReturnsInvalidFlagException() {
    StepVerifier.create(moduleService.setStaticFlag("id", ""))
        .expectError(InvalidFlagException.class)
        .verify();
  }

  @Test
  void setStaticFlag_NullStaticFlag_ReturnsNulPointerException() {
    StepVerifier.create(moduleService.setStaticFlag("id", null))
        .expectErrorMatches(
            throwable ->
                throwable instanceof NullPointerException
                    && throwable.getMessage().equals("Flag cannot be null"))
        .verify();
  }

  @Test
  void setStaticFlag_ValidStaticFlag_SetsFlagToStatic() {
    final String staticFlag = "setStaticFlag_ValidStaticFlag_SetsFlagToStatic";

    final Module mockModule = mock(Module.class);
    final Module mockModuleWithStaticFlag = mock(Module.class);
    final Module mockModuleWithStaticFlagEnabled = mock(Module.class);

    final String mockModuleName = "id";

    when(moduleRepository.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));
    when(mockModule.withFlagStatic(true)).thenReturn(mockModuleWithStaticFlag);
    when(mockModuleWithStaticFlag.withStaticFlag(staticFlag))
        .thenReturn(mockModuleWithStaticFlagEnabled);

    when(mockModuleWithStaticFlagEnabled.isFlagStatic()).thenReturn(true);
    when(mockModuleWithStaticFlagEnabled.getStaticFlag()).thenReturn(staticFlag);

    when(moduleRepository.save(mockModuleWithStaticFlagEnabled))
        .thenReturn(Mono.just(mockModuleWithStaticFlagEnabled));

    StepVerifier.create(moduleService.setStaticFlag(mockModuleName, staticFlag))
        .assertNext(
            module -> {
              assertThat(module.isFlagStatic()).isTrue();
              assertThat(module.getStaticFlag()).isEqualTo(staticFlag);
            })
        .expectComplete()
        .verify();

    ArgumentCaptor<String> findArgument = ArgumentCaptor.forClass(String.class);
    verify(moduleRepository).findByName(findArgument.capture());
    assertThat(findArgument.getValue()).isEqualTo(mockModuleName);

    ArgumentCaptor<Module> saveArgument = ArgumentCaptor.forClass(Module.class);
    verify(moduleRepository).save(saveArgument.capture());
    assertThat(saveArgument.getValue().getStaticFlag()).isEqualTo(staticFlag);
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    moduleService = new ModuleService(moduleRepository, keyService);
  }
}
