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

import { ModuleItemComponent } from './components/module-item/module-item.component';
import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { LoginComponent } from './components/login/login.component';
import { SignupComponent } from './components/signup/signup.component';
import { ModuleListComponent } from './components/module-list/module-list.component';

import { AuthGuard } from './shared/auth.guard';
import { HomeComponent } from './components/home/home.component';
import { ScoreboardComponent } from './components/scoreboard/scoreboard.component';
import { UserScoreComponent } from './components/user-score/user-score.component';
import { UserListComponent } from './components/user-list/user-list.component';
import { ModuleSolvesComponent } from './components/module-solves/module-solves.component';

const routes: Routes = [
  { path: '', component: HomeComponent, canActivate: [AuthGuard] },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: SignupComponent },
  {
    path: 'users',
    component: UserListComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'scoreboard',
    component: ScoreboardComponent,
    canActivate: [AuthGuard],
  },
  { path: 'modules', component: ModuleListComponent, canActivate: [AuthGuard] },
  {
    path: 'module',
    children: [
      {
        path: '**',
        component: ModuleItemComponent,
        canActivate: [AuthGuard],
      },
    ],
  },
  {
    path: 'scoreboard/user/:userId',
    component: UserScoreComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'scoreboard/team/:teamId',
    component: UserScoreComponent,
    canActivate: [AuthGuard],
  },
  {
    path: 'scoreboard/module/:moduleLocator',
    component: ModuleSolvesComponent,
    canActivate: [AuthGuard],
  },
  { path: '**', redirectTo: '' },
];

@NgModule({
  imports: [RouterModule.forRoot(routes, { relativeLinkResolution: 'legacy' })],
  exports: [RouterModule],
})
export class AppRoutingModule {}
