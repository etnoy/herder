import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { UserScoreComponent } from './user-score.component';
import { RouterModule } from '@angular/router';
import { HttpClientTestingModule } from '@angular/common/http/testing';

describe('UserScoreComponent', () => {
  let component: UserScoreComponent;
  let fixture: ComponentFixture<UserScoreComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
      declarations: [UserScoreComponent],
      imports: [
        RouterModule.forRoot([], { relativeLinkResolution: 'legacy' }),
        HttpClientTestingModule,
      ],
    }).compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(UserScoreComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
