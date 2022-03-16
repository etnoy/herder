import { Component, OnInit } from '@angular/core';
import { ApiService } from 'src/app/service/api.service';
import { User } from 'src/app/model/user';

@Component({
  selector: 'app-userlist',
  templateUrl: './userlist.component.html',
})
export class UserListComponent implements OnInit {
  users: User[];

  constructor(private apiService: ApiService) {
    this.users = [];
  }

  ngOnInit(): void {
    this.apiService.getUsers().subscribe((users: User[]) => {
      this.users = users;
    });
  }
}
