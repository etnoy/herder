import { Component, OnInit, Input } from '@angular/core';
import { ApiService } from '../../service/api.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Module } from 'src/app/model/module';
import { AlertService } from 'src/app/service/alert.service';
import { FlagTutorialResult } from 'src/app/model/flag-tutorial-result';

@Component({
  selector: 'app-flag-tutorial',
  templateUrl: './flag-tutorial.component.html',
  styleUrls: ['./flag-tutorial.component.css'],
})
export class FlagTutorialComponent {
  claimForm: FormGroup;
  result: FlagTutorialResult;

  submitted = false;
  loading = true;

  @Input() module: Module;

  constructor(
    private apiService: ApiService,
    public fb: FormBuilder,
    private alertService: AlertService
  ) {
    this.claimForm = this.fb.group({});
    this.result = null;
    this.loading = false;
  }

  public getFlag() {
    this.loading = true;
    this.apiService.moduleGetRequest(this.module.name, '').subscribe(
      (data) => {
        this.alertService.clear();
        this.loading = false;
        this.submitted = true;
        this.result = data;
      },
      (error) => {
        this.loading = false;
        this.submitted = false;
        this.result = null;

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
