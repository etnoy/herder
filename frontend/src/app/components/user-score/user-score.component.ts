import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from 'src/app/service/api.service';
import { UserScore } from 'src/app/model/user-score';
import { catchError, of, throwError } from 'rxjs';

@Component({
  selector: 'app-user-score',
  templateUrl: './user-score.component.html',
})
export class UserScoreComponent implements OnInit {
  submissions: UserScore[];
  userId: string;
  userFound: boolean;

  constructor(private route: ActivatedRoute, public apiService: ApiService) {
    this.submissions = [];
    this.userFound = false;
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.userId = params.get('userId');
      this.apiService
        .getScoresByUserId(this.userId)
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
