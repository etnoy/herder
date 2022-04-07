import { Observable, throwError, BehaviorSubject, EMPTY } from 'rxjs';
import { Injectable } from '@angular/core';
import {
  HttpClient,
  HttpHeaders,
  HttpErrorResponse,
} from '@angular/common/http';
import { Router } from '@angular/router';
import { map, catchError } from 'rxjs/operators';
import { User } from '../model/user';
import { ModuleList } from '../model/module-list';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  endpoint = 'http://localhost:8080/api/v1';
  headers = new HttpHeaders().set('Content-Type', 'application/json');
  private currentUserSubject: BehaviorSubject<User>;
  private impersonatingUserSubject: BehaviorSubject<User>;
  public currentUser: Observable<User>;
  public impersonatingUser: Observable<User>;

  constructor(private http: HttpClient, public router: Router) {
    this.currentUserSubject = new BehaviorSubject<User>(
      JSON.parse(localStorage.getItem('currentUser'))
    );
    this.currentUser = this.currentUserSubject.asObservable();
    this.impersonatingUserSubject = new BehaviorSubject<User>(
      JSON.parse(localStorage.getItem('impersonatingUser'))
    );
    this.impersonatingUser = this.impersonatingUserSubject.asObservable();
  }

  submitFlag(moduleLocator: string, flag: string): Observable<any> {
    const api = `${this.endpoint}/flag/submit/${moduleLocator}`;
    return this.http.post<any>(api, flag).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  modulePostRequest(
    locator: string,
    resource: string,
    request: string
  ): Observable<any> {
    const api = `${this.endpoint}/module/${locator}/${resource}`;
    return this.http.post<any>(api, request).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  moduleGetRequest(locator: string, resource: string): Observable<any> {
    const api = `${this.endpoint}/module/${locator}/${resource}`;
    return this.http.get<any>(api).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  public get impersonatingUserValue(): User {
    return this.impersonatingUserSubject.value;
  }

  public get currentUserValue(): User {
    return this.currentUserSubject.value;
  }

  // Sign-up
  signUp(user: User): Observable<any> {
    const api = `${this.endpoint}/register`;
    return this.http.post(api, user).pipe(catchError(this.handleError));
  }

  // Sign-in
  login(creds: User) {
    return this.http.post<any>(`${this.endpoint}/login`, creds).pipe(
      map((loginResponse) => {
        // store user details and jwt token in local storage to keep user logged in between page refreshes
        localStorage.setItem('currentUser', JSON.stringify(loginResponse));
        this.currentUserSubject.next(loginResponse);
        this.impersonatingUserSubject.next(null);
        return loginResponse;
      })
    );
  }

  // Impersonate
  impersonate(userId: String) {
    if (this.impersonatingUserValue) {
      // Already impersonating, must clear it first
      this.clearImpersonation();
    }
    if (userId === this.currentUserValue.id) {
      return EMPTY;
    }

    const api = `${this.endpoint}/impersonate`;
    return this.http.post<any>(api, { impersonatedId: userId }).pipe(
      map((response) => {
        this.impersonatingUserSubject.next(
          JSON.parse(localStorage.getItem('currentUser'))
        );
        localStorage.setItem(
          'impersonatingUser',
          JSON.stringify(this.currentUserValue)
        );
        localStorage.setItem('currentUser', JSON.stringify(response));
        this.currentUserSubject.next(response);
        return response;
      }),
      catchError(this.handleError)
    );
  }

  clearImpersonation() {
    localStorage.setItem(
      'currentUser',
      JSON.stringify(this.impersonatingUserValue)
    );
    this.currentUserSubject.next(this.impersonatingUserValue);
    this.impersonatingUserSubject.next(null);
  }

  getToken() {
    return localStorage.getItem('access_token');
  }

  get isLoggedIn(): boolean {
    const authToken = localStorage.getItem('access_token');
    return authToken !== null ? true : false;
  }

  logout() {
    // remove user from local storage and set current user to null
    this.clearImpersonation();
    localStorage.removeItem('currentUser');
    this.currentUserSubject.next(null);
  }

  getScoreboard(): Observable<any> {
    const api = `${this.endpoint}/scoreboard/`;
    return this.http.get(api, { headers: this.headers }).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  getSolvers(): Observable<any> {
    const api = `${this.endpoint}/solvers/`;
    return this.http
      .get(api, { headers: this.headers })
      .pipe(catchError(this.handleError));
  }

  getModuleByLocator(locator: string): Observable<any> {
    const api = `${this.endpoint}/module/${locator}`;
    return this.http.get(api).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  getSolvesByLocator(locator: string): Observable<any> {
    const api = `${this.endpoint}/scoreboard/module/${locator}`;
    return this.http.get(api).pipe(catchError(this.handleError));
  }

  getScoresByUserId(userId: string): Observable<any> {
    const api = `${this.endpoint}/scoreboard/user/${userId}`;
    return this.http.get(api).pipe(catchError(this.handleError));
  }

  getScoresByTeamId(teamId: string): Observable<any> {
    const api = `${this.endpoint}/scoreboard/team/${teamId}`;
    return this.http.get(api).pipe(catchError(this.handleError));
  }

  getModuleList(): Observable<ModuleList> {
    const api = `${this.endpoint}/modules/`;
    return this.http.get<ModuleList>(api).pipe(catchError(this.handleError));
  }

  // Error
  handleError(error: HttpErrorResponse) {
    return throwError(() => error);
  }
}
