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

import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from 'src/app/service/api.service';
import { UserScore } from 'src/app/model/user-score';
import { catchError, Observable, of, throwError } from 'rxjs';

@Component({
  selector: 'app-user-score',
  templateUrl: './user-score.component.html',
})
export class UserScoreComponent implements OnInit {
  submissions: UserScore[];
  solverId: string;
  solver: string;
  userFound: boolean;

  constructor(private route: ActivatedRoute, public apiService: ApiService) {
    this.submissions = [];
    this.userFound = false;
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const userId = params.get('userId');
      const teamId = params.get('teamId');

      let response: Observable<any>;

      if (userId != null) {
        response = this.apiService.getScoresByUserId(userId);
      } else if (teamId != null) {
        response = this.apiService.getScoresByTeamId(teamId);
      } else {
        return;
      }

      if (userId != null || teamId != null)
        response
          .pipe(
            catchError((error) => {
              if (error.status === 404) {
                this.userFound = false;
                return of();
              } else {
                return throwError(() => error);
              }
            })
          )
          .subscribe((submissions: UserScore[]) => {
            this.userFound = true;
            this.submissions = submissions;
          });
    });
  }
}
