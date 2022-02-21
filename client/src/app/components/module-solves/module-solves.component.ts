import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from 'src/app/service/api.service';
import { UserScore } from 'src/app/model/user-score';
import { catchError, throwError } from 'rxjs';

@Component({
  selector: 'app-module-solves',
  templateUrl: './module-solves.component.html',
})
export class ModuleSolvesComponent implements OnInit {
  submissions: UserScore[];
  locator: string;
  moduleFound: boolean;

  constructor(private route: ActivatedRoute, public apiService: ApiService) {
    this.submissions = [];
    this.moduleFound = false;
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      this.locator = params.get('moduleLocator');
      this.apiService
        .getSolvesByLocator(this.locator)
        .pipe(catchError(this.handleError))
        .subscribe((submissions: UserScore[]) => {
          this.submissions = submissions;
          this.moduleFound = true;
        });
    });
  }

  handleError(error: string) {
    if (error.includes('Module not found')) {
      this.moduleFound = false;
      console.log('Error');
    } else {
      return throwError(() => error);
    }
  }
}
