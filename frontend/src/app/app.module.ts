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

import { ModuleDirective } from './module.directive';
import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppComponent } from './app.component';
import { LoginComponent } from './components/login/login.component';
import { SignupComponent } from './components/signup/signup.component';
import { ModuleListComponent } from './components/module-list/module-list.component';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { AuthInterceptor } from './shared/authconfig.interceptor';
import { AppRoutingModule } from './app-routing.module';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { SearchbarModule } from './components/searchbar/searchbar.module';
import { SqlInjectionTutorialComponent } from './components/sql-injection-tutorial/sql-injection-tutorial.component';
import { ModuleItemComponent } from './components/module-item/module-item.component';
import { XssTutorialComponent } from './components/xss-tutorial/xss-tutorial.component';
import { CsrfTutorialComponent } from './components/csrf-tutorial/csrf-tutorial.component';
import { RankDisplayComponent } from './components/rank-display/rank-display.component';
import { HomeComponent } from './components/home/home.component';
import { AlertComponent } from './components/alert/alert.component';
import { ErrorInterceptor } from './shared/error.interceptor';
import { ScoreboardComponent } from './components/scoreboard/scoreboard.component';
import { UserListComponent } from './components/user-list/user-list.component';
import { UserScoreComponent } from './components/user-score/user-score.component';
import { FlagTutorialComponent } from './components/flag-tutorial/flag-tutorial.component';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { MomentModule } from 'ngx-moment';
import { ModuleSolvesComponent } from './components/module-solves/module-solves.component';

@NgModule({
  declarations: [
    AppComponent,
    LoginComponent,
    SignupComponent,
    HomeComponent,
    ModuleListComponent,
    AlertComponent,
    SqlInjectionTutorialComponent,
    ModuleItemComponent,
    XssTutorialComponent,
    CsrfTutorialComponent,
    RankDisplayComponent,
    FlagTutorialComponent,
    ModuleDirective,
    ScoreboardComponent,
    UserListComponent,
    ModuleSolvesComponent,
    UserScoreComponent,
  ],
  imports: [
    BrowserModule,
    HttpClientModule,
    AppRoutingModule,
    ReactiveFormsModule,
    FormsModule,
    SearchbarModule,
    NgbModule,
    MomentModule,
  ],
  providers: [
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true,
    },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true },
  ],
  bootstrap: [AppComponent],
})
export class AppModule {}
