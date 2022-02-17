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
package org.owasp.herder.module;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ModuleRepository extends ReactiveMongoRepository<ModuleEntity, String> {
  @Query("{ 'isOpen' : true }")
  public Flux<ModuleEntity> findAllOpen();

  @Aggregation({
    // Only show open modules
    "{$match:{'isOpen': true, 'name': ?1 }}",
    // Include all submissions per module
    "{$lookup:{from:'submission',localField:'_id',foreignField:'moduleId',as:'submissions'}}",
    // Include all tabs per module
    "{$lookup:{from:'moduleTag',localField:'_id',foreignField:'moduleId',as:'tags'}}",
    // Check if current user has solved the module
    "{$addFields:{isSolved: {$and: [{$in: [true, '$submissions.isValid']}, {$in: [ ?0 , '$submissions.userId']}]}}}",
    // Project only the required values
    "{$project:{_id: 0, name:1, displayName:1, isSolved:1, tags:{ $map: { 'input': '$tags', 'as': 'tag', in: { 'name': '$$tag.name', 'value': '$$tag.value'}}}}}"
  })
  public Mono<ModuleListItem> findByIdWithSolutionStatus(String userId, String moduleId);

  @Aggregation({
    // Only show open modules
    "{$match:{'isOpen': true }}",
    // Include all submissions per module
    "{$lookup:{from:'submission',localField:'name',foreignField:'moduleId',as:'submissions'}}",
    // Include all tabs per module
    "{$lookup:{from:'moduleTag',localField:'name',foreignField:'moduleId',as:'tags'}}",
    // Check if current user has solved the module
    "{$addFields:{isSolved: {$and: [{$in: [true, '$submissions.isValid']}, {$in: [ ?0 , '$submissions.userId']}]}}}",
    // Project only the required values
    "{$project:{_id: 0, name:1, displayName:1, isSolved:1, tags:{ $map: { 'input': '$tags', 'as': 'tag', in: { 'name': '$$tag.name', 'value': '$$tag.value'}}}}}"
  })
  public Flux<ModuleListItem> findAllOpenWithSolutionStatus(String userId);

  public Mono<ModuleEntity> findByName(String moduleId);

  public Mono<ModuleEntity> findByLocator(String moduleLocator);
}
