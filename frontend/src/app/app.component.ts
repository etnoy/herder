import { Component } from '@angular/core';
import { ApiService } from './service/api.service';
import { Router } from '@angular/router';
import { User } from './model/user';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
})
export class AppComponent {
  currentUser: User;
  impersonatingUser: User;

  constructor(private router: Router, private apiService: ApiService) {
    this.apiService.currentUser.subscribe((user) => (this.currentUser = user));
    this.apiService.impersonatingUser.subscribe(
      (x) => (this.impersonatingUser = x)
    );
  }
  logout() {
    this.apiService.logout();
    this.router.navigate(['/login']);
  }

  clearImpersonation() {
    this.apiService.clearImpersonation();
  }
}
