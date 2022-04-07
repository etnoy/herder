import { Component, OnInit } from '@angular/core';
import { ApiService } from 'src/app/service/api.service';
import { Principal } from 'src/app/model/principal';
import { User } from 'src/app/model/user';

@Component({
  selector: 'app-userlist',
  templateUrl: './user-list.component.html',
})
export class UserListComponent implements OnInit {
  principals: Principal[];
  currentUser: User;
  impersonatingUser: User;

  constructor(private apiService: ApiService) {
    this.principals = [];
  }

  ngOnInit(): void {
    this.apiService.getSolvers().subscribe((principals: Principal[]) => {
      this.principals = principals;
    });
    this.apiService.currentUser.subscribe((user) => (this.currentUser = user));
    this.apiService.impersonatingUser.subscribe(
      (user) => (this.impersonatingUser = user)
    );
  }

  impersonate(userId: string) {
    this.apiService.impersonate(userId).subscribe();
  }

  clearImpersonation() {
    this.apiService.clearImpersonation();
  }
}
