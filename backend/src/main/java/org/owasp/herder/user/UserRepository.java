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
package org.owasp.herder.user;

import org.owasp.herder.module.ModuleList;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepository
  extends ReactiveMongoRepository<UserEntity, String> {
  public Mono<UserEntity> findByDisplayName(final String displayName);

  public Mono<UserEntity> findByIdAndIsDeletedFalse(final String id);

  public Flux<UserEntity> findAllByTeamId(final String teamId);

  @Aggregation(
    {
      "{$lookup:{from:'submission','let':{userid:'$_id',teamid:'$teamId'},pipeline:[{$match:{$expr:{$and:[{$or:[{$eq:['$userId','$$userid']},{$eq:['$teamId','$$teamid']}]},{$eq:['$isValid',true]}]}}},{$lookup:{from:'module',let:{moduleIdObj:{$toObjectId:'$moduleId'}},pipeline:[{$match:{$expr:{$eq:['$_id','$$moduleIdObj']}}}],as:'module'}},{$unwind:'$module'},{$addFields:{isSolved:true,name:'$module.name',locator:'$module.locator',tags:'$module.tags',moduleId:'$module._id','isOpen':'$module.isOpen'}},{$unionWith:{coll:'module',pipeline:[{$addFields:{isSolved:false,moduleId:'$_id'}}]}},{$match:{isOpen:true}},{$sort:{time:1}},{$group:{_id:'$moduleId',name:{$first:'$name'},locator:{$first:'$locator'},tags:{$first:'$tags'},isSolved:{$max:'$isSolved'}}}],as:'modules'}}",
      "{$project:{teamId:1,modules:1}}",
      "{$out:'moduleList'}",
    }
  )
  public Flux<ModuleList> computeModuleLists();
}
