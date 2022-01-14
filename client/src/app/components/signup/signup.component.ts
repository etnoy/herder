import { Component } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Router } from '@angular/router';
import { ApiService } from 'src/app/service/api.service';

@Component({
  selector: 'app-signup',
  templateUrl: './signup.component.html',
})
export class SignupComponent {
  signupForm: FormGroup;

  constructor(
    public fb: FormBuilder,
    public apiService: ApiService,
    public router: Router
  ) {
    this.signupForm = this.fb.group({
      displayName: [''],
      userName: [''],
      password: [''],
    });
  }

  registerUser() {
    this.apiService.signUp(this.signupForm.value).subscribe((res) => {
      if (res.result) {
        this.signupForm.reset();
        this.router.navigate(['login']);
      }
    });
  }
}
