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
package org.owasp.herder.module;

import com.google.common.collect.Multimap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.owasp.herder.crypto.KeyService;
import org.owasp.herder.exception.DuplicateModuleLocatorException;
import org.owasp.herder.exception.DuplicateModuleNameException;
import org.owasp.herder.exception.ModuleNotFoundException;
import org.owasp.herder.user.ModuleListRepository;
import org.owasp.herder.user.UserRepository;
import org.owasp.herder.validation.ValidFlag;
import org.owasp.herder.validation.ValidModuleBaseScore;
import org.owasp.herder.validation.ValidModuleBonusScore;
import org.owasp.herder.validation.ValidModuleBonusScores;
import org.owasp.herder.validation.ValidModuleId;
import org.owasp.herder.validation.ValidModuleLocator;
import org.owasp.herder.validation.ValidModuleName;
import org.owasp.herder.validation.ValidModuleTags;
import org.owasp.herder.validation.ValidTeamId;
import org.owasp.herder.validation.ValidUserId;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Validated
@RequiredArgsConstructor
@Service
public class ModuleService {

  private final ModuleRepository moduleRepository;

  private final KeyService keyService;

  private final ModuleListRepository moduleListRepository;

  private final UserRepository userRepository;

  public Mono<ModuleEntity> close(@ValidModuleId final String moduleId) {
    return getById(moduleId).map(module -> module.withOpen(false)).flatMap(moduleRepository::save);
  }

  public Mono<Long> count() {
    return moduleRepository.count();
  }

  public Mono<String> create(@ValidModuleName final String moduleName, @ValidModuleLocator final String moduleLocator) {
    log.info("Creating new module " + moduleName + " with locator " + moduleLocator);

    return Mono
      .just(moduleLocator)
      // Check if locator already exists
      .filterWhen(this::doesNotExistByLocator)
      .switchIfEmpty(
        // Locator already exists, return error
        Mono.error(
          new DuplicateModuleLocatorException(String.format("Module locator %s already exists", moduleLocator))
        )
      )
      .map(exists -> moduleName)
      // Check if name already exists
      .filterWhen(this::doesNotExistByName)
      .switchIfEmpty(
        // Name exists, return error
        Mono.error(new DuplicateModuleNameException(String.format("Module name %s already exists", moduleName)))
      )
      // Name and locator don't exist already, create new module
      .map(exists ->
        ModuleEntity
          .builder()
          .isOpen(true)
          .locator(moduleLocator)
          .name(moduleName)
          // Generate the secret key
          .key(keyService.generateRandomBytes(16))
          .build()
      )
      // Persist the module in the database
      .flatMap(moduleRepository::save)
      // Return the created module id
      .map(ModuleEntity::getId);
  }

  private Mono<Boolean> doesNotExistByLocator(@ValidModuleLocator final String moduleLocator) {
    return findByLocator(moduleLocator).map(u -> false).defaultIfEmpty(true);
  }

  private Mono<Boolean> doesNotExistByName(final String moduleName) {
    return findByName(moduleName).map(u -> false).defaultIfEmpty(true);
  }

  public Mono<Boolean> existsByLocator(@ValidModuleLocator final String moduleLocator) {
    return findByLocator(moduleLocator).map(u -> true).defaultIfEmpty(false);
  }

  public Mono<Boolean> existsByName(@ValidModuleName final String moduleName) {
    return findByName(moduleName).map(u -> true).defaultIfEmpty(false);
  }

  public Flux<ModuleEntity> findAll() {
    return moduleRepository.findAll();
  }

  public Flux<ModuleList> findAllModuleLists() {
    return moduleListRepository.findAll();
  }

  public Flux<ModuleEntity> findAllOpen() {
    return moduleRepository.findAllOpen();
  }

  /**
   * Finds the specific module given by the module id. If the module id isn't found, it returns an
   * empty mono
   *
   * @param moduleId
   * @return the corresponding module entity
   */
  public Mono<ModuleEntity> findById(@ValidModuleId final String moduleId) {
    return moduleRepository.findById(moduleId);
  }

  public Mono<ModuleEntity> findByLocator(@ValidModuleLocator final String moduleLocator) {
    return moduleRepository.findByLocator(moduleLocator);
  }

  public Mono<ModuleEntity> findByName(final String moduleName) {
    return moduleRepository.findByName(moduleName);
  }

  public Mono<ModuleListItem> findListItemByUserIdAndLocator(
    @ValidUserId final String userId,
    @ValidModuleLocator final String moduleLocator
  ) {
    return moduleListRepository.findListItemByUserIdAndLocator(userId, moduleLocator);
  }

  public Mono<ModuleList> findModuleListByTeamId(@ValidTeamId final String teamId) {
    return moduleListRepository.findByTeamId(teamId);
  }

  public Mono<ModuleList> findModuleListByUserId(@ValidUserId final String userId) {
    return moduleListRepository.findById(userId);
  }

  /**
   * Finds the specific module given by the module id. If the module id isn't found, it returns a
   * mono exception
   *
   * @param moduleId
   * @return the corresponding module entity
   */
  public Mono<ModuleEntity> getById(@ValidModuleId final String moduleId) {
    return moduleRepository
      .findById(moduleId)
      .switchIfEmpty(Mono.error(new ModuleNotFoundException("Module id " + moduleId + " not found")));
  }

  /**
   * Finds the specific module given by the module id. If the module id isn't found, it returns a
   * mono exception
   *
   * @param moduleLocator
   * @return the corresponding module entity
   */
  public Mono<ModuleEntity> getByLocator(@ValidModuleLocator final String moduleLocator) {
    return moduleRepository
      .findByLocator(moduleLocator)
      .switchIfEmpty(Mono.error(new ModuleNotFoundException("Module locator " + moduleLocator + " not found")));
  }

  public Mono<ModuleEntity> open(@ValidModuleId final String moduleId) {
    return getById(moduleId).map(module -> module.withOpen(true)).flatMap(moduleRepository::save);
  }

  public Mono<ModuleEntity> setBaseScore(
    @ValidModuleId final String moduleId,
    @ValidModuleBaseScore final int baseScore
  ) {
    return getById(moduleId).map(module -> module.withBaseScore(baseScore)).flatMap(moduleRepository::save);
  }

  public Mono<ModuleEntity> setBonusScores(
    @ValidModuleId final String moduleId,
    @ValidModuleBonusScores final List<@ValidModuleBonusScore Integer> scores
  ) {
    return getById(moduleId).map(module -> module.withBonusScores(scores)).flatMap(moduleRepository::save);
  }

  public Mono<ModuleEntity> setDynamicFlag(@ValidModuleId final String moduleId) {
    return getById(moduleId).map(module -> module.withFlagStatic(false)).flatMap(moduleRepository::save);
  }

  public Mono<ModuleEntity> setStaticFlag(@ValidModuleId final String moduleId, @ValidFlag final String staticFlag) {
    return getById(moduleId)
      .map(module -> module.withFlagStatic(true).withStaticFlag(staticFlag))
      .flatMap(moduleRepository::save);
  }

  public Mono<Void> setTags(
    @ValidModuleId final String moduleId,
    @ValidModuleTags final Multimap<String, String> tags
  ) {
    return getById(moduleId).map(module -> module.withTags(tags)).flatMap(moduleRepository::save).then();
  }

  public Mono<Void> refreshModuleLists() {
    return userRepository.computeModuleLists().then();
  }
}
