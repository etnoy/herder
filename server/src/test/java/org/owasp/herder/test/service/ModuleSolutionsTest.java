/* 
 * Copyright 2018-2021 Jonathan Jogenfors, jonathan@jogenfors.se
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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.owasp.herder.exception.EmptyModuleNameException;
import org.owasp.herder.exception.InvalidUserIdException;
import org.owasp.herder.module.Module;
import org.owasp.herder.module.ModuleListItem;
import org.owasp.herder.module.ModuleListItem.ModuleListItemBuilder;
import org.owasp.herder.module.ModuleService;
import org.owasp.herder.module.ModuleSolutions;
import org.owasp.herder.scoring.Submission;
import org.owasp.herder.scoring.SubmissionService;
import org.owasp.herder.test.util.TestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModuleSolutions unit test")
class ModuleSolutionsTest {

  @BeforeAll
  private static void reactorVerbose() {
    // Tell Reactor to print verbose error messages
    Hooks.onOperatorDebug();
  }

  private ModuleSolutions moduleSolutions;

  @Mock private ModuleService moduleService;

  @Mock private SubmissionService submissionService;

  @Test
  void findOpenModuleByIdWithSolutionStatus_EmptyModuleName_ReturnsInvalidModuleNameException() {
    final long mockUserId = 690L;
    StepVerifier.create(moduleSolutions.findOpenModuleByIdWithSolutionStatus(mockUserId, ""))
        .expectErrorMatches(
            throwable ->
                throwable instanceof EmptyModuleNameException
                    && throwable.getMessage().equals("Module name cannot be empty"))
        .verify();
  }

  @Test
  void findOpenModuleByIdWithSolutionStatus_InvalidUserid_ReturnsInvalidUserIdException() {
    final String mockModuleName = "moduleName";
    for (final long userId : TestUtils.INVALID_IDS) {
      StepVerifier.create(
              moduleSolutions.findOpenModuleByIdWithSolutionStatus(userId, mockModuleName))
          .expectErrorMatches(
              throwable ->
                  throwable instanceof InvalidUserIdException
                      && throwable
                          .getMessage()
                          .equals("User id must be a strictly positive integer"))
          .verify();
    }
  }

  @Test
  void findOpenModuleByIdWithSolutionStatus_ModuleIsClosedAndHasSolution_ReturnsEmpty() {
    final String mockModuleName = "moduleName";

    final long mockUserId = 1000L;
    final Module mockModule = mock(Module.class);

    when(mockModule.isOpen()).thenReturn(false);

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    StepVerifier.create(
            moduleSolutions.findOpenModuleByIdWithSolutionStatus(mockUserId, mockModuleName))
        .expectComplete()
        .verify();

    verify(mockModule, never()).getId();
    verify(mockModule, times(2)).isOpen();

    verify(submissionService, never())
        .findAllValidByUserIdAndModuleName(mockUserId, mockModuleName);
    verify(moduleService, times(1)).findByName(mockModuleName);
  }

  @Test
  void findOpenModuleByIdWithSolutionStatus_ModuleIsOpenAndHasSolution_ReturnsModule() {
    final String mockModuleName = "moduleName";

    final long mockUserId = 1000L;
    final Module mockModule = mock(Module.class);

    when(mockModule.getName()).thenReturn(mockModuleName);
    when(mockModule.isOpen()).thenReturn(true);

    final Submission mockedSubmission = mock(Submission.class);

    when(submissionService.findAllValidByUserIdAndModuleName(mockUserId, mockModuleName))
        .thenReturn(Mono.just(mockedSubmission));

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    final ModuleListItemBuilder moduleListItemBuilder = ModuleListItem.builder();

    final ModuleListItem listItem =
        moduleListItemBuilder.name(mockModuleName).isSolved(true).build();

    StepVerifier.create(
            moduleSolutions.findOpenModuleByIdWithSolutionStatus(mockUserId, mockModuleName))
        .expectNext(listItem)
        .expectComplete()
        .verify();

    verify(mockModule, times(2)).isOpen();

    verify(submissionService, times(1))
        .findAllValidByUserIdAndModuleName(mockUserId, mockModuleName);
    verify(moduleService, times(1)).findByName(mockModuleName);
  }

  @Test
  void findOpenModuleByIdWithSolutionStatus_ModuleIsOpenAndHasNoSolution_ReturnsModule() {
    final String mockModuleName = "moduleName";

    final long mockUserId = 1000L;
    final Module mockModule = mock(Module.class);

    when(mockModule.getName()).thenReturn(mockModuleName);
    when(mockModule.isOpen()).thenReturn(true);

    when(submissionService.findAllValidByUserIdAndModuleName(mockUserId, mockModuleName))
        .thenReturn(Mono.empty());

    when(moduleService.findByName(mockModuleName)).thenReturn(Mono.just(mockModule));

    final ModuleListItemBuilder moduleListItemBuilder = ModuleListItem.builder();

    final ModuleListItem listItem =
        moduleListItemBuilder.name(mockModuleName).isSolved(false).build();

    StepVerifier.create(
            moduleSolutions.findOpenModuleByIdWithSolutionStatus(mockUserId, mockModuleName))
        .expectNext(listItem)
        .expectComplete()
        .verify();

    verify(mockModule, times(2)).getName();
    verify(mockModule, times(2)).isOpen();

    verify(submissionService, times(1))
        .findAllValidByUserIdAndModuleName(mockUserId, mockModuleName);
    verify(moduleService, times(1)).findByName(mockModuleName);
  }

  @Test
  void findOpenModuleByIdWithSolutionStatus_NullModuleName_ReturnsInvalidModuleNameException() {
    final Long mockUserId = 108L;
    StepVerifier.create(moduleSolutions.findOpenModuleByIdWithSolutionStatus(mockUserId, null))
        .expectErrorMatches(
            throwable ->
                throwable instanceof NullPointerException
                    && throwable.getMessage().equals("Module name cannot be null"))
        .verify();
  }

  @Test
  void findOpenModulesByUserIdWithSolutionStatus_InvalidUserid_ReturnsInvalidUserIdException() {
    for (final long userId : TestUtils.INVALID_IDS) {
      StepVerifier.create(moduleSolutions.findOpenModulesByUserIdWithSolutionStatus(userId))
          .expectError(InvalidUserIdException.class)
          .verify();
    }
  }

  @Test
  void findOpenModulesByUserIdWithSolutionStatus_NoModulesOrSubmissions_ReturnsEmpty() {
    final long mockUserId = 1000L;

    when(submissionService.findAllValidModuleNamesByUserId(mockUserId)).thenReturn(Mono.empty());

    StepVerifier.create(moduleSolutions.findOpenModulesByUserIdWithSolutionStatus(mockUserId))
        .expectComplete()
        .verify();

    verify(submissionService, times(1)).findAllValidModuleNamesByUserId(mockUserId);
    verify(moduleService, never()).findAllOpen();
  }

  @Test
  void findOpenModulesByUserIdWithSolutionStatus_ValidSubmissions_ReturnsModules() {
    final String mockModule1Name = "id1";

    final String mockModule2Name = "id2";

    final String mockModule3Id = "id3";

    final long mockUserId = 1000L;
    final Module mockModule1 = mock(Module.class);
    final Module mockModule2 = mock(Module.class);
    final Module mockModule3 = mock(Module.class);
    final Module mockModule4 = mock(Module.class);

    when(mockModule1.getName()).thenReturn(mockModule1Name);
    when(mockModule2.getName()).thenReturn(mockModule2Name);

    final Mono<List<String>> mockedValidSolutionsList =
        Mono.just(
            new ArrayList<String>(Arrays.asList(mockModule1Name, mockModule2Name, mockModule3Id)));

    when(submissionService.findAllValidModuleNamesByUserId(mockUserId))
        .thenReturn(mockedValidSolutionsList);

    when(moduleService.findAllOpen()).thenReturn(Flux.just(mockModule1, mockModule2));

    final ModuleListItemBuilder moduleListItemBuilder = ModuleListItem.builder();

    final ModuleListItem listItem1 =
        moduleListItemBuilder.name(mockModule1Name).isSolved(true).build();

    final ModuleListItem listItem2 =
        moduleListItemBuilder.name(mockModule2Name).isSolved(true).build();

    StepVerifier.create(moduleSolutions.findOpenModulesByUserIdWithSolutionStatus(mockUserId))
        .expectNext(listItem1)
        .expectNext(listItem2)
        .expectComplete()
        .verify();

    verify(mockModule1, times(1)).getName();

    verify(mockModule1, never()).isOpen();

    verify(mockModule2, times(1)).getName();

    verify(mockModule2, never()).isOpen();

    verify(mockModule3, never()).getName();

    verify(mockModule3, never()).isOpen();

    verify(mockModule4, never()).getName();

    verify(mockModule4, never()).isOpen();

    verify(submissionService, times(1)).findAllValidModuleNamesByUserId(mockUserId);
    verify(moduleService, times(1)).findAllOpen();
  }

  @BeforeEach
  private void setUp() {
    // Set up the system under test
    moduleSolutions = new ModuleSolutions(moduleService, submissionService);
  }
}
