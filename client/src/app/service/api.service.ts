import { Observable, throwError, BehaviorSubject } from 'rxjs';
import { Injectable } from '@angular/core';
import {
  HttpClient,
  HttpHeaders,
  HttpErrorResponse,
} from '@angular/common/http';
import { Router } from '@angular/router';
import { map, catchError } from 'rxjs/operators';
import { User } from '../model/user';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  endpoint = 'http://localhost:8080/api/v1';
  headers = new HttpHeaders().set('Content-Type', 'application/json');
  private currentUserSubject: BehaviorSubject<User>;
  public currentUser: Observable<User>;

  constructor(private http: HttpClient, public router: Router) {
    this.currentUserSubject = new BehaviorSubject<User>(
      JSON.parse(localStorage.getItem('currentUser'))
    );
    this.currentUser = this.currentUserSubject.asObservable();
  }

  submitFlag(moduleId: string, flag: string): Observable<any> {
    const api = `${this.endpoint}/flag/submit/${moduleId}`;
    return this.http.post<any>(api, flag).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  modulePostRequest(
    shortName: string,
    resource: string,
    request: string
  ): Observable<any> {
    const api = `${this.endpoint}/module/${shortName}/${resource}`;
    return this.http.post<any>(api, request).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  moduleGetRequest(shortName: string, resource: string): Observable<any> {
    const api = `${this.endpoint}/module/${shortName}/${resource}`;
    return this.http.get<any>(api).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
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
        return loginResponse;
      })
    );
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
    localStorage.removeItem('currentUser');
    this.currentUserSubject.next(null);
  }

  getScoreboard(): Observable<any> {
    const api = `${this.endpoint}/scoreboard/`;
    return this.http.get(api, { headers: this.headers }).pipe(
      map((res: Response) => {
        console.log(res);
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  getUsers(): Observable<any> {
    const api = `${this.endpoint}/users/`;
    return this.http.get(api, { headers: this.headers }).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  getModuleById(moduleId: string): Observable<any> {
    const api = `${this.endpoint}/module/${moduleId}`;
    return this.http.get(api, { headers: this.headers }).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  getScoresByUserId(userId: number): Observable<any> {
    const api = `${this.endpoint}/scoreboard/${userId}`;
    return this.http.get(api, { headers: this.headers }).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  getModules(): Observable<any> {
    const api = `${this.endpoint}/modules/`;
    return this.http.get(api, { headers: this.headers }).pipe(
      map((res: Response) => {
        return res || {};
      }),
      catchError(this.handleError)
    );
  }

  // Error
  handleError(error: HttpErrorResponse) {
    let msg = '';
    if (error.error instanceof ErrorEvent) {
      // client-side error
      msg = error.error.message;
    } else {
      // server-side error
      msg = `Error Code: ${error.status}\nMessage: ${error.message}`;
    }
    return throwError(() => msg);
  }
}
