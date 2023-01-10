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

import { ModuleDirective } from '../../module.directive';
import { ApiService } from '../../service/api.service';
import {
  Component,
  OnInit,
  Input,
  ViewChild,
  ComponentFactoryResolver,
} from '@angular/core';
import { FormGroup, FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { ModuleListItem } from '../../model/module-list-item';
import { throwError } from 'rxjs';
import { AlertService } from 'src/app/service/alert.service';
import { Submission } from 'src/app/model/submission';
import { XssTutorialComponent } from '../xss-tutorial/xss-tutorial.component';
import { SqlInjectionTutorialComponent } from '../sql-injection-tutorial/sql-injection-tutorial.component';
import { CsrfTutorialComponent } from '../csrf-tutorial/csrf-tutorial.component';
import { FlagTutorialComponent } from '../flag-tutorial/flag-tutorial.component';

@Component({
  selector: 'app-module-item',
  templateUrl: './module-item.component.html',
})
export class ModuleItemComponent implements OnInit {
  flagForm: FormGroup;
  loading = false;
  submitted = false;
  userId: string;
  solved = false;

  @Input() modules: ModuleListItem[];

  @ViewChild(ModuleDirective) moduleDirective: ModuleDirective;

  module: ModuleListItem;

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private componentFactoryResolver: ComponentFactoryResolver,
    private apiService: ApiService,
    private alertService: AlertService
  ) {
    this.flagForm = this.fb.group({
      flag: ['', Validators.required],
    });
    this.flagForm.enable();
  }

  ngOnInit() {
    this.route.url.subscribe((segments: UrlSegment[]) => {
      if (!Array.isArray(segments) || !segments.length) {
        // no parameters given, return error
        return throwError(() => 'Invalid argument');
      }
      const locator = segments[0].path;
      this.apiService
        .getModuleByLocator(locator)
        .subscribe((module: ModuleListItem) => {
          this.module = module;
          if (segments.length > 1) {
            this.module.parameters = segments;
            this.module.parameters.shift();
          }
          this.solved = this.module.isSolved;
          if (this.solved) {
            this.flagForm.disable();
          }
          let currentModule;
          switch (this.module.locator) {
            case 'sql-injection-tutorial': {
              currentModule = SqlInjectionTutorialComponent;
              break;
            }
            case 'xss-tutorial': {
              currentModule = XssTutorialComponent;
              break;
            }
            case 'csrf-tutorial': {
              currentModule = CsrfTutorialComponent;
              break;
            }
            case 'flag-tutorial': {
              currentModule = FlagTutorialComponent;
              break;
            }
            default: {
              return throwError(() => 'module locator cannot be resolved');
            }
          }

          const viewContainerRef = this.moduleDirective.viewContainerRef;
          viewContainerRef.clear();

          // Load the selected module
          const componentRef = viewContainerRef.createComponent(currentModule);

          (componentRef.instance as typeof currentModule).module = this.module;
        });
    });
  }

  submitFlag() {
    this.submitted = true;

    // stop here if form is invalid
    if (this.flagForm.invalid) {
      return;
    }
    this.loading = true;

    return this.apiService
      .submitFlag(this.module.locator, this.flagForm.controls.flag.value)
      .subscribe({
        next: (submission: Submission) => {
          this.loading = false;
          if (submission.isValid) {
            this.alertService.success(`Well done, module complete`);
            this.solved = true;
            this.flagForm = this.fb.group({
              flag: [''],
            });
          } else {
            this.alertService.error(`Invalid flag`);
          }
        },
        error: () => {
          this.loading = false;
          this.alertService.error(`An error occurred`);
        },
      });
  }
}
