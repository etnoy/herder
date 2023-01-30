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
import { SqlInjectionTutorialResult } from 'src/app/model/sql-injection-tutorial-result';

@Component({
  selector: 'app-sql-injection-tutorial',
  templateUrl: './sql-injection-tutorial.component.html',
})
export class SqlInjectionTutorialComponent {
  queryForm: FormGroup;
  result: SqlInjectionTutorialResult[];
  errorResult: string;
  submitted = false;
  loading = false;
  fullQuery: String;

  @Input() module: ModuleListItem;

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
    this.fullQuery = null;
  }

  submitQuery() {
    const query = this.queryForm.controls.query.value;
    this.fullQuery = `SELECT * FROM sqlinjection.users WHERE name = '${query}';`;

    if (query === '') {
      this.result = null;
      return;
    }
    return this.apiService
      .modulePostRequest(this.module.locator, 'search', query)
      .subscribe({
        next: (data) => {
          this.alertService.clear();
          this.submitted = true;
          this.result = data;
        },

        error: (error) => {
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
        },
      });
  }
}
