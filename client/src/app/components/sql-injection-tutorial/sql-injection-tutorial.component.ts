import { Component, Input } from '@angular/core';
import { ApiService } from '../../service/api.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Module } from 'src/app/model/module';
import { AlertService } from 'src/app/service/alert.service';
import { SqlInjectionTutorialResult } from 'src/app/model/sql-injection-tutorial-result';

@Component({
  selector: 'app-sql-injection-tutorial',
  templateUrl: './sql-injection-tutorial.component.html',
  styleUrls: ['./sql-injection-tutorial.component.css'],
})
export class SqlInjectionTutorialComponent {
  queryForm: FormGroup;
  result: SqlInjectionTutorialResult[];
  errorResult: string;
  submitted = false;
  loading = false;

  @Input() module: Module;

  constructor(
    private apiService: ApiService,
    public fb: FormBuilder,
    private alertService: AlertService
  ) {
    this.queryForm = this.fb.group({
      query: [''],
    });
    this.result = [];
    this.errorResult = '';
  }

  submitQuery() {
    if (this.queryForm.controls.query.value === '') {
      this.result = null;
      return;
    }
    return this.apiService
      .modulePostRequest(
        this.module.name,
        'search',
        this.queryForm.controls.query.value
      )
      .subscribe(
        (data) => {
          this.alertService.clear();
          this.submitted = true;
          this.result = data;
        },
        (error) => {
          this.submitted = false;
          this.result = [];
          this.errorResult = '';
          let msg = '';
          if (error.error instanceof ErrorEvent) {
            // client-side error
            msg = error.error.message;
          } else {
            msg = `An error occurred`;
          }
          this.alertService.error(msg);
        }
      );
  }
}
