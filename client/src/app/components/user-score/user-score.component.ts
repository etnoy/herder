import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from 'src/app/service/api.service';
import { UserScore } from 'src/app/model/user-score';

@Component({
  selector: 'app-user-score',
  templateUrl: './user-score.component.html',
})
export class UserScoreComponent implements OnInit {
  submissions: UserScore[];
  userId: string;

  constructor(private route: ActivatedRoute, public apiService: ApiService) {
    this.submissions = [];
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const userId: string = params.get('userId');

      this.apiService
        .getScoresByUserId(userId)
        .subscribe((submissions: UserScore[]) => {
          this.userId = userId;
          this.submissions = submissions;
        });
    });
  }
}
