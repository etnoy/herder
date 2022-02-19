import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ApiService } from 'src/app/service/api.service';
import { UserScore } from 'src/app/model/user-score';
import { Module } from 'src/app/model/module';

@Component({
  selector: 'app-module-solves',
  templateUrl: './module-solves.component.html',
})
export class ModuleSolvesComponent implements OnInit {
  submissions: UserScore[];
  module: Module;

  constructor(private route: ActivatedRoute, public apiService: ApiService) {
    this.submissions = [];
  }

  ngOnInit(): void {
    this.route.paramMap.subscribe((params) => {
      const locator: string = params.get('moduleLocator');
      this.apiService
        .getModuleByLocator(locator)
        .subscribe((module: Module) => {
          this.module = module;
        });

      this.apiService
        .getSolvesByLocator(locator)
        .subscribe((submissions: UserScore[]) => {
          this.submissions = submissions;
        });
    });
  }
}
