import { Component } from '@angular/core';
import { ApiService } from 'src/app/service/api.service';
import { User } from 'src/app/model/user';

@Component({ templateUrl: 'home.component.html' })
export class HomeComponent {
  currentUser: User;
  impersonatedUser: User;

  constructor(private apiService: ApiService) {
    this.currentUser = this.apiService.currentUserValue;
    this.impersonatedUser = this.apiService.impersonatedUserValue;
  }
  clearImpersonation() {
    this.apiService.clearImpersonation();
  }
}
