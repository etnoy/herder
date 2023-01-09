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
package org.owasp.herder.scoring;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SubmissionRepository
  extends ReactiveMongoRepository<Submission, String> {
  @Aggregation({ "{$match:{'module._id':?0}}" })
  public Flux<Submission> findAllByModuleId(String moduleId);

  public Flux<Submission> findAllByTeamId(String teamId);

  public Flux<Submission> findAllByUserId(String userId);

  public Flux<Submission> findAllByUserIdAndIsValidTrue(String userId);

  public Mono<Submission> findAllByUserIdAndModuleIdAndIsValidTrue(
    String userId,
    String moduleId
  );

  public Mono<Boolean> existsByUserIdAndModuleIdAndIsValidTrue(
    @Param("userId") String userId,
    @Param("moduleId") String moduleId
  );

  @Aggregation(
    {
      "{$match:{isValid:true}}",
      "{$facet:{team:[{$match:{teamId:{$ne:null}}},{$group:{_id:{id:'$teamId',moduleId:'$moduleId'},userId:{$first:'$userId'},teamId:{$first:'$teamId'},moduleId:{$first:'$moduleId'},flag:{$first:'$flag'},time:{$min:'$time'}}}],user:[{$match:{teamId:{$eq:null}}}]}}",
      "{$project:{data:['$team','$user']}}",
      "{$unwind:{path:'$data'}}",
      "{$unwind:{path:'$data'}}",
      "{$replaceRoot:{newRoot:'$data'}}",
      "{$setWindowFields:{partitionBy:'$moduleId',sortBy:{time:1},output:{rank:{$rank:{}}}}}",
      "{$lookup:{from:'team','let':{teamObjectId:{$toObjectId:'$teamId'}},pipeline:[{$match:{$expr:{$eq:['$_id','$$teamObjectId']}}}],as:'team'}}",
      "{$lookup:{from:'user','let':{userObjectId:{$toObjectId:'$userId'}},pipeline:[{$match:{$expr:{$eq:['$_id','$$userObjectId']}}}],as:'user'}}",
      "{$lookup:{from:'module','let':{moduleObjectId:{$toObjectId:'$moduleId'}},pipeline:[{$match:{$expr:{$eq:['$_id','$$moduleObjectId']}}}],as:'module'}}",
      "{$unwind:{path:'$module'}}",
      "{$unwind:{path:'$team',preserveNullAndEmptyArrays:true}}",
      "{$unwind:{path:'$user'}}",
      "{$addFields:{baseScore:'$module.baseScore',bonusScore:{$ifNull:[{$arrayElemAt:['$module.bonusScores',{$sum:['$rank',-1]}]},0]}}}",
      "{$addFields:{score:{$sum:['$baseScore','$bonusScore']}}}",
      "{$project:{user:1,team:1,module:1,score:1,baseScore:1,bonusScore:1,time:1,flag:1,rank:1}}",
      "{$out:'submissionRank'}",
    }
  )
  public Flux<RankedSubmission> refreshSubmissionRanks();
}
