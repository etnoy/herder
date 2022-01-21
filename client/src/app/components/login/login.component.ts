import { Component, OnInit } from '@angular/core';
import { Router, ActivatedRoute } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { first } from 'rxjs/operators';
import { AlertService } from 'src/app/service/alert.service';
import { ApiService } from 'src/app/service/api.service';

@Component({ templateUrl: 'login.component.html' })
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  loading = false;
  submitted = false;
  returnUrl: string;

  constructor(
    private formBuilder: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private apiService: ApiService,
    private alertService: AlertService
  ) {}

  ngOnInit() {
    this.loginForm = this.formBuilder.group({
      userName: ['', Validators.required],
      password: ['', Validators.required],
    });

    // get return url from route parameters or default to '/'
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
      .subscribe({
        next: () => {
          this.router.navigate([this.returnUrl]);
        },
        error: (error) => {
          this.loading = false;
          this.submitted = false;
          let msg = '';
          if (error.error instanceof ErrorEvent) {
            // client-side error
            msg = error.error.errorMessage;
          } else {
            // HTTP error
            if (error.status === 401) {
              // HTTP Unauthorized
              msg = error.error.errorMessage;

              this.loginForm.controls['password'].reset();
            } else {
              // We don't want to tell the user about any other error.
              msg = `An error occurred`;
            }
          }
          this.alertService.error(msg);
        },
      });
  }
}
