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
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface SubmissionRepository extends ReactiveMongoRepository<Submission, String> {
  public Flux<Submission> findAllByModuleName(String moduleName);

  @Query("{ 'isValid' : true, 'user_id': ?0 }")
  public Flux<Submission> findAllValidByUserId(String userId);

  public Mono<Submission> findAllByUserIdAndModuleNameAndIsValidTrue(
      String userId, String moduleName);

  public Mono<Boolean> existsByUserIdAndModuleNameAndIsValidTrue(
      @Param("userId") String userId, @Param("moduleName") String moduleName);

  @Aggregation({
    "{$match:{isValid:true}}",
    "{$setWindowFields:{partitionBy:'$moduleName',sortBy:{time:1},output:{rank:{$rank:{}}}}}",
    "{$lookup:{from:'modulePoint',localField:'moduleName',foreignField:'moduleName',as:'points'}}",
    "{$addFields:{baseScoreArray:{$filter:{input:'$points',as:'item',cond:{$eq:['$$item.rank',0]}}},bonusScoreArray:{$filter:{input:'$points',as:'item',cond:{$eq:['$$item.rank','$rank']}}}}}",
    "{$addFields:{baseScore:{$ifNull:[{$arrayElemAt:['$baseScoreArray.points',0]},0]},bonusScore:{$ifNull:[{$arrayElemAt:['$bonusScoreArray.points',0]},0]}}}",
    "{$project:{userId:1,rank:1,moduleName:1,time:1,flag:1,baseScore:1,bonusScore:1,score:{$add:['$baseScore','$bonusScore']}}}",
    "{$group:{_id:'$userId',score:{$sum:'$score'},goldMedals:{$sum:{$cond:[{$eq:['$rank',1]},1,0]}},silverMedals:{$sum:{$cond:[{$eq:['$rank',2]},1,0]}},bronzeMedals:{$sum:{$cond:[{$eq:['$rank',3]},1,0]}}}}",
    "{$unionWith:{coll:'user',pipeline:[{$set:{_id:{$toString:'$_id'},score:0,goldMedals:0,silverMedals:0,bronzeMedals:0}},{$project:{score:1,goldMedals:1,silverMedals:1,bronzeMedals:1,displayName:1}}]}}",
    "{$unionWith:{coll:'correction',pipeline:[{$set:{_id:'$userId',score:'$amount',goldMedals:0,silverMedals:0,bronzeMedals:0}},{$project:{score:1,goldMedals:1,silverMedals:1,bronzeMedals:1,displayName:1}}]}}",
    "{$group:{_id:'$_id',displayName:{$max:'$displayName'},score:{$sum:'$score'},goldMedals:{$sum:'$goldMedals'},silverMedals:{$sum:'$silverMedals'},bronzeMedals:{$sum:'$bronzeMedals'}}}",
    "{$setWindowFields:{sortBy:{score:-1},output:{rank:{$rank:{}}}}}",
    "{$project:{_id:0,userId:'$_id',rank:1,score:1,goldMedals:1,silverMedals:1,bronzeMedals:1,displayName:1}}",
    "{$sort: {rank: 1, goldMedals:-1, silverMedals:-1, bronzeMedals:-1, displayName: 1}}"
  })
  public Flux<ScoreboardEntry> getScoreboard();

  @Aggregation({
    "{$match:{isValid:true}}",
    "{$setWindowFields:{partitionBy:'$moduleName',sortBy:{time:1},output:{rank:{$rank:{}}}}}",
    "{$lookup:{from:'modulePoint',localField:'moduleName',foreignField:'moduleName',as:'points'}}",
    "{$addFields:{baseScoreArray:{$filter:{input:'$points',as:'item',cond:{$eq:['$$item.rank',0]}}},bonusScoreArray:{$filter:{input:'$points',as:'item',cond:{$eq:['$$item.rank','$rank']}}}}}",
    "{$addFields:{baseScore:{$ifNull:[{$arrayElemAt:['$baseScoreArray.points',0]},0]},bonusScore:{$ifNull:[{$arrayElemAt:['$bonusScoreArray.points',0]},0]}}}",
    "{$project:{_id:0,userId:{$toObjectId:'$userId'},rank:1,moduleName:1,time:1,flag:1,baseScore:1,bonusScore:1,score:{$add:['$baseScore','$bonusScore']}}}",
    "{$lookup:{from:'user',localField:'userId',foreignField:'_id',as:'user'}}",
    "{$addFields:{displayName:{$ifNull:[{$arrayElemAt:['$user.displayName',0]},0]}}}",
    "{$project:{userId:{$toString:'$userId'},rank:1,moduleName:1,displayName:1,time:1,flag:1,baseScore:1,bonusScore:1,score:1}}",
    "{$match:{'userId': ?0 }}",
    "{$sort: {time: -1}}"
  })
  public Flux<RankedSubmission> findAllRankedByUserId(String userId);

  @Aggregation({
    "{$match:{isValid:true}}",
    "{$match:{'moduleName': ?0 }}",
    "{$setWindowFields:{partitionBy:'$moduleName',sortBy:{time:1},output:{rank:{$rank:{}}}}}",
    "{$lookup:{from:'modulePoint',localField:'moduleName',foreignField:'moduleName',as:'points'}}",
    "{$addFields:{baseScoreArray:{$filter:{input:'$points',as:'item',cond:{$eq:['$$item.rank',0]}}},bonusScoreArray:{$filter:{input:'$points',as:'item',cond:{$eq:['$$item.rank','$rank']}}}}}",
    "{$addFields:{baseScore:{$ifNull:[{$arrayElemAt:['$baseScoreArray.points',0]},0]},bonusScore:{$ifNull:[{$arrayElemAt:['$bonusScoreArray.points',0]},0]}}}",
    "{$project:{_id:0,userId:{$toObjectId:'$userId'},rank:1,moduleName:1,time:1,flag:1,baseScore:1,bonusScore:1,score:{$add:['$baseScore','$bonusScore']}}}",
    "{$lookup:{from:'user',localField:'userId',foreignField:'_id',as:'user'}}",
    "{$addFields:{displayName:{$ifNull:[{$arrayElemAt:['$user.displayName',0]},0]}}}",
    "{$project:{userId:{$toString:'$userId'},rank:1,moduleName:1,displayName:1,time:1,flag:1,baseScore:1,bonusScore:1,score:1}}",
    "{$sort: {time: 1}}"
  })
  public Flux<RankedSubmission> findAllRankedByModuleName(String moduleName);
}
