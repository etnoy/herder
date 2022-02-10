import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from 'src/app/service/api.service';
import { UserScore } from 'src/app/model/user-score';

@Component({
  selector: 'app-module-solves',
  templateUrl: './module-solves.component.html',
})
export class ModuleSolvesComponent implements OnInit {
  submissions: UserScore[];
  moduleName: string;

  constructor(private route: ActivatedRoute, public apiService: ApiService) {
    this.submissions = [];
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const moduleName: string = params.get('moduleName');

      this.apiService
        .getSolvesByModuleName(moduleName)
        .subscribe((submissions: UserScore[]) => {
          this.moduleName = moduleName;
          this.submissions = submissions;
        });
    });
  }
}
