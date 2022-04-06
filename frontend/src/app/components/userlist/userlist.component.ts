import { Component, OnInit } from '@angular/core';
import { ApiService } from 'src/app/service/api.service';
import { Solver } from 'src/app/model/solver';

@Component({
  selector: 'app-userlist',
  templateUrl: './userlist.component.html',
})
export class UserListComponent implements OnInit {
  solvers: Solver[];

  constructor(private apiService: ApiService) {
    this.solvers = [];
  }

  ngOnInit(): void {
    this.apiService.getSolvers().subscribe((solvers: Solver[]) => {
      this.solvers = solvers;
    });
  }
}
