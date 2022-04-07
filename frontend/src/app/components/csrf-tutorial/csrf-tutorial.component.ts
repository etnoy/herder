import { Component, OnInit, Input } from '@angular/core';
import { ApiService } from '../../service/api.service';
import { FormBuilder, FormGroup } from '@angular/forms';
import { ModuleListItem } from 'src/app/model/module-list-item';
import { AlertService } from 'src/app/service/alert.service';
import { CsrfTutorialResult } from 'src/app/model/csrf-tutorial-result';

@Component({
  selector: 'app-csrf-injection-tutorial',
  templateUrl: './csrf-tutorial.component.html',
})
export class CsrfTutorialComponent implements OnInit {
  queryForm: FormGroup;
  tutorialResult: CsrfTutorialResult;

  errorResult: string;
  submitted = false;
  loading = true;

  @Input() module: ModuleListItem;

  constructor(
    private apiService: ApiService,
    public fb: FormBuilder,
    private alertService: AlertService
  ) {
    this.queryForm = this.fb.group({
      query: [''],
    });
    this.tutorialResult = null;
    this.errorResult = '';
  }

  ngOnInit(): void {
    this.loading = true;
    if (
      !Array.isArray(this.module.parameters) ||
      !this.module.parameters.length
    ) {
      this.loadTutorial();
    } else if (
      this.module.parameters.length === 2 &&
      this.module.parameters[0].path === 'activate'
    ) {
      this.activate(this.module.parameters[1].path);
    }
    this.loading = false;
  }

  public activate(pseudonym: string): void {
    this.apiService
      .moduleGetRequest(this.module.locator, 'activate/' + pseudonym)
      .subscribe({
        next: (data) => {
          this.alertService.clear();
          this.loading = false;
          this.submitted = true;
          this.tutorialResult = data;
        },
        error: (error) => {
          this.loading = false;
          this.submitted = false;
          this.tutorialResult = null;

          this.errorResult = '';
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

  public loadTutorial(): void {
    this.loading = true;
    this.apiService.moduleGetRequest(this.module.locator, '').subscribe({
      next: (data) => {
        this.alertService.clear();
        this.loading = false;
        this.tutorialResult = data;
      },
      error: (error) => {
        this.loading = false;
        this.tutorialResult = null;
        this.errorResult = '';
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