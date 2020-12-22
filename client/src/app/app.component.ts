import { Component } from '@angular/core';
import { ApiService } from './service/api.service';
import { Router } from '@angular/router';
import { User } from './model/user';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  currentUser: User;

  constructor(private router: Router, private apiService: ApiService) {
    this.apiService.currentUser.subscribe(x => (this.currentUser = x));
  }
  logout() {
    this.apiService.logout();
    this.router.navigate(['/login']);
  }
}
