/*
 * Copyright Jonathan Jogenfors, jonathan@jogenfors.se
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

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
