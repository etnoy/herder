import { Component, Input } from '@angular/core';
import { ApiService } from '../../service/api.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ModuleListItem } from 'src/app/model/module-list-item';
import { AlertService } from 'src/app/service/alert.service';
import { FlagTutorialResult } from 'src/app/model/flag-tutorial-result';

@Component({
  selector: 'app-flag-tutorial',
  templateUrl: './flag-tutorial.component.html',
})
export class FlagTutorialComponent {
  claimForm: FormGroup;
  result: FlagTutorialResult;

  submitted = false;
  loading = true;

  @Input() module: ModuleListItem;

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
    this.apiService.moduleGetRequest(this.module.locator, '').subscribe({
      next: (data) => {
        this.alertService.clear();
        this.loading = false;
        this.submitted = true;
        this.result = data;
      },
      error: (error) => {
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
      },
    });
  }
}
