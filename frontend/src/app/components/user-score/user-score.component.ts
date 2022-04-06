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
