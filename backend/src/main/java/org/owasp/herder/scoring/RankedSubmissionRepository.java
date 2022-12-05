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
package org.owasp.herder.scoring;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface RankedSubmissionRepository
  extends ReactiveMongoRepository<RankedSubmission, String> {
  public Flux<RankedSubmission> findAllByTeamId(String teamId);

  public Flux<RankedSubmission> findAllByUserId(String userId);

  @Query("{'module.locator' : ?0}")
  public Flux<RankedSubmission> findAllByModuleLocator(String moduleLocator);

  @Aggregation(
    {
      "{$addFields:{displayName:{$ifNull:['$team.displayName','$user.displayName']},solver:{$ifNull:['$team._id',{$concat:['user',{$toString:'$user._id'}]}]}}}",
      "{$group:{_id:'$solver',team:{$first:'$team'},displayName:{$first:'$displayName'},user:{$first:'$user'},score:{$sum:'$score'},bonusScore:{$sum:'$bonusScore'},baseScore:{$sum:'$baseScore'},goldMedals:{$sum:{$cond:[{$eq:['$rank',1]},1,0]}},silverMedals:{$sum:{$cond:[{$eq:['$rank',2]},1,0]}},bronzeMedals:{$sum:{$cond:[{$eq:['$rank',3]},1,0]}}}}",
      "{$lookup:{from:'scoreAdjustment','let':{userId:{$toString:'$user._id'}},pipeline:[{$match:{$expr:{$in:['$$userId','$userIds']}}}],as:'adjustments'}}",
      "{$unwind:{path:'$adjustments',preserveNullAndEmptyArrays:true}}",
      "{$addFields:{adjustment:'$adjustments.amount'}}",
      "{$group:{_id:'$_id',scoreAdjustment:{$sum:'$adjustment'},team:{$first:'$team'},displayName:{$first:'$displayName'},user:{$first:'$user'},score:{$first:'$score'},bonusScore:{$first:'$bonusScore'},baseScore:{$first:'$baseScore'},goldMedals:{$first:'$goldMedals'},silverMedals:{$first:'$silverMedals'},bronzeMedals:{$first:'$bronzeMedals'}}}",
      "{$addFields:{score:{$sum:['$scoreAdjustment','$score']}}}",
      "{$sort:{score:-1,goldMedals:-1,silverMedals:-1,bronzeMedals:-1,scoreAdjustment:-1,displayName:1}}",
    }
  )
  public Flux<UnrankedScoreboardEntry> getUnrankedScoreboard();
}
