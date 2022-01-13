import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { ApiService } from '../../service/api.service';
import { Router, ActivatedRoute } from '@angular/router';
import { AlertService } from 'src/app/service/alert.service';
import { first } from 'rxjs/operators';

@Component({
  selector: 'app-signin',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css'],
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  returnUrl: string;
  submitted = false;
  loading = false;

  constructor(
    private formBuilder: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private apiService: ApiService,
    private alertService: AlertService
  ) {
    if (this.apiService.currentUserValue) {
      this.router.navigate(['/']);
    }
  }

  ngOnInit() {
    this.loginForm = this.formBuilder.group({
      userName: ['', Validators.required],
      password: ['', Validators.required],
    });
    this.returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/';
  }

  onSubmit() {
    this.submitted = true;

    // reset alerts on submit
    this.alertService.clear();

    // stop here if form is invalid
    if (this.loginForm.invalid) {
      return;
    }

    this.loading = true;
    this.apiService
      .login(this.loginForm.value)
      .pipe(first())
      .subscribe(
        () => {
          this.router.navigate([this.returnUrl]);
        },
        (error) => {
          this.loading = false;
          let msg = '';
          if (error.error instanceof ErrorEvent) {
            // client-side error
            msg = error.error.message;
          } else {
            // HTTP error
            if (error.status === 401) {
              // HTTP Unauthorized
              msg = 'Incorrect username or password';
            } else {
              // We don't want to tell the user about any other error.
              msg = `An error occurred`;
            }
          }
          this.alertService.error(msg);
        }
      );
  }
}
